package com.ing.wbaa.rokku.sts.service.db.dao

import akka.actor.ActorSystem
import com.ing.wbaa.rokku.sts.config.{MariaDBSettings, StsSettings}
import com.ing.wbaa.rokku.sts.data.{AccountStatus, NPA, UserGroup, UserName}
import com.ing.wbaa.rokku.sts.data.aws.{AwsAccessKey, AwsCredential}
import com.ing.wbaa.rokku.sts.service.TokenGeneration
import com.ing.wbaa.rokku.sts.service.db.MariaDb
import org.scalatest.wordspec.AsyncWordSpec

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
        getAwsCredentialAndStatus(testObject.userName).map{ case (c, _) => assert(c.contains(testObject.cred)) }
        getUserSecretWithExtInfo(testObject.cred.accessKey).map(c => assert(c.contains((testObject.userName, testObject.cred.secretKey, NPA(false), AccountStatus(true), Set.empty[UserGroup]))))
      }

      "user is already present in the db" in {
        val testObject = new TestObject
        val newCred = generateAwsCredential

        insertAwsCredentials(testObject.userName, testObject.cred, isNpa = false).flatMap { inserted =>
          getAwsCredentialAndStatus(testObject.userName).map { case (c, _) =>
            assert(c.contains(testObject.cred))
            assert(inserted)
          }
        }

        insertAwsCredentials(testObject.userName, newCred, isNpa = false).flatMap(inserted =>
          getAwsCredentialAndStatus(testObject.userName).map { case (c, _) =>
            assert(c.contains(testObject.cred))
            assert(!inserted)
          }
        )
      }

      "have an already existing accesskey" in {
        val testObject = new TestObject

        insertAwsCredentials(testObject.userName, testObject.cred, isNpa = false).flatMap { inserted =>
          getAwsCredentialAndStatus(testObject.userName).map { case (c, _) =>
            assert(c.contains(testObject.cred))
            assert(inserted)
          }
        }

        val anotherTestObject = new TestObject
        insertAwsCredentials(anotherTestObject.userName, testObject.cred, isNpa = false).flatMap(inserted =>
          getAwsCredentialAndStatus(anotherTestObject.userName).map { case (c, _) =>
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
        getUserSecretWithExtInfo(testObject.cred.accessKey).map { o =>
          assert(o.isDefined)
          assert(o.get._1 == testObject.userName)
          assert(o.get._2 == testObject.cred.secretKey)
          assert(!o.get._3.value)
        }
      }

      "doesn't exist" in {
        getUserSecretWithExtInfo(AwsAccessKey("DOESNTEXIST")).map { o =>
          assert(o.isEmpty)
        }
      }
    }

    "get AwsCredential" that {
      "exists" in {
        val testObject = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, isNpa = false)
        getAwsCredentialAndStatus(testObject.userName).map { case (o, _) =>
          assert(o.isDefined)
          assert(o.get.accessKey == testObject.cred.accessKey)
          assert(o.get.secretKey == testObject.cred.secretKey)
        }
      }

      "doesn't exist" in {
        getAwsCredentialAndStatus(UserName("DOESNTEXIST")).map { case (o, _) =>
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
        getUserSecretWithExtInfo(testObject.cred.accessKey)
          .map(c => assert(c.contains((testObject.userName, testObject.cred.secretKey, NPA(false), AccountStatus(true), testObject.userGroups))))
        insertUserGroups(testObject.userName, Set(testObject.userGroups.head))
        getUserSecretWithExtInfo(testObject.cred.accessKey)
          .map(c => assert(c.contains((testObject.userName, testObject.cred.secretKey, NPA(false), AccountStatus(true), Set(testObject.userGroups.head)))))
        insertUserGroups(testObject.userName, Set.empty[UserGroup])
        getUserSecretWithExtInfo(testObject.cred.accessKey)
          .map(c => assert(c.contains((testObject.userName, testObject.cred.secretKey, NPA(false), AccountStatus(true), Set.empty[UserGroup]))))
      }
    }

    "disable or enable user" that {
      "exists in sts records" in {
        val testObject = new TestObject
        val newUser = testObject.userName
        val newCred = testObject.cred
        insertAwsCredentials(newUser, newCred, isNpa = false).map(r => assert(r))

        setAccountStatus(newUser, false)
        getAwsCredentialAndStatus(newUser).map { case (_, AccountStatus(isEnabled)) => assert(!isEnabled) }
        getUserSecretWithExtInfo(newCred.accessKey).map(c => assert(c.contains((newUser, newCred.secretKey, NPA(false), AccountStatus(false), Set.empty[UserGroup]))))

        setAccountStatus(newUser, true)
        getAwsCredentialAndStatus(newUser).map { case (_, AccountStatus(isEnabled)) => assert(isEnabled) }
        getUserSecretWithExtInfo(newCred.accessKey).map(c => assert(c.contains((newUser, newCred.secretKey, NPA(false), AccountStatus(true), Set.empty[UserGroup]))))

      }
    }

    "lists NPA user accounts" that {
      "exists in sts records" in {
        val testObject = new TestObject
        val newUser = testObject.userName
        val newCred = testObject.cred
        insertAwsCredentials(newUser, newCred, isNpa = true)
        getAllNPAAccounts.map(l=> assert(l.data.head.accountName == newUser.value))
      }
    }

  }
}
