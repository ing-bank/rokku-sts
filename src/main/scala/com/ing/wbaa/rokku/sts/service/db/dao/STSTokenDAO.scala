package com.ing.wbaa.rokku.sts.service.db.dao

import java.sql._

import com.ing.wbaa.rokku.sts.data.UserName
import com.ing.wbaa.rokku.sts.data.aws.{AwsSessionToken, AwsSessionTokenExpiration}
import com.ing.wbaa.rokku.sts.service.db.security.Encryption
import com.typesafe.scalalogging.LazyLogging
import org.mariadb.jdbc.MariaDbPoolDataSource

import scala.concurrent.{ExecutionContext, Future}

trait STSTokenDAO extends LazyLogging with Encryption {

  implicit protected[this] def dbExecutionContext: ExecutionContext

  protected[this] def mariaDbConnectionPool: MariaDbPoolDataSource

  protected[this] def withMariaDbConnection[T](f: Connection => Future[T]): Future[T]

  private[this] val TOKENS_TABLE = "tokens"
  private[this] val MYSQL_DUPLICATE__KEY_ERROR_CODE = 1062
  val TOKENS_ARCH_TABLE = "tokens_arch"

  /**
   * Get Token from database against the token session identifier
   *
   * @param awsSessionToken
   * @param userName
   * @return
   */
  def getToken(
    awsSessionToken: AwsSessionToken,
    userName: UserName
  ): Future[Option[(UserName, AwsSessionTokenExpiration)]] =
    getToken(awsSessionToken, userName, TOKENS_TABLE)

  /**
   * overloaded getToken method to allow get token form different table
   *  eg from tokens_arch for integration tests
   *
   * @param awsSessionToken
   * @param userName
   * @param table - table the token is taken
   * @return
   */
  def getToken(
    awsSessionToken: AwsSessionToken,
    userName: UserName,
    table: String = TOKENS_TABLE
  ): Future[Option[(UserName, AwsSessionTokenExpiration)]] =
    withMariaDbConnection[Option[(UserName, AwsSessionTokenExpiration)]] { connection =>
      {
        val sqlQuery = s"SELECT * FROM $table WHERE sessiontoken = ?"
        Future {
          val preparedStatement: PreparedStatement = connection.prepareStatement(sqlQuery)
          preparedStatement.setString(1, encryptSecret(awsSessionToken.value, userName.value))
          val results = preparedStatement.executeQuery()
          if (results.first()) {
            val username = UserName(results.getString("username"))
            val expirationDate = AwsSessionTokenExpiration(results.getTimestamp("expirationtime").toInstant)
            logger.debug("getToken {} expire {} (table {})", awsSessionToken, expirationDate, table)
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
  def insertToken(
    awsSessionToken: AwsSessionToken,
    username: UserName,
    expirationDate: AwsSessionTokenExpiration
  ): Future[Boolean] =
    withMariaDbConnection[Boolean] { connection =>
      {
        val sqlQuery = s"INSERT INTO $TOKENS_TABLE (sessiontoken, username, expirationtime) VALUES (?, ?, ?)"

        Future {
          val preparedStatement: PreparedStatement = connection.prepareStatement(sqlQuery)
          preparedStatement.setString(1, encryptSecret(awsSessionToken.value, username.value))
          preparedStatement.setString(2, username.value)
          preparedStatement.setTimestamp(3, Timestamp.from(expirationDate.value))
          preparedStatement.execute()
          true
        }.recoverWith {
          //A SQL Exception could be thrown as a result of the column sessiontoken containing a duplicate value
          //return a successful future with a false result indicating it did not insert and needs to be retried with a new sessiontoken
          case sqlEx: SQLException
              if (sqlEx.isInstanceOf[SQLIntegrityConstraintViolationException]
                && sqlEx.getErrorCode.equals(MYSQL_DUPLICATE__KEY_ERROR_CODE)) =>
            logger.error(sqlEx.getMessage, sqlEx)
            Future.successful(false)
        }
      }
    }

  /**
   * Remove all expired tokens (after the expirationDate)
   *   - move to archive table
   *   - delete from original table
   * @param expirationDate after the date the tokens are removed
   * @return how many tokens have been removed
   */
  def cleanExpiredTokens(expirationDate: AwsSessionTokenExpiration): Future[Int] =
    withMariaDbConnection[Int] { connection =>
      val archiveTokensQuery =
        s"insert into $TOKENS_ARCH_TABLE select *, now() from $TOKENS_TABLE where expirationtime < ?"
      val deleteOldTokenQuery = s"delete from $TOKENS_TABLE where expirationtime < ?"
      Future {
        val preparedStmArch = connection.prepareStatement(archiveTokensQuery)
        preparedStmArch.setTimestamp(1, Timestamp.from(expirationDate.value))
        val archRecords = preparedStmArch.executeUpdate()
        preparedStmArch.close()

        val preparedStmDel = connection.prepareStatement(deleteOldTokenQuery)
        preparedStmDel.setTimestamp(1, Timestamp.from(expirationDate.value))
        val delRecords = preparedStmDel.executeUpdate()
        preparedStmDel.close()
        logger.info(s"archived {} tokens and deleted {}", archRecords, delRecords)
        delRecords
      }
    }
}
