package com.ing.wbaa.gargoyle.sts.service.db

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.sts.config.GargoyleStsSettings
import com.ing.wbaa.gargoyle.sts.data.aws.AwsSession
import com.ing.wbaa.gargoyle.sts.data.{ UserGroup, UserName }
import com.ing.wbaa.gargoyle.sts.service.TokenGeneration
import org.scalatest.AsyncWordSpec

class TokenDbTest extends AsyncWordSpec {

  private class TestObject extends TokenGeneration {
    val testSystem: ActorSystem = ActorSystem.create("test-system")
    override protected[this] def stsSettings: GargoyleStsSettings = GargoyleStsSettings(testSystem)

    val testSession: AwsSession = generateAwsSession(None)
    val testUserName: UserName = UserName("user")
    val testAssumedUserGroup: Option[UserGroup] = Some(UserGroup("group"))
  }

  "TokenDbTest" should {

    "addCredential" that {
      "succeeds if session unique" in {
        val testObject = new TestObject
        TokenDb.addCredential(testObject.testSession, testObject.testUserName, None).map(
          s => assert(s.contains(testObject.testSession))
        )
      }

      "fails if session already present" in {
        val testObject = new TestObject
        TokenDb.addCredential(testObject.testSession, testObject.testUserName, None).map(
          s => assert(s.contains(testObject.testSession))
        )
        TokenDb.addCredential(testObject.testSession, testObject.testUserName, None).map(
          s => assert(s.isEmpty)
        )
      }
    }

    "getAssumedGroupsForToken" that {
      "token is not present" in {
        val testObject = new TestObject
        TokenDb.getAssumedGroupsForToken(testObject.testSession.sessionToken).map(t => assert(t.isEmpty))
      }

      "token is present but role cannot be assumed" in {
        val testObject = new TestObject
        TokenDb.addCredential(testObject.testSession, testObject.testUserName, None)
        TokenDb.getAssumedGroupsForToken(testObject.testSession.sessionToken).map(t => assert(t.isEmpty))
      }

      "token is present and role can be assumed" in {
        val testObject = new TestObject
        TokenDb.addCredential(testObject.testSession, testObject.testUserName, testObject.testAssumedUserGroup)
        TokenDb.getAssumedGroupsForToken(testObject.testSession.sessionToken).map(t => assert(t == testObject.testAssumedUserGroup))
      }
    }

    "getUserNameAndTokenExpiration" that {
      "token is not present" in {
        val testObject = new TestObject
        TokenDb.getUserNameAndTokenExpiration(testObject.testSession.sessionToken).map(t => assert(t.isEmpty))
      }

      "token is present" in {
        val testObject = new TestObject
        TokenDb.addCredential(testObject.testSession, testObject.testUserName, None)
        TokenDb.getUserNameAndTokenExpiration(testObject.testSession.sessionToken).map(t => assert(t.contains((testObject.testUserName, testObject.testSession.expiration))))
      }
    }

  }
}
