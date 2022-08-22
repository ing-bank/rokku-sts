package com.ing.wbaa.rokku.sts.service.db.dao

import com.ing.wbaa.rokku.sts.data.{ UserAssumeRole, UserName }
import com.ing.wbaa.rokku.sts.data.aws.{ AwsSessionToken, AwsSessionTokenExpiration }
import com.ing.wbaa.rokku.sts.service.db.security.Encryption
import com.typesafe.scalalogging.LazyLogging
import redis.clients.jedis.{ JedisPooled, Jedis }
import java.time.Instant
import scala.jdk.CollectionConverters._

import scala.concurrent.{ ExecutionContext, Future }

trait STSTokenDAO extends LazyLogging with Encryption {

  protected[this] implicit def dbExecutionContext: ExecutionContext

  protected[this] def withRedisPool[T](f: JedisPooled => Future[T]): Future[T]

  private[this] val TOKENS_PREFIX = "sessionTokens:"

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
              .hgetAll(s"${TOKENS_PREFIX}${encryptSecret(awsSessionToken.value.trim(), username.value.trim())}")

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
            val key = s"${TOKENS_PREFIX}${encryptSecret(awsSessionToken.value.trim(), username.value.trim())}"

            if (!client.exists(key)) {
              val trx = new Jedis(connection).multi()
              trx.hset(key, Map(
                "username" -> username.value,
                "assumeRole" -> role.value,
                "expirationTime" -> expirationDate.value.toString(),
              ).asJava)

              trx.expireAt(key, expirationDate.value.toEpochMilli())
              // @TODO check what exec returns
              trx.exec()
              connection.close()
              true
            } else {
              //A SQL Exception could be thrown as a result of the column sessiontoken containing a duplicate value
              //return a successful future with a false result indicating it did not insert and needs to be retried with a new sessiontoken
              false
            }
          }
        }
    }

  // /**
  //  * Remove all expired tokens (after the expirationDate)
  //  *   - move to archive table
  //  *   - delete from original table
  //  * @param expirationDate after the date the tokens are removed
  //  * @return how many tokens have been removed
  //  */
  // def cleanExpiredTokens(expirationDate: AwsSessionTokenExpiration): Future[Int] = {
  //   withMariaDbConnection[Int] {
  //     connection =>
  //       val archiveTokensQuery = s"insert into $TOKENS_ARCH_TABLE select *, now() from $TOKENS_TABLE where expirationtime < ?"
  //       val deleteOldTokenQuery = s"delete from $TOKENS_TABLE where expirationtime < ?"
  //       Future {
  //         val preparedStmArch = connection.prepareStatement(archiveTokensQuery)
  //         preparedStmArch.setTimestamp(1, Timestamp.from(expirationDate.value))
  //         val archRecords = preparedStmArch.executeUpdate()
  //         preparedStmArch.close()

  //         val preparedStmDel = connection.prepareStatement(deleteOldTokenQuery)
  //         preparedStmDel.setTimestamp(1, Timestamp.from(expirationDate.value))
  //         val delRecords = preparedStmDel.executeUpdate()
  //         preparedStmDel.close()
  //         logger.info(s"archived {} tokens and deleted {}", archRecords, delRecords)
  //         delRecords
  //       }
  //   }
  // }

  def getAssumeRole(role: String): UserAssumeRole = {
    if (role == null || role.equals("null")) UserAssumeRole("") else UserAssumeRole(role)
  }
}

