package com.ing.wbaa.rokku.sts.service.db.dao

import java.sql._

import com.ing.wbaa.rokku.sts.data.UserName
import com.ing.wbaa.rokku.sts.data.aws.{ AwsSessionToken, AwsSessionTokenExpiration }
import com.ing.wbaa.rokku.sts.service.db.security.Encryption
import com.typesafe.scalalogging.LazyLogging
import org.mariadb.jdbc.MariaDbPoolDataSource

import scala.concurrent.{ ExecutionContext, Future }

trait STSTokenDAO extends LazyLogging with Encryption {

  protected[this] implicit def dbExecutionContext: ExecutionContext

  protected[this] def mariaDbConnectionPool: MariaDbPoolDataSource

  protected[this] def withMariaDbConnection[T](f: Connection => Future[T]): Future[T]

  private[this] val TOKENS_TABLE = "tokens"
  private[this] val MYSQL_DUPLICATE__KEY_ERROR_CODE = 1062

  /**
   * Get Token from database against the token session identifier
   *
   * @param awsSessionToken
   * @return
   */
  def getToken(awsSessionToken: AwsSessionToken, userName: UserName): Future[Option[(UserName, AwsSessionTokenExpiration)]] =
    withMariaDbConnection[Option[(UserName, AwsSessionTokenExpiration)]] {
      connection =>
        {
          val sqlQuery = s"SELECT * FROM $TOKENS_TABLE WHERE sessiontoken = ?"
          Future {
            val preparedStatement: PreparedStatement = connection.prepareStatement(sqlQuery)
            preparedStatement.setString(1, encryptSecret(awsSessionToken.value, userName.value))
            val results = preparedStatement.executeQuery()
            if (results.first()) {
              val username = UserName(results.getString("username"))
              val expirationDate = AwsSessionTokenExpiration(results.getTimestamp("expirationtime").toInstant)
              Some((username, expirationDate))
            } else None
          }
        }
    }

  /**
   * Insert a token item into the database
   *
   * @param awsSessionToken
   * @param username
   * @param expirationDate
   * @return
   */
  def insertToken(awsSessionToken: AwsSessionToken, username: UserName, expirationDate: AwsSessionTokenExpiration): Future[Boolean] =
    withMariaDbConnection[Boolean] {
      connection =>
        {
          val sqlQuery = s"INSERT INTO $TOKENS_TABLE (sessiontoken, username, expirationtime) VALUES (?, ?, ?)"

          Future {
            val preparedStatement: PreparedStatement = connection.prepareStatement(sqlQuery)
            preparedStatement.setString(1, encryptSecret(awsSessionToken.value, username.value))
            preparedStatement.setString(2, username.value)
            preparedStatement.setTimestamp(3, Timestamp.from(expirationDate.value))
            preparedStatement.execute()
            true
          } recoverWith {
            //A SQL Exception could be thrown as a result of the column sessiontoken containing a duplicate value
            //return a successful future with a false result indicating it did not insert and needs to be retried with a new sessiontoken
            case sqlEx: SQLException if (sqlEx.isInstanceOf[SQLIntegrityConstraintViolationException]
              && sqlEx.getErrorCode.equals(MYSQL_DUPLICATE__KEY_ERROR_CODE)) =>
              logger.error(sqlEx.getMessage, sqlEx)
              Future.successful(false)
          }
        }
    }
}

