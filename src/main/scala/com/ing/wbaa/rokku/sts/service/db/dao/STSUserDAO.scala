package com.ing.wbaa.rokku.sts.service.db.dao

import com.ing.wbaa.rokku.sts.data.AccountStatus
import com.ing.wbaa.rokku.sts.data.NPA
import com.ing.wbaa.rokku.sts.data.NPAAccount
import com.ing.wbaa.rokku.sts.data.NPAAccountList
import com.ing.wbaa.rokku.sts.data.UserGroup
import com.ing.wbaa.rokku.sts.data.Username
import com.ing.wbaa.rokku.sts.data.UserAccount
import com.ing.wbaa.rokku.sts.data.aws.AwsAccessKey
import com.ing.wbaa.rokku.sts.data.aws.AwsCredential
import com.ing.wbaa.rokku.sts.data.aws.AwsSecretKey
import com.ing.wbaa.rokku.sts.service.db.Redis
import com.ing.wbaa.rokku.sts.service.db.RedisModel
import com.ing.wbaa.rokku.sts.service.db.security.Encryption
import com.typesafe.scalalogging.LazyLogging
import redis.clients.jedis.search.Query
import scala.collection.immutable.Set

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

trait STSUserDAO extends LazyLogging with Encryption with Redis with RedisModel {

  protected[this] implicit def dbExecutionContext: ExecutionContext

  /**
   * Retrieves AWS user credentials along with the user state based on the username
   *
   * @param userName The username to search an entry against
   */
  def getUserAccountByName(username: Username): Future[(UserAccount)] =
    withRedisPool[UserAccount] {
      client =>
        {
          Future {
            Try {
              val values = client
                .hgetAll(UserKey.encode(username))

              if (values.size() > 0) {
                val accessKey = AwsAccessKey(values.get(UserFields.accessKey))
                val secretKey = AwsSecretKey(decryptSecret(values.get(UserFields.secretKey).trim(), username.value.trim()))
                val isEnabled = values.get(UserFields.isEnabled).toBooleanOption.getOrElse(false)
                val isNPA = Try(values.get(UserFields.isNPA).toBoolean).getOrElse(false)
                val groups = UserGroups.decode(values.get(UserFields.groups))

                val userAccount = UserAccount(
                  username,
                  Some(AwsCredential(accessKey, secretKey)),
                  AccountStatus(values.get(UserFields.isEnabled).toBooleanOption.getOrElse(false)),
                  NPA(isNPA),
                  groups,
                )

                userAccount
              } else UserAccount(username, None, AccountStatus(false), NPA(false), Set.empty[UserGroup])
            } match {
              case Success(r) => r
              case Failure(ex) =>
                logger.error(s"getUserAccountByName(${username.value} failed: ${ex.getMessage}")
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
  def getUserUserAccountByAccessKey(awsAccessKey: AwsAccessKey): Future[Option[(Username, AwsSecretKey, NPA, AccountStatus, Set[UserGroup])]] =
    withRedisPool[Option[(Username, AwsSecretKey, NPA, AccountStatus, Set[UserGroup])]] {
      client =>
        {
          Future {
            val query = new Query(s"@${UserFields.accessKey}:{${awsAccessKey.value}}")
            val results = client.ftSearch(UsersIndex, query);
            if (results.getDocuments().size == 1) {
              val document = results.getDocuments().get(0)
              val username = UserKey.decode(document.getId())
              val secretKey = AwsSecretKey(decryptSecret(document.getString(UserFields.secretKey).trim(), username.value.trim()))
              val isEnabled = Try(document.getString(UserFields.isEnabled).toBoolean).getOrElse(false)
              val isNPA = Try(document.getString(UserFields.isNPA).toBoolean).getOrElse(false)
              val groups = UserGroups.decode(document.getString(UserFields.groups))

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
              client.hset(UserKey.encode(username), Map(
                UserFields.accessKey -> awsCredential.accessKey.value,
                UserFields.secretKey -> encryptSecret(awsCredential.secretKey.value.trim(), username.value.trim()),
                UserFields.isNPA -> isNPA.toString(),
                UserFields.isEnabled -> "true",
                UserFields.groups -> "",
              ).asJava)
              true
            case false => false

          }
        }
    }

  /**
   * Sets the user groups for the target user
   * @param userName
   * @param userGroups
   * @return true if succeeded
   */
  def setUserGroups(username: Username, userGroups: Set[UserGroup]): Future[Boolean] =
    withRedisPool[Boolean] {
      client =>
        {
          Future {
            client.hset(UserKey.encode(username), UserFields.groups, UserGroups.encode(userGroups))
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
              client.hset(UserKey.encode(username), UserFields.isEnabled, enabled.toString())
              true
            } match {
              case Success(r) => r
              case Failure(ex) =>
                logger.error(s"setAccountStatus(${username.value}) failed: ${ex.getMessage}")
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
          // val query = new Query(s"@isNpa:{true}")
          val query = new Query(s"@${UserFields.isNPA}:{true}")
          val results = client.ftSearch(UsersIndex, query)
          val npaAccounts = results.getDocuments().asScala
            .map(doc => {
              NPAAccount(
                UserKey.decode(doc.getId()).value,
                Try(doc.getString(UserFields.isEnabled).toBoolean).getOrElse(false)
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

  protected[this] def doesUsernameExist(username: Username): Future[Boolean] =
    withRedisPool {
      client =>
        {
          Future {
            client.exists(UserKey.encode(username))
          }
        }
    }

  protected[this] def doesAccessKeyExist(awsAccessKey: AwsAccessKey): Future[Boolean] =
    withRedisPool { client =>
      {
        Future {
          val query = new Query(s"@${UserFields.accessKey}:{${awsAccessKey.value}}")
          val results = client.ftSearch(UsersIndex, query)
          val accessKeyExists = results.getTotalResults() != 0
          accessKeyExists
        }
      }
    }
}
