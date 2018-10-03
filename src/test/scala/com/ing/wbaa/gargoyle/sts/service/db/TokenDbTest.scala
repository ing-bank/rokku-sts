package com.ing.wbaa.gargoyle.sts.service.db

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.sts.config.GargoyleStsSettings
import com.ing.wbaa.gargoyle.sts.data.aws.AwsSession
import com.ing.wbaa.gargoyle.sts.data.{ UserAssumedGroup, UserName }
import com.ing.wbaa.gargoyle.sts.service.TokenGeneration
import org.scalatest.{ AsyncWordSpec, PrivateMethodTester }

import scala.util.Random

class TokenDbTest extends AsyncWordSpec with TokenDb with PrivateMethodTester {

  val testSystem: ActorSystem = ActorSystem.create("test-system")
  override protected[this] val stsSettings: GargoyleStsSettings = GargoyleStsSettings(testSystem)

  private class TestObject extends TokenGeneration {
    override protected[this] def stsSettings: GargoyleStsSettings = GargoyleStsSettings(testSystem)

    val testSession: AwsSession = generateAwsSession(None)
    val testUserName: UserName = UserName(Random.alphanumeric.take(32).mkString)
    val testAssumedUserGroup: Option[UserAssumedGroup] = Some(UserAssumedGroup(Random.alphanumeric.take(32).mkString))
  }

  "TokenDbTest" should {

    "addCredential" that {

      "succeeds if session unique" in {
        val testObject = new TestObject
        val result = addCredential(testObject.testSession, testObject.testUserName, None)

        result.map(
          s => assert(s.contains(testObject.testSession))
        )
      }

      "fails if session already present" in {
        val testObject = new TestObject
        val result1 = addCredential(testObject.testSession, testObject.testUserName, None)
        result1.map(
          s => assert(s.contains(testObject.testSession))
        )
        val result2 = addCredential(testObject.testSession, testObject.testUserName, None)
        result2.map(
          s => assert(s.isEmpty)
        )
      }
    }

    "getAssumedGroupsForToken" that {
      "token is not present" in {
        val testObject = new TestObject
        getAssumedGroupsForToken(testObject.testSession.sessionToken).map(t => assert(t.isEmpty))
      }

      "token is present but role cannot be assumed" in {
        val testObject = new TestObject
        getNewAwsSession(testObject.testUserName, None, None).flatMap { testSession =>
          getAssumedGroupsForToken(testSession.sessionToken).map(t => assert(t.isEmpty))
        }
      }

      "token is present and role can be assumed" in {
        val testObject = new TestObject
        getNewAwsSession(testObject.testUserName, None, testObject.testAssumedUserGroup).flatMap { testSession =>
          getAssumedGroupsForToken(testSession.sessionToken).map(t => assert(t == testObject.testAssumedUserGroup))
        }
      }
    }

    "getUserNameAndTokenExpiration" that {
      "token is not present" in {
        val testObject = new TestObject
        getTokenExpiration(testObject.testSession.sessionToken).map(t => assert(t.isEmpty))
      }

      "token is present" in {
        val testObject = new TestObject
        getNewAwsSession(testObject.testUserName, None, None).flatMap { testSession =>
          getTokenExpiration(testSession.sessionToken).map(t => assert(t.contains(testSession.expiration)))
        }
      }
    }

  }
}
