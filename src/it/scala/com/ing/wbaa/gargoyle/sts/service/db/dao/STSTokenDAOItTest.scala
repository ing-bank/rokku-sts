package com.ing.wbaa.gargoyle.sts.service.db.dao

import java.time.Instant

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.sts.config.GargoyleMariaDBSettings
import com.ing.wbaa.gargoyle.sts.data.{UserAssumedGroup, UserName}
import com.ing.wbaa.gargoyle.sts.data.aws.{AwsSessionToken, AwsSessionTokenExpiration}
import com.ing.wbaa.gargoyle.sts.service.db.MariaDb
import org.scalatest.AsyncWordSpec

import scala.util.Random

class STSTokenDAOItTest extends AsyncWordSpec with STSTokenDAO with MariaDb {

  val testSystem: ActorSystem = ActorSystem.create("test-system")

  override protected[this] def gargoyleMariaDBSettings: GargoyleMariaDBSettings = GargoyleMariaDBSettings(testSystem)

  private class TestObject {
    val testAwsSessionToken: AwsSessionToken = AwsSessionToken(Random.alphanumeric.take(32).mkString)
    val testUserName: UserName = UserName(Random.alphanumeric.take(32).mkString)
    val testExpirationDate: AwsSessionTokenExpiration = AwsSessionTokenExpiration(Instant.now())
    val testAssumedUserGroup: UserAssumedGroup = UserAssumedGroup(Random.alphanumeric.take(32).mkString)
  }

  "STS Token DAO" should {
    "get Token" that {

      "exists" in {
        val testObject = new TestObject
        insertToken(testObject.testAwsSessionToken, testObject.testUserName, testObject.testExpirationDate, Some(testObject.testAssumedUserGroup))
        getToken(testObject.testAwsSessionToken).map{ o =>
          assert(o.isDefined)
          assert(o.get._1 == testObject.testUserName)
          assert(o.get._2 == testObject.testExpirationDate)
          assert(o.get._3 == testObject.testAssumedUserGroup)
        }
      }

      "doesn't exist" in {
        getToken(AwsSessionToken("DOESNTEXIST")).map { o =>
          assert(o.isEmpty)
        }
      }
    }

    "insert Token" that {
      "new to the db" in {
        val testObject = new TestObject
        insertToken(testObject.testAwsSessionToken, testObject.testUserName, testObject.testExpirationDate, Some(testObject.testAssumedUserGroup))
          .map(r => assert(r))
      }

      "token with same session token already exists " in {
        val testObject = new TestObject
        insertToken(testObject.testAwsSessionToken, testObject.testUserName, testObject.testExpirationDate, Some(testObject.testAssumedUserGroup))
          .map(r => assert(r))
        insertToken(testObject.testAwsSessionToken, UserName("u"), testObject.testExpirationDate, Some(UserAssumedGroup("uag")))
          .map(r => assert(r))
      }
    }
  }

}
