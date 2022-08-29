package com.ing.wbaa.rokku.sts.service.db.dao

import com.ing.wbaa.rokku.sts.data.UserAssumeRole
import com.ing.wbaa.rokku.sts.data.Username
import com.ing.wbaa.rokku.sts.data.aws.AwsSessionToken
import com.ing.wbaa.rokku.sts.data.aws.AwsSessionTokenExpiration
import com.ing.wbaa.rokku.sts.service.db.Redis
import com.ing.wbaa.rokku.sts.service.db.RedisModel
import com.ing.wbaa.rokku.sts.service.db.security.Encryption
import com.typesafe.scalalogging.LazyLogging
import redis.clients.jedis.Jedis

import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

trait STSTokenDAO extends LazyLogging with Encryption with Redis with RedisModel {

  protected[this] implicit def dbExecutionContext: ExecutionContext

  /**
   * Get Token from database against the token session identifier
   *
   * @param awsSessionToken
   * @param userName
   * @return
   */
  def getToken(awsSessionToken: AwsSessionToken, username: Username): Future[Option[(Username, UserAssumeRole, AwsSessionTokenExpiration)]] =
    withRedisPool[Option[(Username, UserAssumeRole, AwsSessionTokenExpiration)]] {
      client =>
        {
          Future {
            val values = client
              .hgetAll(SessionTokenKey(awsSessionToken, username))

            if (values.size() > 0) {
              val assumeRole = getAssumeRole(values.get(SessionTokenFields.assumeRole))
              val expirationDate = AwsSessionTokenExpiration(Instant.parse(values.get(SessionTokenFields.expirationTime)))
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
  def insertToken(awsSessionToken: AwsSessionToken, username: Username, expirationDate: AwsSessionTokenExpiration): Future[Boolean] =
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
  def insertToken(awsSessionToken: AwsSessionToken, username: Username, role: UserAssumeRole, expirationDate: AwsSessionTokenExpiration): Future[Boolean] =
    withRedisPool[Boolean] {
      client =>
        {
          Future {
            val connection = client.getPool().getResource()
            val key = SessionTokenKey(awsSessionToken, username)
            if (!client.exists(key)) {
              val tx = new Jedis(connection).multi()
              tx.hset(key, Map(
                SessionTokenFields.username -> username.value,
                SessionTokenFields.assumeRole -> role.value,
                SessionTokenFields.expirationTime -> expirationDate.value.toString(),
              ).asJava)

              tx.expireAt(key, expirationDate.value.getEpochSecond())
              tx.exec()
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

