package com.ing.wbaa.gargoyle.sts.service.db

import java.time.Instant

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.sts.config.GargoyleStsSettings
import com.ing.wbaa.gargoyle.sts.data.aws.{ AwsSession, AwsSessionToken, AwsSessionTokenExpiration }
import com.ing.wbaa.gargoyle.sts.data.{ UserAssumedGroup, UserName }
import com.ing.wbaa.gargoyle.sts.service.TokenGeneration
import org.scalatest.{ AsyncWordSpec, PrivateMethodTester }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Random

class TokenDbTest extends AsyncWordSpec with PrivateMethodTester {

  val testSystem: ActorSystem = ActorSystem.create("test-system")

  trait TokenDbTest extends TokenDb {
    override implicit def executionContext: ExecutionContext = testSystem.dispatcher

    override protected[this] val stsSettings: GargoyleStsSettings = GargoyleStsSettings(testSystem)

    override protected[this] def insertToken(awsSessionToken: AwsSessionToken, username: UserName, expirationDate: AwsSessionTokenExpiration, assumedGroup: Option[UserAssumedGroup]): Future[Boolean] =
      Future.successful(true)

    override protected[this] def getToken(awsSessionToken: AwsSessionToken): Future[Option[(UserName, AwsSessionTokenExpiration, UserAssumedGroup)]] =
      Future.successful(Some((UserName("u"), AwsSessionTokenExpiration(Instant.now()), UserAssumedGroup("group"))))
  }

  private class TestObject extends TokenGeneration {
    override protected[this] def stsSettings: GargoyleStsSettings = GargoyleStsSettings(testSystem)

    val testSession: AwsSession = generateAwsSession(None)
    val testUserName: UserName = UserName(Random.alphanumeric.take(32).mkString)
    val testAssumedUserGroup: UserAssumedGroup = UserAssumedGroup(Random.alphanumeric.take(32).mkString)
  }

  "TokenDbTest" should {

    "getAssumedGroupsForToken" that {
      "token is not present" in {
        val testObject = new TestObject
        new TokenDbTest {
          override protected[this] def getToken(awsSessionToken: AwsSessionToken): Future[Option[(UserName, AwsSessionTokenExpiration, UserAssumedGroup)]] =
            Future.successful(None)
        }.getAssumedGroupsForToken(testObject.testSession.sessionToken).map(t => assert(t.isEmpty))
      }

      "token is present but role cannot be assumed" in {
        val testObject = new TestObject
        val tokenDbTestMock = new TokenDbTest {
          override protected[this] def getToken(awsSessionToken: AwsSessionToken): Future[Option[(UserName, AwsSessionTokenExpiration, UserAssumedGroup)]] =
            Future.successful(None)
        }

        tokenDbTestMock.getNewAwsSession(testObject.testUserName, None, None).flatMap { testSession =>
          tokenDbTestMock.getAssumedGroupsForToken(testSession.sessionToken).map(t => assert(t.isEmpty))
        }
      }

      "token is present and role can be assumed" in {
        val testObject = new TestObject
        val tokenDbTestMock = new TokenDbTest {
          override protected[this] def getToken(awsSessionToken: AwsSessionToken): Future[Option[(UserName, AwsSessionTokenExpiration, UserAssumedGroup)]] =
            Future.successful(None)
        }

        val tdt = new TokenDbTest {
          override protected[this] def getToken(awsSessionToken: AwsSessionToken): Future[Option[(UserName, AwsSessionTokenExpiration, UserAssumedGroup)]] =
            Future.successful(Some((testObject.testUserName, testObject.testSession.expiration, testObject.testAssumedUserGroup)))
        }

        tokenDbTestMock.getNewAwsSession(testObject.testUserName, None, Some(testObject.testAssumedUserGroup)).flatMap { testSession =>
          tdt.getAssumedGroupsForToken(testSession.sessionToken).map(t => assert(t == Some(testObject.testAssumedUserGroup)))
        }
      }
    }

    "getUserNameAndTokenExpiration" that {
      "token is not present" in {
        val testObject = new TestObject
        new TokenDbTest {
          override protected[this] def getToken(awsSessionToken: AwsSessionToken): Future[Option[(UserName, AwsSessionTokenExpiration, UserAssumedGroup)]] =
            Future.successful(None)
        }.getTokenExpiration(testObject.testSession.sessionToken).map(t => assert(t.isEmpty))
      }

      "token is present" in {
        val testObject = new TestObject
        val tokenDbTestMock = new TokenDbTest {
          override protected[this] def getToken(awsSessionToken: AwsSessionToken): Future[Option[(UserName, AwsSessionTokenExpiration, UserAssumedGroup)]] =
            Future.successful(None)
        }

        tokenDbTestMock.getNewAwsSession(testObject.testUserName, None, None).flatMap { testSession =>
          new TokenDbTest {}.getTokenExpiration(testSession.sessionToken).map(t => assert(!t.isEmpty))
        }
      }
    }

  }
}
