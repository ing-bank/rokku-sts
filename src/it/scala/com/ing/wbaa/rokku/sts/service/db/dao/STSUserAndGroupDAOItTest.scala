package com.ing.wbaa.rokku.sts.service.db.dao

import akka.actor.ActorSystem
import com.ing.wbaa.rokku.sts.config.{MariaDBSettings, StsSettings}
import com.ing.wbaa.rokku.sts.data.{UserGroup, UserName}
import com.ing.wbaa.rokku.sts.data.aws.{AwsAccessKey, AwsCredential}
import com.ing.wbaa.rokku.sts.service.TokenGeneration
import com.ing.wbaa.rokku.sts.service.db.MariaDb
import org.scalatest.AsyncWordSpec

import scala.util.Random

class STSUserAndGroupDAOItTest extends AsyncWordSpec with STSUserAndGroupDAO with MariaDb with TokenGeneration {
  val system: ActorSystem = ActorSystem.create("test-system")

  override protected[this] def stsSettings: StsSettings = StsSettings(system)

  override protected[this] def mariaDBSettings: MariaDBSettings = MariaDBSettings(system)

  override lazy val dbExecutionContext = executionContext

  private class TestObject {
    val cred: AwsCredential = generateAwsCredential
    val userName: UserName = UserName(Random.alphanumeric.take(32).mkString)
    val userGroups: Set[UserGroup] = Set(UserGroup(Random.alphanumeric.take(10).mkString), UserGroup(Random.alphanumeric.take(10).mkString))
  }

  "STS User DAO" should {
    "insert AwsCredentials with User" that {
      "are new in the db and have a unique accesskey" in {
        val testObject = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, isNpa = false).map(r => assert(r))
        getAwsCredential(testObject.userName).map(c => assert(c.contains(testObject.cred)))
        getUserSecretKeyAndIsNPA(testObject.cred.accessKey).map(c => assert(c.contains((testObject.userName, testObject.cred.secretKey, false, Set.empty[UserGroup]))))
      }

      "user is already present in the db" in {
        val testObject = new TestObject
        val newCred = generateAwsCredential

        insertAwsCredentials(testObject.userName, testObject.cred, isNpa = false).flatMap { inserted =>
          getAwsCredential(testObject.userName).map { c =>
            assert(c.contains(testObject.cred))
            assert(inserted)
          }
        }

        insertAwsCredentials(testObject.userName, newCred, isNpa = false).flatMap(inserted =>
          getAwsCredential(testObject.userName).map { c =>
            assert(c.contains(testObject.cred))
            assert(!inserted)
          }
        )
      }

      "have an already existing accesskey" in {
        val testObject = new TestObject

        insertAwsCredentials(testObject.userName, testObject.cred, isNpa = false).flatMap { inserted =>
          getAwsCredential(testObject.userName).map { c =>
            assert(c.contains(testObject.cred))
            assert(inserted)
          }
        }

        val anotherTestObject = new TestObject
        insertAwsCredentials(anotherTestObject.userName, testObject.cred, isNpa = false).flatMap(inserted =>
          getAwsCredential(anotherTestObject.userName).map { c =>
            assert(c.isEmpty)
            assert(!inserted)
          }
        )
      }
    }

    "get User, Secret and isNPA" that {
      "exists" in {
        val testObject = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, isNpa = false)
        getUserSecretKeyAndIsNPA(testObject.cred.accessKey).map { o =>
          assert(o.isDefined)
          assert(o.get._1 == testObject.userName)
          assert(o.get._2 == testObject.cred.secretKey)
          assert(!o.get._3)
        }
      }

      "doesn't exist" in {
        getUserSecretKeyAndIsNPA(AwsAccessKey("DOESNTEXIST")).map { o =>
          assert(o.isEmpty)
        }
      }
    }

    "get AwsCredential" that {
      "exists" in {
        val testObject = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, isNpa = false)
        getAwsCredential(testObject.userName).map { o =>
          assert(o.isDefined)
          assert(o.get.accessKey == testObject.cred.accessKey)
          assert(o.get.secretKey == testObject.cred.secretKey)
        }
      }

      "doesn't exist" in {
        getAwsCredential(UserName("DOESNTEXIST")).map { o =>
          assert(o.isEmpty)
        }
      }
    }

    "verify duplicate entry" that {
      "username is different and access key is the same" in {
        val testObject = new TestObject
        val testObjectVerification = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, isNpa = false)

        doesUsernameNotExistAndAccessKeyExist(testObjectVerification.userName, testObject.cred.accessKey).map(r => assert(r))
      }

      "username is different and access key is different" in {
        val testObject = new TestObject
        val testObjectVerification = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, isNpa = false)

        doesUsernameNotExistAndAccessKeyExist(testObjectVerification.userName, testObjectVerification.cred.accessKey)
          .map(r => assert(!r))
      }

      "username is same and access key is different" in {
        val testObject = new TestObject
        val testObjectVerification = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, isNpa = false)

        doesUsernameNotExistAndAccessKeyExist(testObject.userName, testObjectVerification.cred.accessKey)
          .map(r => assert(!r))
      }

      "username is same and access key is same" in {
        val testObject = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, isNpa = false)

        doesUsernameNotExistAndAccessKeyExist(testObject.userName, testObject.cred.accessKey)
          .map(r => assert(!r))
      }
    }

    "verify groups" that {
      "user has two groups then one and then zero" in {
        val testObject = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, isNpa = false)
        insertUserGroups(testObject.userName, testObject.userGroups)
        getUserSecretKeyAndIsNPA(testObject.cred.accessKey)
          .map(c => assert(c.contains((testObject.userName, testObject.cred.secretKey, false, testObject.userGroups))))
        insertUserGroups(testObject.userName, Set(testObject.userGroups.head))
        getUserSecretKeyAndIsNPA(testObject.cred.accessKey)
          .map(c => assert(c.contains((testObject.userName, testObject.cred.secretKey, false, Set(testObject.userGroups.head)))))
        insertUserGroups(testObject.userName, Set.empty[UserGroup])
        getUserSecretKeyAndIsNPA(testObject.cred.accessKey)
          .map(c => assert(c.contains((testObject.userName, testObject.cred.secretKey, false, Set.empty[UserGroup]))))
      }
    }

  }
}
