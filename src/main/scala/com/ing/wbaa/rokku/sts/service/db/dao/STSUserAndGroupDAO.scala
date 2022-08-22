package com.ing.wbaa.rokku.sts.service.db.dao

import com.ing.wbaa.rokku.sts.data.aws.{ AwsAccessKey, AwsCredential, AwsSecretKey }
import com.ing.wbaa.rokku.sts.data.{ AccountStatus, NPA, NPAAccount, NPAAccountList, UserGroup, UserName }
import com.ing.wbaa.rokku.sts.service.db.security.Encryption
import com.typesafe.scalalogging.LazyLogging
import redis.clients.jedis.search.{ Query }
import scala.jdk.CollectionConverters._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }
import redis.clients.jedis.JedisPooled

trait STSUserAndGroupDAO extends LazyLogging with Encryption {

  protected[this] implicit def dbExecutionContext: ExecutionContext

  protected[this] def withRedisPool[T](f: JedisPooled => Future[T]): Future[T]

  // private[this] val MYSQL_DUPLICATE__KEY_ERROR_CODE = 1062
  private[this] val USERS_PREFIX = "users:"
  private[this] val GROUPNAME_SEPARATOR = ","
  // private[this] val USER_GROUP_TABLE = "user_groups"

  /**
   * Retrieves AWS user credentials based on the username
   *
   * @param userName The username to search an entry against
   */
  def getAwsCredentialAndStatus(username: UserName): Future[(Option[AwsCredential], AccountStatus)] =
    withRedisPool[(Option[AwsCredential], AccountStatus)] {
      client =>
        {
          Future {
            Try {
              val values = client
                .hgetAll(s"${USERS_PREFIX}${username.value}")

              if (values.size() > 0) {
                val accessKey = AwsAccessKey(values.get("accessKey"))
                val secretKey = AwsSecretKey(decryptSecret(values.get("secretKey").trim(), username.value.trim()))
                val isEnabled = values.get("isEnabled").toBooleanOption.getOrElse(false)

                (Some(AwsCredential(accessKey, secretKey)), AccountStatus(isEnabled))
              } else (None, AccountStatus(false))
            } match {
              case Success(r) => r
              case Failure(ex) =>
                logger.error("Cannot find user credentials for ({}), {} ", username, ex.getMessage)
                throw ex
            }
          }
        }
    }

  /**
   * Retrieves the secret key, username and NPA status against the AWS access key.
   *
   * @param awsAccessKey
   * @return
   */
  def getUserSecretWithExtInfo(awsAccessKey: AwsAccessKey): Future[Option[(UserName, AwsSecretKey, NPA, AccountStatus, Set[UserGroup])]] =
    withRedisPool[Option[(UserName, AwsSecretKey, NPA, AccountStatus, Set[UserGroup])]] {
      client =>
        {
          Future {
            val query = new Query(s"@accessKey:{${awsAccessKey.value}}")
            println(s" GET @accessKey:{${awsAccessKey.value}}")
            val results = client.ftSearch("users-index", query);
            if (results.getDocuments().size == 1) {
              val document = results.getDocuments().get(0)
              val username = UserName(document.getId())
              val secretKey = AwsSecretKey(decryptSecret(document.getString("secretKey").trim(), username.value.trim()))
              val isEnabled = Try(document.getString("isEnabled").toBoolean).getOrElse(false)
              val isNPA = Try(document.getString("isNPA").toBoolean).getOrElse(false)
              val groups = Option(document.getString("groups")
                .split(GROUPNAME_SEPARATOR)
                .map(g => UserGroup(g.trim)).toSet)
                .getOrElse(Set.empty[UserGroup])

              Some((username, secretKey, NPA(isNPA), AccountStatus(isEnabled), groups))
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
  def insertAwsCredentials(username: UserName, awsCredential: AwsCredential, isNPA: Boolean): Future[Boolean] =
    withRedisPool[Boolean] {
      client =>
        {
          Future {
            // @TODO check return value and how to handle it
            println(s"Inserting : ${awsCredential.accessKey.value} ${username.value}")
            client.hset(s"users:${username.value}", Map(
              "accessKey" -> awsCredential.accessKey.value,
              "secretKey" -> encryptSecret(awsCredential.secretKey.value.trim(), username.value.trim()),
              "isNPA" -> isNPA.toString(),
              "isEnabled" -> "true",
              "groups" -> "",
            ).asJava)
            true
          }

          // recoverWith {
          //   //A SQL Exception could be thrown as a result of the column accesskey containing a duplicate value
          //   //return a successful future with a false result indicating it did not insert and needs to be retried with a new accesskey
          //   case sqlEx: SQLException if (sqlEx.isInstanceOf[SQLIntegrityConstraintViolationException]
          //     && sqlEx.getErrorCode.equals(MYSQL_DUPLICATE__KEY_ERROR_CODE)) =>
          //     logger.error(sqlEx.getMessage, sqlEx)
          //     Future.successful(false)
          // }
        }
    }

  /**
   * Removes all user groups and inserts the new on from userGroup
   * @param userName
   * @param userGroups
   * @return true if succeeded
   */
  def insertUserGroups(username: UserName, userGroups: Set[UserGroup]): Future[Boolean] =
    withRedisPool[Boolean] {
      client =>
        {
          Future {
            // @TODO handle errors
            client.hset(s"users:${username.value}", "groups", userGroups.mkString(GROUPNAME_SEPARATOR))
            true
          }
        }
    }

  /**
   * Either disables or enables particular user account
   *
   * @param username
   * @param enabled
   * @return
   */
  def setAccountStatus(username: UserName, enabled: Boolean): Future[Boolean] =
    withRedisPool[Boolean] {
      client =>
        {
          Future {
            Try {
              client.hset(s"users:${username.value}", "isEnabled", enabled.toString())
              true
            } match {
              case Success(r) => r
              case Failure(ex) =>
                logger.error("Cannot enable or disable user account {} reason {}", username.value, ex)
                throw ex
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
  def doesUsernameNotExistAndAccessKeyExist(username: UserName, awsAccessKey: AwsAccessKey): Future[Boolean] = {
    Future.sequence(List(doesUsernameExist(username), doesAccessKeyExist(awsAccessKey))).map {
      case List(false, true) => true
      case _                 => false
    }
  }

  def getAllNPAAccounts: Future[NPAAccountList] = {
    withRedisPool {
      client =>
        Future {
          val query = new Query(s"@isNPA:{true}")
          // @TODO HANDLE ERRORS
          val results = client.ftSearch("users-index", query)

          val npaAccounts = results.getDocuments().asScala
            .map(doc => NPAAccount(
              doc.getString("username"),
              Try(doc.getString("isEnabled").toBoolean).getOrElse(false)
            ))

          println(npaAccounts)
          NPAAccountList(npaAccounts.toList)
        }
    }
  }

  private[this] def doesUsernameExist(username: UserName): Future[Boolean] =
    withRedisPool {
      client =>
        {
          Future {
            // @TODO handle errors
            client.exists(s"users:$username")
          }
        }
    }

  private[this] def doesAccessKeyExist(awsAccessKey: AwsAccessKey): Future[Boolean] =
    withRedisPool { client =>
      {
        Future {
          val query = new Query(s"@accessKey:{${awsAccessKey.value}}")
          // @TODO HANDLE ERRORS
          val results = client.ftSearch("users-index", query)
          val accessKeyExists = results.getTotalResults() != 0
          accessKeyExists
        }
      }
    }
}
