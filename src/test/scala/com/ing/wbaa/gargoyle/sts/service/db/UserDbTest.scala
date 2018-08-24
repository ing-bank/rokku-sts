package com.ing.wbaa.gargoyle.sts.service.db

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.sts.config.GargoyleStsSettings
import com.ing.wbaa.gargoyle.sts.data.UserName
import com.ing.wbaa.gargoyle.sts.data.aws.AwsCredential
import com.ing.wbaa.gargoyle.sts.service.TokenGeneration
import org.scalatest.AsyncWordSpec

import scala.util.Random

class UserDbTest extends AsyncWordSpec {

  private class TestObject extends TokenGeneration {
    private val testSystem: ActorSystem = ActorSystem.create("test-system")
    override protected[this] def stsSettings: GargoyleStsSettings = GargoyleStsSettings(testSystem)

    val cred: AwsCredential = generateAwsCredential
    val userName: UserName = UserName(Random.alphanumeric.take(32).mkString)
  }

  "UserDb" should {
    "add Users" that {
      "are new in the db and have a unique accesskey" in {
        val testObject = new TestObject
        UserDb.addToUserStore(testObject.userName, testObject.cred).map(c => assert(c.contains(testObject.cred)))
      }

      "are already present in the db and have a unique accesskey" in {
        val testObject = new TestObject
        val newCred = testObject.generateAwsCredential
        UserDb.addToUserStore(testObject.userName, testObject.cred).map(c => assert(c.contains(testObject.cred)))
        UserDb.addToUserStore(testObject.userName, newCred).map(c => assert(c.contains(newCred)))
      }

      "have an already existing accesskey" in {
        val testObject = new TestObject
        UserDb.addToUserStore(testObject.userName, testObject.cred).map(c => assert(c.contains(testObject.cred)))
        UserDb.addToUserStore(testObject.userName, testObject.cred).map(c => assert(c.isEmpty))
      }
    }

    "get AwsCredential" that {
      "exists" in {
        val testObject = new TestObject
        UserDb.addToUserStore(testObject.userName, testObject.cred)
        UserDb.getAwsCredential(testObject.userName).map(c => assert(c.contains(testObject.cred)))
      }

      "does not exist" in {
        val testObject = new TestObject
        UserDb.getAwsCredential(testObject.userName).map(c => assert(c.isEmpty))
      }
    }

    "get User" that {
      "exists with accesskey" in {
        val testObject = new TestObject
        UserDb.addToUserStore(testObject.userName, testObject.cred)
        UserDb.getUser(testObject.cred.accessKey).map(c => assert(c.contains(testObject.userName)))
      }

      "doesn't exist with accesskey" in {
        val testObject = new TestObject
        UserDb.getUser(testObject.cred.accessKey).map(c => assert(c.isEmpty))
      }
    }
  }
}
