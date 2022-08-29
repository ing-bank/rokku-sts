package com.ing.wbaa.rokku.sts.service.db.dao

import com.ing.wbaa.rokku.sts.data.{ UserAssumeRole, UserName }
import com.ing.wbaa.rokku.sts.data.aws.{ AwsSessionToken, AwsSessionTokenExpiration }
import com.ing.wbaa.rokku.sts.service.db.security.Encryption
import com.typesafe.scalalogging.LazyLogging
import redis.clients.jedis.{ Jedis }
import com.ing.wbaa.rokku.sts.service.db.Redis
import java.time.Instant
import scala.jdk.CollectionConverters._

import scala.concurrent.{ ExecutionContext, Future }

trait STSTokenDAO extends LazyLogging with Encryption with Redis {

  protected[this] implicit def dbExecutionContext: ExecutionContext

  private val SessionTokensKeyPrefix = "sessionTokens:"

  /**
   * Get Token from database against the token session identifier
   *
   * @param awsSessionToken
   * @param userName
   * @return
   */
  def getToken(awsSessionToken: AwsSessionToken, username: UserName): Future[Option[(UserName, UserAssumeRole, AwsSessionTokenExpiration)]] =
    withRedisPool[Option[(UserName, UserAssumeRole, AwsSessionTokenExpiration)]] {
      client =>
        {
          Future {
            val values = client
              .hgetAll(s"$SessionTokensKeyPrefix${encryptSecret(awsSessionToken.value.trim(), username.value.trim())}")

            if (values.size() > 0) {
              val assumeRole = getAssumeRole(values.get("assumeRole"))
              val expirationDate = AwsSessionTokenExpiration(Instant.parse(values.get("expirationTime")))
              logger.debug("getToken {} expire {}", awsSessionToken, expirationDate)
              Some((username, assumeRole, expirationDate))
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
    insertToken(awsSessionToken, username, UserAssumeRole(""), expirationDate)

  /**
   * Insert a token item into the database
   *
   * @param awsSessionToken
   * @param username
   * @param role
   * @param expirationDate
   * @return
   */
  def insertToken(awsSessionToken: AwsSessionToken, username: UserName, role: UserAssumeRole, expirationDate: AwsSessionTokenExpiration): Future[Boolean] =
    withRedisPool[Boolean] {
      client =>
        {
          Future {
            val connection = client.getPool().getResource()
            val key = s"$SessionTokensKeyPrefix${encryptSecret(awsSessionToken.value.trim(), username.value.trim())}"
            if (!client.exists(key)) {
              val trx = new Jedis(connection).multi()
              trx.hset(key, Map(
                "username" -> username.value,
                "assumeRole" -> role.value,
                "expirationTime" -> expirationDate.value.toString(),
              ).asJava)

              trx.expireAt(key, expirationDate.value.getEpochSecond())
              trx.exec()
              connection.close()
              true
            } else false
          }
        }
    }

  def getAssumeRole(role: String): UserAssumeRole = {
    if (role == null || role.equals("null")) UserAssumeRole("") else UserAssumeRole(role)
  }
}

