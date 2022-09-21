package com.ing.wbaa.rokku.sts.service.db

import com.ing.wbaa.rokku.sts.data.UserGroup
import com.ing.wbaa.rokku.sts.data.Username
import com.ing.wbaa.rokku.sts.data.aws.AwsSessionToken
import com.ing.wbaa.rokku.sts.service.db.security.Encryption
import redis.clients.jedis.JedisPooled
import redis.clients.jedis.exceptions.JedisDataException
import redis.clients.jedis.search.IndexDefinition
import redis.clients.jedis.search.IndexOptions
import redis.clients.jedis.search.Schema

trait RedisModel extends Encryption {
  protected val UsersIndex = "users-idx"

  protected val SessionTokenKeyPrefix = "sessionTokens:"

  protected val UserKeyPrefix = "users:"

  private val GroupnameSeparator = ","

  private val DuplicateIndexExceptionMsg = "Index already exists"

  case class RedisSecondaryIndexException(message: String) extends Exception

  object UserKey {
    def encode(username: Username): String = {
      s"$UserKeyPrefix${username.value}"
    }

    def decode(key: String): Username = {
      Username(key.replace(UserKeyPrefix, ""))
    }
  }

  object SessionTokenKey {
    def apply(sessionToken: AwsSessionToken, username: Username): String = {
      s"$SessionTokenKeyPrefix${encryptSecret(sessionToken.value, username.value)}"
    }
  }

  object UserGroups {
    def encode(groups: Set[UserGroup]): String = {
      groups.mkString(GroupnameSeparator)
    }

    def decode(groupsAsString: String): Set[UserGroup] = {
      groupsAsString.split(GroupnameSeparator)
        .filter(_.trim.nonEmpty)
        .map(g => UserGroup(g.trim)).toSet[UserGroup]
    }
  }

  object UserFields extends Enumeration {
    type UserFields = String

    val accessKey = "accessKey"
    val secretKey = "secretKey"
    val isNPA = "isNPA"
    val isEnabled = "isEnabled"
    val groups = "groups"
  }

  object SessionTokenFields extends Enumeration {
    type SessionTokenFields = Value

    val username = "username"
    val assumeRole = "assumeRole"
    val expirationTime = "expirationTime"
  }

  /**
   * Create secondary search index for users fields
   */
  protected[this] def initializeUserSearchIndex(redisPooledConnection: JedisPooled): Unit = {
    val schema = new Schema()
      .addTagField(UserFields.accessKey)
      .addTagField(UserFields.isNPA)

    val prefixDefinition = new IndexDefinition()
      .setPrefixes(UserKeyPrefix)

    try {
      redisPooledConnection.ftCreate(
        UsersIndex,
        IndexOptions.defaultOptions().setDefinition(prefixDefinition), schema)
      logger.info(s"Created index ${UsersIndex}")
    } catch {
      case exc: JedisDataException =>
        exc.getMessage() match {
          case DuplicateIndexExceptionMsg =>
            logger.info(s"Index ${UsersIndex} already exists. Continuing...")
          case _ =>
            throw new RedisSecondaryIndexException(s"Unable to create index $UsersIndex. Error: ${exc.getMessage()}")
        }
      case exc: Exception =>
        logger.error(s"Unable to create index $UsersIndex. Error: ${exc.getMessage()}")
    }
  }

}
