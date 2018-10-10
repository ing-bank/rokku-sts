package com.ing.wbaa.gargoyle.sts.service.db.dao

import java.time.Instant

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.sts.config.{GargoyleMariaDBSettings, GargoyleStsSettings}
import com.ing.wbaa.gargoyle.sts.data.{UserAssumedGroup, UserName}
import com.ing.wbaa.gargoyle.sts.data.aws.{AwsCredential, AwsSessionToken, AwsSessionTokenExpiration}
import com.ing.wbaa.gargoyle.sts.service.TokenGeneration
import com.ing.wbaa.gargoyle.sts.service.db.MariaDb
import org.scalatest.{Assertion, AsyncWordSpec}

import scala.concurrent.Future
import scala.util.Random

class STSTokenDAOItTest extends AsyncWordSpec with STSTokenDAO with STSUserDAO with MariaDb with TokenGeneration {

  val testSystem: ActorSystem = ActorSystem.create("test-system")

  override protected[this] def gargoyleMariaDBSettings: GargoyleMariaDBSettings = GargoyleMariaDBSettings(testSystem)

  override protected[this] def stsSettings: GargoyleStsSettings = GargoyleStsSettings(testSystem)

  private class TestObject {
    val testAwsSessionToken: AwsSessionToken = AwsSessionToken(Random.alphanumeric.take(32).mkString)
    val userName: UserName = UserName(Random.alphanumeric.take(32).mkString)
    val testExpirationDate: AwsSessionTokenExpiration = AwsSessionTokenExpiration(Instant.now())
    val testAssumedUserGroup: UserAssumedGroup = UserAssumedGroup(Random.alphanumeric.take(32).mkString)
    val cred: AwsCredential = generateAwsCredential
  }

  private def withInsertedUser(testCode: UserName => Future[Assertion]): Future[Assertion] = {
    val testObject = new TestObject
    insertAwsCredentials(testObject.userName, testObject.cred, false)
    testCode(testObject.userName)
  }

  "STS Token DAO" should {
    "get Token" that {

      "exists" in withInsertedUser { userName =>
        val testObject = new TestObject
        insertToken(testObject.testAwsSessionToken, userName, testObject.testExpirationDate, Some(testObject.testAssumedUserGroup))
        getToken(testObject.testAwsSessionToken).map { o =>
          assert(o.isDefined)
          assert(o.get._1 == userName)
          //is off by milliseconds, because we truncate it, so we match be epoch seconds
          assert(o.get._2.value.getEpochSecond == testObject.testExpirationDate.value.getEpochSecond)
          assert(o.get._3.contains(testObject.testAssumedUserGroup))
        }
      }

      "doesn't exist" in {
        getToken(AwsSessionToken("DOESNTEXIST")).map { o =>
          assert(o.isEmpty)
        }
      }

      "doesn't have a assumed user group" in withInsertedUser { userName =>
        val testObject = new TestObject
        insertToken(testObject.testAwsSessionToken, userName, testObject.testExpirationDate, None)
        getToken(testObject.testAwsSessionToken).map { o =>
          assert(o.isDefined)
          assert(o.get._1 == userName)
          //is off by milliseconds, because we truncate it, so we match be epoch seconds
          assert(o.get._2.value.getEpochSecond == testObject.testExpirationDate.value.getEpochSecond)
          assert(o.get._3.isEmpty)
        }
      }
    }

    "insert Token" that {
      "new to the db" in withInsertedUser { userName =>
        val testObject = new TestObject
        insertToken(testObject.testAwsSessionToken, userName, testObject.testExpirationDate, Some(testObject.testAssumedUserGroup))
          .map(r => assert(r))
      }

      "token with same session token already exists " in withInsertedUser { userName =>
        val testObject = new TestObject
        insertToken(testObject.testAwsSessionToken, userName, testObject.testExpirationDate, Some(testObject.testAssumedUserGroup))
          .map(r => assert(r))

        insertToken(testObject.testAwsSessionToken, UserName("u"), testObject.testExpirationDate, Some(UserAssumedGroup("uag")))
          .map(r => assert(!r))
      }
    }
  }

}
