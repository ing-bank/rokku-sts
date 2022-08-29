package com.ing.wbaa.rokku.sts.service.db.dao

import java.time.Instant

import akka.actor.ActorSystem
import com.ing.wbaa.rokku.sts.config.{ RedisSettings, StsSettings }
import com.ing.wbaa.rokku.sts.data.{ UserAssumeRole, UserName }
import com.ing.wbaa.rokku.sts.data.aws.{ AwsCredential, AwsSessionToken, AwsSessionTokenExpiration }
import com.ing.wbaa.rokku.sts.service.TokenGeneration
import com.ing.wbaa.rokku.sts.service.db.Redis
import org.scalatest.Assertion
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{ BeforeAndAfterAll }
import scala.concurrent.Future
import scala.util.Random
import scala.jdk.CollectionConverters._

class STSTokenDAOItTest extends AsyncWordSpec with STSTokenDAO with STSUserAndGroupDAO with Redis with TokenGeneration with BeforeAndAfterAll {

  val system: ActorSystem = ActorSystem.create("test-system")

  override protected[this] def redisSettings: RedisSettings = RedisSettings(system)

  override protected[this] def stsSettings: StsSettings = StsSettings(system)

  override lazy val dbExecutionContext = executionContext

  override protected def beforeAll(): Unit = {
    forceInitRedisConnectionPool()
  }

  override protected def afterAll(): Unit = {
    List("users:*", "sessionTokens:*").foreach(pattern => {
      val keys = redisConnectionPool.keys(pattern)
      keys.asScala.foreach(key => {
        redisConnectionPool.del(key)
      })
    })
  }

  private class TestObject {
    val testAwsSessionToken: AwsSessionToken = AwsSessionToken(Random.alphanumeric.take(32).mkString)
    val username: UserName = UserName(Random.alphanumeric.take(32).mkString)
    val testExpirationDate: AwsSessionTokenExpiration = AwsSessionTokenExpiration(Instant.now().plusSeconds(120))
    val cred: AwsCredential = generateAwsCredential
    val testAwsSessionTokenValid1 = AwsSessionToken(Random.alphanumeric.take(32).mkString)
    val testAwsSessionTokenValid2 = AwsSessionToken(Random.alphanumeric.take(32).mkString)
    val assumeRole = UserAssumeRole("testRole")
  }

  private def withInsertedUser(testCode: UserName => Future[Assertion]): Future[Assertion] = {
    val testObject = new TestObject
    insertAwsCredentials(testObject.username, testObject.cred, isNPA = false).flatMap { _ =>
      testCode(testObject.username)
    }
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
      "that expires after 1 millisecond" in {
        val testObject = new TestObject
        insertToken(testObject.testAwsSessionToken, UserName("boom"), testObject.assumeRole,
          AwsSessionTokenExpiration(Instant.now().plusMillis(1))).flatMap { inserted =>
            assert(inserted)
            getToken(testObject.testAwsSessionToken, UserName("boom")).map { o =>
              assert(o.isEmpty)
            }
          }
      }
    }

    "insert token" that {
      "new to the db" in withInsertedUser { username =>
        val testObject = new TestObject
        insertToken(testObject.testAwsSessionToken, username, testObject.testExpirationDate)
          .map(r => assert(r))
      }

      "token with same session token already exists " in withInsertedUser { username =>
        val testObject = new TestObject
        insertToken(testObject.testAwsSessionToken, username, testObject.testExpirationDate).flatMap { _ =>
          insertToken(testObject.testAwsSessionToken, username, testObject.testExpirationDate)
            .map(r => assert(!r))
        }
      }
    }

    "insert token for a role" that {
      "new to the db" in withInsertedUser { username =>
        val testObject = new TestObject
        insertToken(testObject.testAwsSessionToken, username, testObject.assumeRole, testObject.testExpirationDate)
          .flatMap { r =>
            assert(r)
            getToken(testObject.testAwsSessionToken, username).map { o =>
              assert(o.isDefined)
              assert(o.get._1 == username)
              assert(o.get._2 == testObject.assumeRole)
              assert(o.get._3.value.getEpochSecond == testObject.testExpirationDate.value.getEpochSecond)
            }
          }
      }

      "token with same session token already exists " in withInsertedUser { username =>
        val testObject = new TestObject
        insertToken(testObject.testAwsSessionToken, username, testObject.assumeRole, testObject.testExpirationDate).flatMap { _ =>
          insertToken(testObject.testAwsSessionToken, username, testObject.assumeRole, testObject.testExpirationDate)
            .map(r => assert(!r))
        }
      }
    }

  }
}
