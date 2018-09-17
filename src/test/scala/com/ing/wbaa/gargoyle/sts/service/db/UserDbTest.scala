package com.ing.wbaa.gargoyle.sts.service.db

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.sts.config.GargoyleStsSettings
import com.ing.wbaa.gargoyle.sts.data.UserName
import com.ing.wbaa.gargoyle.sts.data.aws.AwsCredential
import org.scalatest.{ AsyncWordSpec, PrivateMethodTester }

import scala.util.Random

class UserDbTest extends AsyncWordSpec with UserDb with TokenGeneration with PrivateMethodTester {
  val testSystem: ActorSystem = ActorSystem.create("test-system")

  override val stsSettings: GargoyleStsSettings = GargoyleStsSettings(testSystem)

  private class TestObject {
    val cred: AwsCredential = generateAwsCredential
    val userName: UserName = UserName(Random.alphanumeric.take(32).mkString)
  }

  "UserDb" should {
    "add Users" that {
      "are new in the db and have a unique accesskey" in {
        val testObject = new TestObject
        val result = addToUserStore(testObject.userName, testObject.cred)
        result.map(c => assert(c.contains(testObject.cred)))
      }

      "are already present in the db and have a unique accesskey" in {
        val testObject = new TestObject
        val newCred = generateAwsCredential
        val result1 = addToUserStore(testObject.userName, testObject.cred)
        result1.map(c => assert(c.contains(testObject.cred)))
        val result2 = addToUserStore(testObject.userName, newCred)
        result2.map(c => assert(c.contains(newCred)))
      }

      "have an already existing accesskey" in {
        val testObject = new TestObject
        val result1 = addToUserStore(testObject.userName, testObject.cred)
        result1.map(c => assert(c.contains(testObject.cred)))
        val result2 = addToUserStore(testObject.userName, testObject.cred)
        result2.map(c => assert(c.isEmpty))
      }
    }

    "get AwsCredential" that {
      "exists" in {
        val testObject = new TestObject
        getOrGenerateAwsCredential(testObject.userName).flatMap { testCred =>
          getAwsCredential(testObject.userName).map(c => assert(c.contains(testCred)))
        }
      }

      "does not exist" in {
        val testObject = new TestObject
        getAwsCredential(testObject.userName).map(c => assert(c.isEmpty))
      }
    }

    "get User" that {
      "exists with accesskey" in {
        val testObject = new TestObject
        getOrGenerateAwsCredential(testObject.userName).flatMap { testCred =>
          getUserSecretKeyAndIsNPA(testCred.accessKey).map(c => assert(c.contains((testObject.userName, testCred.secretKey, false))))
        }
      }

      "doesn't exist with accesskey" in {
        val testObject = new TestObject
        getUserSecretKeyAndIsNPA(testObject.cred.accessKey).map(c => assert(c.isEmpty))
      }
    }
  }
}
