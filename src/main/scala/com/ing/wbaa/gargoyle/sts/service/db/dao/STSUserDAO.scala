package com.ing.wbaa.gargoyle.sts.service.db.dao

import java.sql.{ Connection, PreparedStatement, SQLException, SQLIntegrityConstraintViolationException }

import com.typesafe.scalalogging.LazyLogging
import com.ing.wbaa.gargoyle.sts.data.UserName
import com.ing.wbaa.gargoyle.sts.data.aws.{ AwsAccessKey, AwsCredential, AwsSecretKey }
import org.mariadb.jdbc.MariaDbPoolDataSource

import scala.concurrent.{ ExecutionContext, Future }

trait STSUserDAO extends LazyLogging {

  protected[this] implicit def executionContext: ExecutionContext

  protected[this] def mariaDbConnectionPool: MariaDbPoolDataSource

  protected[this] def withMariaDbConnection[T](f: Connection => Future[T]): Future[T]

  private[this] val MYSQL_DUPLICATE__KEY_ERROR_CODE = 1062
  private[this] val USER_TABLE = "users"

  /**
   * Retrieves AWS user credentials based on the username
   *
   * @param userName The username to search an entry against
   */
  def getAwsCredential(userName: UserName): Future[Option[AwsCredential]] =
    withMariaDbConnection[Option[AwsCredential]] {

      connection =>
        {
          val sqlQuery = s"SELECT * FROM $USER_TABLE WHERE username = ?"
          Future {

            val preparedStatement: PreparedStatement = connection.prepareStatement(sqlQuery)
            preparedStatement.setString(1, userName.value)
            val results = preparedStatement.executeQuery()
            if (results.first()) {
              val accessKey = AwsAccessKey(results.getString("accesskey"))
              val secretKey = AwsSecretKey(results.getString("secretkey"))
              Some(AwsCredential(accessKey, secretKey))

            } else None
          }
        }

    }

  /**
   * Retrieves the secret key, username and NPA status against the AWS access key.
   *
   * @param awsAccessKey
   * @return
   */
  def getUserSecretKeyAndIsNPA(awsAccessKey: AwsAccessKey): Future[Option[(UserName, AwsSecretKey, Boolean)]] =
    withMariaDbConnection[Option[(UserName, AwsSecretKey, Boolean)]] {

      connection =>
        {
          val sqlQuery = s"SELECT * FROM $USER_TABLE WHERE accesskey = ?"
          Future {

            val preparedStatement: PreparedStatement = connection.prepareStatement(sqlQuery)
            preparedStatement.setString(1, awsAccessKey.value)
            val results = preparedStatement.executeQuery()
            if (results.first()) {
              val username = UserName(results.getString("username"))
              val secretKey = AwsSecretKey(results.getString("secretkey"))
              val isNpa = results.getBoolean("isNPA")
              Some((username, secretKey, isNpa))

            } else None
          }
        }

    }

  /**
   * Inserts User object into the Users tables
   *
   * @param username
   * @param awsCredential
   * @param isNpa
   * @return A future with a boolean if the operation was succesful or not
   */
  def insertAwsCredentials(username: UserName, awsCredential: AwsCredential, isNpa: Boolean): Future[Boolean] =
    withMariaDbConnection[Boolean] {
      connection =>
        {
          val sqlQuery = s"INSERT INTO $USER_TABLE (username, accesskey, secretkey, isNPA) VALUES (?, ?, ?, ?)"

          Future {
            val preparedStatement: PreparedStatement = connection.prepareStatement(sqlQuery)
            preparedStatement.setString(1, username.value)
            preparedStatement.setString(2, awsCredential.accessKey.value)
            preparedStatement.setString(3, awsCredential.secretKey.value)
            preparedStatement.setBoolean(4, isNpa)

            preparedStatement.execute()
            true
          } recoverWith {
            //A SQL Exception could be thrown as a result of the column accesskey containing a duplicate value
            //return a successful future with a false result indicating it did not insert and needs to be retried with a new accesskey
            case sqlEx: SQLException if (sqlEx.isInstanceOf[SQLIntegrityConstraintViolationException]
              && sqlEx.getErrorCode.equals(MYSQL_DUPLICATE__KEY_ERROR_CODE)) => {
              logger.error(s"Duplicate key detected for username: $username")
              Future.successful(false)
            }
          }
        }

    }

}
