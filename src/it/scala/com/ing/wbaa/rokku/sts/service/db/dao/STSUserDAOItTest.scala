package com.ing.wbaa.rokku.sts.service.db.dao

import akka.actor.ActorSystem
import com.ing.wbaa.rokku.sts.config.RedisSettings
import com.ing.wbaa.rokku.sts.config.StsSettings
import com.ing.wbaa.rokku.sts.data.AccountStatus
import com.ing.wbaa.rokku.sts.data.NPA
import com.ing.wbaa.rokku.sts.data.UserGroup
import com.ing.wbaa.rokku.sts.data.Username
import com.ing.wbaa.rokku.sts.data.UserAccount
import com.ing.wbaa.rokku.sts.data.aws.AwsAccessKey
import com.ing.wbaa.rokku.sts.data.aws.AwsCredential
import com.ing.wbaa.rokku.sts.service.TokenGeneration
import com.ing.wbaa.rokku.sts.service.db.Redis
import com.ing.wbaa.rokku.sts.service.db.RedisModel
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.matchers.should.Matchers._

import scala.jdk.CollectionConverters._
import scala.util.Random

class STSUserDAOItTest extends AsyncWordSpec
  with STSUserDAO
  with Redis
  with RedisModel
  with TokenGeneration
  with BeforeAndAfterAll {
  val system: ActorSystem = ActorSystem.create("test-system")

  override protected[this] def stsSettings: StsSettings = StsSettings(system)

  override protected[this] def redisSettings: RedisSettings = RedisSettings(system)

  override lazy val dbExecutionContext = executionContext

  override protected def beforeAll(): Unit = {
    initializeUserSearchIndex(redisPooledConnection)
    val keys = redisPooledConnection.keys(s"${UserKeyPrefix}*")
    keys.asScala.foreach(key => {
      redisPooledConnection.del(key)
    })
  }

  override protected def afterAll(): Unit = {
    val keys = redisPooledConnection.keys(s"${UserKeyPrefix}*")
    keys.asScala.foreach(key => {
      redisPooledConnection.del(key)
    })
  }

  private class TestObject {
    val cred: AwsCredential = generateAwsCredential
    val userName: Username = Username(Random.alphanumeric.take(32).mkString)
    val userGroups: Set[UserGroup] = Set(UserGroup(Random.alphanumeric.take(10).mkString), UserGroup(Random.alphanumeric.take(10).mkString))
  }

  "STS User DAO" should {
    "insert AwsCredentials with User" that {
      "are new in the db and have a unique accesskey" in {
        val testObject = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, isNPA = false).flatMap { r =>
          assert(r)
          getUserAccountByName(testObject.userName).map { case (UserAccount(_, c, _, _, _)) => assert(c.contains(testObject.cred)) }
          getUserAccountByAccessKey(testObject.cred.accessKey).map { u =>
            assert(u.isDefined)
            val userAccount = u.get
            assert(userAccount.username == testObject.userName)
            assert(userAccount.credentials.get.secretKey == testObject.cred.secretKey)
            assert(userAccount.status.isEnabled)
            assert(!userAccount.isNpa.value)
            assert(userAccount.groups.isEmpty)
          }
        }
      }

      "user is already present in the db" in {
        val testObject = new TestObject
        val newCred = generateAwsCredential

        insertAwsCredentials(testObject.userName, testObject.cred, isNPA = false).flatMap { inserted =>
          getUserAccountByName(testObject.userName).map { case (UserAccount(_, c, _, _, _)) =>
            assert(c.contains(testObject.cred))
            assert(inserted)
          }

          insertAwsCredentials(testObject.userName, newCred, isNPA = false).flatMap(inserted =>
            getUserAccountByName(testObject.userName).map { case (UserAccount(_, c, _, _, _)) =>
              assert(c.contains(testObject.cred))
              assert(!inserted)
            }
          )
        }

      }

      "have an already existing accesskey" in {
        val testObject = new TestObject

        insertAwsCredentials(testObject.userName, testObject.cred, isNPA = false).flatMap { inserted =>
          getUserAccountByName(testObject.userName).map { case (UserAccount(_, c, _, _, _)) =>
            assert(c.contains(testObject.cred))
            assert(inserted)
          }

          val anotherTestObject = new TestObject
          insertAwsCredentials(anotherTestObject.userName, testObject.cred, isNPA = false).flatMap(inserted =>
            getUserAccountByName(anotherTestObject.userName).map { case (UserAccount(_, c, _, _, _)) =>
              assert(c.isEmpty)
              assert(!inserted)
            }
          )
        }

      }
    }

    "get User, Secret and isNPA" that {
      "exists" in {
        val testObject = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, isNPA = false).flatMap { _ =>
          getUserAccountByAccessKey(testObject.cred.accessKey).map { u =>
            assert(u.isDefined)
            val userAccount = u.get

            assert(userAccount.username == testObject.userName)
            assert(userAccount.credentials.get.secretKey == testObject.cred.secretKey)
            assert(!userAccount.isNpa.value)
          }
        }
      }

      "doesn't exist" in {
        getUserAccountByAccessKey(AwsAccessKey("DOESNTEXIST")).map { o =>
          assert(o == None)
        }
      }
    }

    "get AwsCredential" that {
      "exists" in {
        val testObject = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, isNPA = false).flatMap { _ =>
          getUserAccountByName(testObject.userName).map { case (UserAccount(_, o, _, _, _)) =>
            assert(o.isDefined)
            assert(o.get.accessKey == testObject.cred.accessKey)
            assert(o.get.secretKey == testObject.cred.secretKey)
          }
        }
      }

      "doesn't exist" in {
        getUserAccountByName(Username("DOESNTEXIST")).map { case (UserAccount(_, o, _, _, _)) =>
          assert(o.isEmpty)
        }
      }
    }

    "verify duplicate entry" that {
      "username is different and access key is the same" in {
        val testObject = new TestObject
        val testObjectVerification = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, isNPA = false).flatMap { _ =>
          doesUsernameNotExistAndAccessKeyExist(testObjectVerification.userName, testObject.cred.accessKey)
            .map(r => assert(r))
        }
      }

      "username is different and access key is different" in {
        val testObject = new TestObject
        val testObjectVerification = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, isNPA = false).flatMap { _ =>
          doesUsernameNotExistAndAccessKeyExist(testObjectVerification.userName, testObjectVerification.cred.accessKey)
            .map(r => assert(!r))
        }
      }

      "username is same and access key is different" in {
        val testObject = new TestObject
        val testObjectVerification = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, isNPA = false).flatMap { _ =>
          doesUsernameNotExistAndAccessKeyExist(testObject.userName, testObjectVerification.cred.accessKey)
            .map(r => assert(!r))
        }
      }

      "username is same and access key is same" in {
        val testObject = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, isNPA = false).flatMap { _ =>
          doesUsernameNotExistAndAccessKeyExist(testObject.userName, testObject.cred.accessKey)
            .map(r => assert(!r))
        }

      }
    }

    "verify groups" that {
      "user has two groups then one and then zero" in {
        val testObject = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, isNPA = false).flatMap { _ =>
          setUserGroups(testObject.userName, testObject.userGroups).flatMap { _ =>
            {
              getUserAccountByAccessKey(testObject.cred.accessKey).map { u =>
                assert(u.isDefined)
                val userAccount = u.get
                assert(userAccount.username == testObject.userName)
                assert(userAccount.credentials.get.secretKey == testObject.cred.secretKey)
                assert(userAccount.status.isEnabled)
                assert(!userAccount.isNpa.value)
                assert(userAccount.groups == testObject.userGroups)

              }

              setUserGroups(testObject.userName, Set(testObject.userGroups.head)).flatMap { _ =>
                getUserAccountByAccessKey(testObject.cred.accessKey).map { u =>
                  assert(u.isDefined)
                  val userAccount = u.get
                  assert(userAccount.username == testObject.userName)
                  assert(userAccount.credentials.get.secretKey == testObject.cred.secretKey)
                  assert(userAccount.status.isEnabled)
                  assert(!userAccount.isNpa.value)
                  assert(userAccount.groups == Set(testObject.userGroups.head))
                }

                setUserGroups(testObject.userName, Set.empty[UserGroup]).flatMap { _ =>
                  getUserAccountByAccessKey(testObject.cred.accessKey).map { u =>
                    assert(u.isDefined)
                    val userAccount = u.get
                    assert(userAccount.username == testObject.userName)
                    assert(userAccount.credentials.get.secretKey == testObject.cred.secretKey)
                    assert(userAccount.status.isEnabled)
                    assert(!userAccount.isNpa.value)
                    assert(userAccount.groups.isEmpty)
                  }
                }
              }
            }
          }
        }
      }
    }

    "disable or enable user" that {
      "exists in sts records" in {
        val testObject = new TestObject
        val newUser = testObject.userName
        val newCred = testObject.cred
        insertAwsCredentials(newUser, newCred, isNPA = false).map(r => assert(r)).flatMap { _ =>

          setAccountStatus(newUser, false).flatMap { _ =>
            getUserAccountByName(newUser).map { case (UserAccount(_, _, AccountStatus(isEnabled), _, _)) => assert(!isEnabled) }
            getUserAccountByAccessKey(testObject.cred.accessKey).map { u =>
              assert(u.isDefined)
              val userAccount = u.get
              assert(userAccount.username == newUser)
              assert(userAccount.credentials.get.secretKey == newCred.secretKey)
              assert(!userAccount.status.isEnabled)
              assert(!userAccount.isNpa.value)
              assert(userAccount.groups.isEmpty)
            }

            setAccountStatus(newUser, true).flatMap { _ =>
              getUserAccountByName(newUser).map { case (UserAccount(_, _, AccountStatus(isEnabled), _, _)) => assert(isEnabled) }
              getUserAccountByAccessKey(testObject.cred.accessKey).map { u =>
                assert(u.isDefined)
                val userAccount = u.get
                assert(userAccount.username == newUser)
                assert(userAccount.credentials.get.secretKey == newCred.secretKey)
                assert(userAccount.status.isEnabled)
                assert(!userAccount.isNpa.value)
                assert(userAccount.groups.isEmpty)
              }
            }
          }
        }
      }
    }

    "lists NPA user accounts" that {
      "exists in sts records" in {
        val testObject = new TestObject
        val newUser = testObject.userName
        val newCred = testObject.cred
        insertAwsCredentials(newUser, newCred, isNPA = true).flatMap { _ =>
          getAllNPAAccounts.map(l => assert(l.data.head.accountName == newUser.value))
        }
      }
    }

  }
}
