package com.ing.wbaa.rokku.sts.service.db.dao

import com.ing.wbaa.rokku.sts.data.aws.{ AwsAccessKey, AwsCredential, AwsSecretKey }
import com.ing.wbaa.rokku.sts.data.{ AccountStatus, NPA, NPAAccount, NPAAccountList, UserGroup, Username }
import com.ing.wbaa.rokku.sts.service.db.security.Encryption
import com.ing.wbaa.rokku.sts.service.db.Redis
import com.typesafe.scalalogging.LazyLogging
import redis.clients.jedis.search.{ Query }
import scala.jdk.CollectionConverters._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

trait STSUserDAO extends LazyLogging with Encryption with Redis {

  protected[this] implicit def dbExecutionContext: ExecutionContext

  private val UsersKeyPrefix = "users:"
  private val GroupnameSeparator = ","

  /**
   * Retrieves AWS user credentials based on the username
   *
   * @param userName The username to search an entry against
   */
  def getAwsCredentialAndStatus(username: Username): Future[(Option[AwsCredential], AccountStatus)] =
    withRedisPool[(Option[AwsCredential], AccountStatus)] {
      client =>
        {
          Future {
            Try {
              val values = client
                .hgetAll(s"$UsersKeyPrefix${username.value}")

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
  def getUserSecretWithExtInfo(awsAccessKey: AwsAccessKey): Future[Option[(Username, AwsSecretKey, NPA, AccountStatus, Set[UserGroup])]] =
    withRedisPool[Option[(Username, AwsSecretKey, NPA, AccountStatus, Set[UserGroup])]] {
      client =>
        {
          Future {
            val query = new Query(s"@accessKey:{${awsAccessKey.value}}")
            val results = client.ftSearch(UsersIndex, query);
            if (results.getDocuments().size == 1) {
              val document = results.getDocuments().get(0)
              val username = Username(document.getId().replace(UsersKeyPrefix, ""))
              val secretKey = AwsSecretKey(decryptSecret(document.getString("secretKey").trim(), username.value.trim()))
              val isEnabled = Try(document.getString("isEnabled").toBoolean).getOrElse(false)
              val isNPA = Try(document.getString("isNPA").toBoolean).getOrElse(false)
              val groups = document.getString("groups")
                .split(GroupnameSeparator)
                .filter(_.trim.nonEmpty)
                .map(g => UserGroup(g.trim)).toSet[UserGroup]

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
  def insertAwsCredentials(username: Username, awsCredential: AwsCredential, isNPA: Boolean): Future[Boolean] =
    withRedisPool[Boolean] {
      client =>
        {
          doesUsernameNotExistAndAccessKeyNotExist(username, awsCredential.accessKey).map {
            case true =>
              client.hset(s"$UsersKeyPrefix${username.value}", Map(
                "accessKey" -> awsCredential.accessKey.value,
                "secretKey" -> encryptSecret(awsCredential.secretKey.value.trim(), username.value.trim()),
                "isNPA" -> isNPA.toString(),
                "isEnabled" -> "true",
                "groups" -> "",
              ).asJava)
              true
            case false => false

          }
        }
    }

  /**
   * Removes all user groups and inserts the new on from userGroup
   * @param userName
   * @param userGroups
   * @return true if succeeded
   */
  def insertUserGroups(username: Username, userGroups: Set[UserGroup]): Future[Boolean] =
    withRedisPool[Boolean] {
      client =>
        {
          Future {
            client.hset(s"users:${username.value}", "groups", userGroups.mkString(GroupnameSeparator))
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
  def setAccountStatus(username: Username, enabled: Boolean): Future[Boolean] =
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
  def doesUsernameNotExistAndAccessKeyExist(username: Username, awsAccessKey: AwsAccessKey): Future[Boolean] = {
    Future.sequence(List(doesUsernameExist(username), doesAccessKeyExist(awsAccessKey))).map {
      case List(false, true) => true
      case _                 => false
    }
  }

  def getAllNPAAccounts: Future[NPAAccountList] = {
    withRedisPool {
      client =>
        Future {
          val query = new Query("@isNPA:{true}")
          // @TODO HANDLE ERRORS
          val results = client.ftSearch(UsersIndex, query)

          val npaAccounts = results.getDocuments().asScala
            .map(doc => {
              NPAAccount(
                doc.getId().replace("users:", ""),
                Try(doc.getString("isEnabled").toBoolean).getOrElse(false)
              )
            })

          NPAAccountList(npaAccounts.toList)
        }
    }
  }

  private[this] def doesUsernameNotExistAndAccessKeyNotExist(username: Username, awsAccessKey: AwsAccessKey): Future[Boolean] = {
    Future.sequence(List(doesUsernameExist(username), doesAccessKeyExist(awsAccessKey))).map {
      case List(false, false) => true
      case _                  => false
    }
  }

  private[this] def doesUsernameExist(username: Username): Future[Boolean] =
    withRedisPool {
      client =>
        {
          Future {
            client.exists(s"users:${username.value}")
          }
        }
    }

  private[this] def doesAccessKeyExist(awsAccessKey: AwsAccessKey): Future[Boolean] =
    withRedisPool { client =>
      {
        Future {
          val query = new Query(s"@accessKey:{${awsAccessKey.value}}")
          // @TODO HANDLE ERRORS
          val results = client.ftSearch(UsersIndex, query)
          val accessKeyExists = results.getTotalResults() != 0
          accessKeyExists
        }
      }
    }
}
