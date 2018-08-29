package com.ing.wbaa.gargoyle.sts.service

import java.time.Instant
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.sts.config.GargoyleStsSettings
import com.ing.wbaa.gargoyle.sts.data.aws.{ AwsAccessKey, AwsCredentialWithToken, AwsSessionToken, AwsSessionTokenExpiration }
import com.ing.wbaa.gargoyle.sts.data.{ UserGroup, STSUserInfo, UserName }
import org.scalatest.{ Assertion, AsyncWordSpec, DiagrammedAssertions }

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.Duration
import scala.util.Random

class UserTokenDbServiceTest extends AsyncWordSpec with DiagrammedAssertions with UserTokenDbService {

  val testSystem: ActorSystem = ActorSystem.create("test-system")

  override implicit def executionContext: ExecutionContext = testSystem.dispatcher
  override protected[this] def stsSettings: GargoyleStsSettings = GargoyleStsSettings(testSystem)

  private class TestObject {
    val userName: UserName = UserName(Random.alphanumeric.take(32).mkString)
    val duration: Duration = Duration(2, TimeUnit.HOURS)
    val assumedUserGroup: Option[UserGroup] = Some(UserGroup("group"))
  }

  "User token service" should {
    "get credentials and token" that {
      def assertExpirationValid(expiration: AwsSessionTokenExpiration, durationFromNow: Duration, allowedOffsetMillis: Long = 1000) = {
        val diff = Instant.now().toEpochMilli + durationFromNow.toMillis - expiration.value.toEpochMilli
        assert(diff >= 0 && diff < allowedOffsetMillis)
      }

      "are new credentials and a new token with specified duration" in {
        val testObject = new TestObject
        getAwsCredentialWithToken(testObject.userName, Some(testObject.duration), None).map { c =>
          assertExpirationValid(c.session.expiration, testObject.duration)
        }
      }

      "have existing credentials and a new token" in {
        val testObject = new TestObject
        getAwsCredentialWithToken(testObject.userName, None, None).flatMap { firstReturn =>
          getAwsCredentialWithToken(testObject.userName, None, None).map { secondReturn =>
            assert(firstReturn.awsCredential == secondReturn.awsCredential)
            assert(firstReturn.session != secondReturn.session)
          }
        }
      }
    }

    "get user with it's assumed groups" that {
      "user doesn't exist" in {
        getUserWithAssumedGroups(AwsAccessKey("nonexistent"), AwsSessionToken("nonexistent")).map { u =>
          assert(u.isEmpty)
        }
      }

      "sessionToken doesn't have any assumed Group" in {
        val testObject = new TestObject
        getAwsCredentialWithToken(testObject.userName, None, None).flatMap { awsCredWithToken =>
          getUserWithAssumedGroups(awsCredWithToken.awsCredential.accessKey, awsCredWithToken.session.sessionToken).map { u =>
            assert(u.contains(STSUserInfo(testObject.userName, None)))
          }
        }
      }

      "sessionToken has assumed Group" in {
        val testObject = new TestObject
        getAwsCredentialWithToken(testObject.userName, None, testObject.assumedUserGroup).flatMap { awsCredWithToken =>
          getUserWithAssumedGroups(awsCredWithToken.awsCredential.accessKey, awsCredWithToken.session.sessionToken).map { u =>
            assert(u.contains(STSUserInfo(testObject.userName, testObject.assumedUserGroup)))
          }
        }
      }

      "sessionToken has assumed Group but sessiontoken doesn't exist" in {
        val testObject = new TestObject
        getAwsCredentialWithToken(testObject.userName, None, testObject.assumedUserGroup).flatMap { awsCredWithToken =>
          getUserWithAssumedGroups(awsCredWithToken.awsCredential.accessKey, AwsSessionToken("nonexistent")).map { u =>
            assert(u.contains(STSUserInfo(testObject.userName, None)))
          }
        }
      }
    }

    "check if token is active" that {

      def withAwsCredentialWithToken(testCode: AwsCredentialWithToken => Future[Assertion]): Future[Assertion] = {
        val testObject = new TestObject
        getAwsCredentialWithToken(testObject.userName, Some(testObject.duration), testObject.assumedUserGroup)
          .flatMap(testCode)
      }

      "has existing accesskey and token that are active" in withAwsCredentialWithToken { awsCredWithToken =>
        isTokenActive(awsCredWithToken.awsCredential.accessKey, awsCredWithToken.session.sessionToken)
          .map(b => assert(b))
      }

      "has existing accesskey but token is not active" in {
        val testObject = new TestObject
        getAwsCredentialWithToken(testObject.userName, Some(Duration(-1, TimeUnit.HOURS)), testObject.assumedUserGroup)
          .flatMap { awsCredWithToken =>
            isTokenActive(awsCredWithToken.awsCredential.accessKey, awsCredWithToken.session.sessionToken)
              .map(b => assert(!b))
          }
      }

      "has existing accesskey but no existing token" in withAwsCredentialWithToken { awsCredWithToken =>
        isTokenActive(awsCredWithToken.awsCredential.accessKey, AwsSessionToken("nonexistent"))
          .map(b => assert(!b))
      }

      "has no existing accesskey but token is active" in withAwsCredentialWithToken { awsCredWithToken =>
        isTokenActive(AwsAccessKey("nonexistent"), awsCredWithToken.session.sessionToken)
          .map(b => assert(!b))
      }

      "has no existing accesskey and token is not active" in {
        isTokenActive(AwsAccessKey("nonexistent"), AwsSessionToken("nonexistent"))
          .map(b => assert(!b))
      }
    }
  }
}
