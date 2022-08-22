package com.ing.wbaa.rokku.sts.service.db.dao

import java.time.Instant

import akka.actor.ActorSystem
import com.ing.wbaa.rokku.sts.config.{RedisSettings, StsSettings}
import com.ing.wbaa.rokku.sts.data.{UserAssumeRole, UserName}
import com.ing.wbaa.rokku.sts.data.aws.{AwsCredential, AwsSessionToken, AwsSessionTokenExpiration}
import com.ing.wbaa.rokku.sts.service.TokenGeneration
import com.ing.wbaa.rokku.sts.service.db.Redis
import org.scalatest.Assertion
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{BeforeAndAfterAll}
import scala.concurrent.Future
import scala.util.Random
import scala.jdk.CollectionConverters._


class STSTokenDAOItTest extends AsyncWordSpec with STSTokenDAO with STSUserAndGroupDAO with Redis with TokenGeneration with BeforeAndAfterAll {

  val system: ActorSystem = ActorSystem.create("test-system")

  override protected[this] def redisSettings: RedisSettings = RedisSettings(system)

  override protected[this] def stsSettings: StsSettings = StsSettings(system)

  override lazy val dbExecutionContext = executionContext

  override protected def beforeAll(): Unit = {
    // forceInitRedisConnectionPool()
    List("users:", "sessionTokens:").foreach(pattern => {
      val keys = redisConnectionPool.keys(pattern)
      keys.asScala.foreach(key => {
      println(s"WTFFFF  $key")
      redisConnectionPool.del(key)})
    })
  }
    override protected def afterAll(): Unit = {
    List("users:", "sessionTokens:").foreach(pattern => {
      val keys = redisConnectionPool.keys(pattern)
      keys.asScala.foreach(key => {
      println(key)
      redisConnectionPool.del(key)})
    })
  }


  private class TestObject {
    val testAwsSessionToken: AwsSessionToken = AwsSessionToken(Random.alphanumeric.take(32).mkString)
    val username: UserName = UserName(Random.alphanumeric.take(32).mkString)
    val testExpirationDate: AwsSessionTokenExpiration = AwsSessionTokenExpiration(Instant.now())
    val cred: AwsCredential = generateAwsCredential
    val testAwsSessionTokenValid1 = AwsSessionToken(Random.alphanumeric.take(32).mkString)
    val testAwsSessionTokenValid2 = AwsSessionToken(Random.alphanumeric.take(32).mkString)
    val assumeRole = UserAssumeRole("testRole")
  }

  private def withInsertedUser(testCode: UserName => Future[Assertion]): Future[Assertion] = {
    val testObject = new TestObject
    insertAwsCredentials(testObject.username, testObject.cred, isNPA = false)
    testCode(testObject.username)
  }

  "STS Token DAO" should {
    "get Token" that {

      "exists" in withInsertedUser { username =>
        val testObject = new TestObject
        insertToken(testObject.testAwsSessionToken, username, testObject.testExpirationDate)
        getToken(testObject.testAwsSessionToken, username).map { o =>
          assert(o.isDefined)
          assert(o.get._1 == username)
          //is off by milliseconds, because we truncate it, so we match be epoch seconds
          assert(o.get._3.value.getEpochSecond == testObject.testExpirationDate.value.getEpochSecond)
        }
      }

      "doesn't exist" in withInsertedUser { username =>
        getToken(AwsSessionToken("DOESNTEXIST"), username).map { o =>
          assert(o.isEmpty)
        }
      }

    }

    "insert Token" that {
      "new to the db" in withInsertedUser { username =>
        val testObject = new TestObject
        insertToken(testObject.testAwsSessionToken, username, testObject.testExpirationDate)
          .map(r => assert(r))
      }

      "token with same session token already exists " in withInsertedUser { username =>
        val testObject = new TestObject
        insertToken(testObject.testAwsSessionToken, username, testObject.testExpirationDate)
        insertToken(testObject.testAwsSessionToken, username, testObject.testExpirationDate)
          .map(r => assert(!r))
      }
    }

    "insert Token for a role" that {
      "new to the db" in withInsertedUser { username =>
        val testObject = new TestObject
        insertToken(testObject.testAwsSessionToken, username, testObject.assumeRole , testObject.testExpirationDate)
          .map(r => assert(r))
        getToken(testObject.testAwsSessionToken, username).map { o =>
          assert(o.isDefined)
          assert(o.get._1 == username)
          assert(o.get._2 == testObject.assumeRole)
          assert(o.get._3.value.getEpochSecond == testObject.testExpirationDate.value.getEpochSecond)
        }
      }

      "token with same session token already exists " in withInsertedUser { username =>
        val testObject = new TestObject
        insertToken(testObject.testAwsSessionToken, username, testObject.assumeRole, testObject.testExpirationDate)
        insertToken(testObject.testAwsSessionToken, username, testObject.assumeRole, testObject.testExpirationDate)
          .map(r => assert(!r))
      }
    }

    // "clean expired tokens" in {
    //   val testObject = new TestObject
    //   insertAwsCredentials(testObject.username, testObject.cred, isNPA = false)
    //   insertToken(testObject.testAwsSessionTokenValid1, testObject.username, AwsSessionTokenExpiration(Instant.now()))
    //   insertToken(testObject.testAwsSessionToken, testObject.username, AwsSessionTokenExpiration(Instant.now().minus(2, ChronoUnit.DAYS)))
    //   insertToken(testObject.testAwsSessionTokenValid2, testObject.username, AwsSessionTokenExpiration(Instant.now()))
    //   for {
    //     notOldTokenYet <- getToken(testObject.testAwsSessionToken, testObject.username).map(_.isDefined)
    //     notArchTokenYet <- getToken(testObject.testAwsSessionToken, testObject.username, TOKENS_ARCH_TABLE).map(_.isEmpty)
    //     cleanedTokens <- cleanExpiredTokens(AwsSessionTokenExpiration(Instant.now().minus(1, ChronoUnit.DAYS))).map(_ == 1)
    //     tokenOneValid <- getToken(testObject.testAwsSessionTokenValid1, testObject.username).map(_.isDefined)
    //     oldTokenGone <- getToken(testObject.testAwsSessionToken, testObject.username).map(_.isEmpty)
    //     tokenTwoValid <- getToken(testObject.testAwsSessionTokenValid2, testObject.username).map(_.isDefined)
    //     archToken <- getToken(testObject.testAwsSessionToken, testObject.username, TOKENS_ARCH_TABLE).map(_.isDefined)
    //   } yield assert(notOldTokenYet && notArchTokenYet && cleanedTokens && tokenOneValid && oldTokenGone && tokenTwoValid && archToken)
    // }
  }
}
