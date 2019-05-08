package com.ing.wbaa.rokku.sts.service.db.dao

import java.sql.{Connection, PreparedStatement, SQLException, SQLIntegrityConstraintViolationException}

import com.ing.wbaa.rokku.sts.data.aws.{AwsAccessKey, AwsCredential, AwsSecretKey}
import com.ing.wbaa.rokku.sts.data.{UserGroup, UserName}
import com.ing.wbaa.rokku.sts.service.db.security.Encryption
import com.typesafe.scalalogging.LazyLogging
import org.mariadb.jdbc.MariaDbPoolDataSource

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait STSUserAndGroupDAO extends LazyLogging with Encryption {

  implicit protected[this] def dbExecutionContext: ExecutionContext

  protected[this] def mariaDbConnectionPool: MariaDbPoolDataSource

  protected[this] def withMariaDbConnection[T](f: Connection => Future[T]): Future[T]

  private[this] val MYSQL_DUPLICATE__KEY_ERROR_CODE = 1062
  private[this] val USER_TABLE = "users"
  private[this] val USER_GROUP_TABLE = "user_groups"

  /**
   * Retrieves AWS user credentials based on the username
   *
   * @param userName The username to search an entry against
   */
  def getAwsCredential(userName: UserName): Future[Option[AwsCredential]] =
    withMariaDbConnection[Option[AwsCredential]] { connection =>
      {
        val sqlQuery = s"SELECT * FROM $USER_TABLE WHERE username = ?"
        Future {
          val preparedStatement: PreparedStatement = connection.prepareStatement(sqlQuery)
          preparedStatement.setString(1, userName.value)
          val results = preparedStatement.executeQuery()
          if (results.first()) {

            val accessKey = AwsAccessKey(results.getString("accesskey"))
            val secretKey = AwsSecretKey(decryptSecret(results.getString("secretkey"), userName.value))
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
  def getUserSecretKeyAndIsNPA(
    awsAccessKey: AwsAccessKey
  ): Future[Option[(UserName, AwsSecretKey, Boolean, Set[UserGroup])]] =
    withMariaDbConnection[Option[(UserName, AwsSecretKey, Boolean, Set[UserGroup])]] { connection =>
      {
        val separator = ","
        val sqlQuery = "select username, accesskey, secretkey, isNPA, " +
          s"(select GROUP_CONCAT(groupname SEPARATOR '$separator') from $USER_GROUP_TABLE g where g.username = u.username) as groups " +
          s"from $USER_TABLE as u where u.accesskey = ? group by username, accesskey, secretkey, isNPA"

        Future {
          val preparedStatement: PreparedStatement = connection.prepareStatement(sqlQuery)
          preparedStatement.setString(1, awsAccessKey.value)
          val results = preparedStatement.executeQuery()
          if (results.first()) {
            val username = UserName(results.getString("username"))
            val secretKey = AwsSecretKey(decryptSecret(results.getString("secretkey"), username.value))
            val isNpa = results.getBoolean("isNPA")
            val groupsAsString = results.getString("groups")
            val groups =
              if (groupsAsString != null)
                groupsAsString
                  .split(separator)
                  .map(_.trim)
                  .map(UserGroup)
                  .toSet
              else Set.empty[UserGroup]
            Some((username, secretKey, isNpa, groups))
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
   * @return A future with a boolean if the operation was successful or not
   */
  def insertAwsCredentials(username: UserName, awsCredential: AwsCredential, isNpa: Boolean): Future[Boolean] =
    withMariaDbConnection[Boolean] { connection =>
      {
        val sqlQuery = s"INSERT INTO $USER_TABLE (username, accesskey, secretkey, isNPA) VALUES (?, ?, ?, ?)"

        Future {
          val preparedStatement: PreparedStatement = connection.prepareStatement(sqlQuery)
          preparedStatement.setString(1, username.value)
          preparedStatement.setString(2, awsCredential.accessKey.value)
          preparedStatement.setString(3, encryptSecret(awsCredential.secretKey.value, username.value))
          preparedStatement.setBoolean(4, isNpa)

          preparedStatement.execute()
          true
        }.recoverWith {
          //A SQL Exception could be thrown as a result of the column accesskey containing a duplicate value
          //return a successful future with a false result indicating it did not insert and needs to be retried with a new accesskey
          case sqlEx: SQLException
              if (sqlEx.isInstanceOf[SQLIntegrityConstraintViolationException]
                && sqlEx.getErrorCode.equals(MYSQL_DUPLICATE__KEY_ERROR_CODE)) =>
            logger.error(sqlEx.getMessage, sqlEx)
            Future.successful(false)
        }
      }
    }

  /**
   * Removes all user groups and inserts the new on from userGroup
   * @param userName
   * @param userGroups
   * @return true if succeeded
   */
  def insertUserGroups(userName: UserName, userGroups: Set[UserGroup]): Future[Boolean] =
    withMariaDbConnection[Boolean] { connection =>
      {
        val deleteQuery = s"delete from $USER_GROUP_TABLE where username = ?"
        val insertQuery = s"insert into $USER_GROUP_TABLE (username, groupname) values (?, ?)"
        Future {
          Try {
            val preparedDeleteStatement: PreparedStatement = connection.prepareStatement(deleteQuery)
            preparedDeleteStatement.setString(1, userName.value)
            val preparedInsertStatement: PreparedStatement = connection.prepareStatement(insertQuery)
            userGroups.foreach { group =>
              preparedInsertStatement.setString(1, userName.value)
              preparedInsertStatement.setString(2, group.value)
              preparedInsertStatement.addBatch()
            }
            connection.setAutoCommit(false)
            preparedDeleteStatement.executeUpdate()
            preparedInsertStatement.executeBatch()
          } match {
            case Success(_) =>
              connection.commit()
              connection.setAutoCommit(true)
              true
            case Failure(ex) =>
              logger.error("Cannot insert user ({}) groups {}", userName, ex)
              connection.rollback()
              connection.setAutoCommit(true)
              throw ex
              false
          }
        }
      }
    }

  /**
   * Checks if a username does not exist, but there exists a similar access key
   *
   *
   * @param userName
   * @param awsAccessKey
   * @return
   */
  def doesUsernameNotExistAndAccessKeyExist(userName: UserName, awsAccessKey: AwsAccessKey): Future[Boolean] =
    Future.sequence(List(doesUsernameExist(userName), doesAccessKeyExist(awsAccessKey))).map {
      case List(false, true) => true
      case _                 => false
    }

  private[this] def doesUsernameExist(userName: UserName): Future[Boolean] =
    withMariaDbConnection { connection =>
      {
        val countUsersQuery = s"SELECT count(*) FROM $USER_TABLE WHERE username = ?"

        Future {
          val preparedStatement: PreparedStatement = connection.prepareStatement(countUsersQuery)
          preparedStatement.setString(1, userName.value)
          val results = preparedStatement.executeQuery()
          if (results.first()) {
            results.getInt(1) > 0
          } else false
        }
      }
    }

  private[this] def doesAccessKeyExist(awsAccessKey: AwsAccessKey): Future[Boolean] =
    withMariaDbConnection { connection =>
      {
        val countAccesskeysQuery = s"SELECT count(*) FROM $USER_TABLE WHERE accesskey = ?"

        Future {
          val preparedStatement: PreparedStatement = connection.prepareStatement(countAccesskeysQuery)
          preparedStatement.setString(1, awsAccessKey.value)
          val results = preparedStatement.executeQuery()
          if (results.first()) {
            results.getInt(1) > 0
          } else false
        }
      }
    }
}
