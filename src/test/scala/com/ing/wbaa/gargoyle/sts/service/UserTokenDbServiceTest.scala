package com.ing.wbaa.gargoyle.sts.service

import java.time.Instant
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.sts.config.GargoyleStsSettings
import com.ing.wbaa.gargoyle.sts.data.aws._
import com.ing.wbaa.gargoyle.sts.data.{ UserGroup, UserName }
import org.scalatest.{ AsyncWordSpec, DiagrammedAssertions }

import scala.concurrent.ExecutionContext
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

    "check if credentials are active" that {
      "has valid accesskey and sessiontoken is active with assumed groups" in {
        val t = new TestObject
        getAwsCredentialWithToken(t.userName, Some(t.duration), t.assumedUserGroup).flatMap { awsCredWithToken =>
          isCredentialActive(awsCredWithToken.awsCredential.accessKey, Some(awsCredWithToken.session.sessionToken)).map { u =>
            assert(u.map(_.userName).contains(t.userName))
            assert(u.map(_.assumedGroup).contains(t.assumedUserGroup))
            assert(u.map(_.awsAccessKey).exists(_.value.length == 32))
            assert(u.map(_.awsSecretKey).exists(_.value.length == 32))
          }
        }
      }

      "has valid accesskey and sessiontoken is active without assumed groups" in {
        val t = new TestObject
        getAwsCredentialWithToken(t.userName, Some(t.duration), None).flatMap { awsCredWithToken =>
          isCredentialActive(awsCredWithToken.awsCredential.accessKey, Some(awsCredWithToken.session.sessionToken)).map { u =>
            assert(u.map(_.userName).contains(t.userName))
            assert(u.exists(_.assumedGroup.isEmpty))
            assert(u.map(_.awsAccessKey).exists(_.value.length == 32))
            assert(u.map(_.awsSecretKey).exists(_.value.length == 32))
          }
        }
      }

      "has valid accesskey and sessiontoken is inactive" in {
        val t = new TestObject
        getAwsCredentialWithToken(t.userName, Some(Duration(-1, TimeUnit.HOURS)), t.assumedUserGroup)
          .flatMap { awsCredWithToken =>
            isCredentialActive(awsCredWithToken.awsCredential.accessKey, Some(awsCredWithToken.session.sessionToken))
              .map(b => assert(b.isEmpty))
          }
      }

      "has invalid accesskey" in {
        isCredentialActive(AwsAccessKey("nonexistent"), None).map(a => assert(a.isEmpty))
      }

      "has valid accesskey and non existent sessiontoken" in {
        val t = new TestObject
        getAwsCredentialWithToken(t.userName, Some(t.duration), t.assumedUserGroup).flatMap { awsCredWithToken =>
          isCredentialActive(awsCredWithToken.awsCredential.accessKey, Some(AwsSessionToken("nonexistent"))).map(
            a => assert(a.isEmpty)
          )
        }
      }

      "has valid accesskey, no sessiontoken and is an NPA" in {
        isCredentialActive(AwsAccessKey("ranger6QeHX2dLdyGYIAK4iE4R7kSUue"), None).map { u =>
          assert(u.map(_.userName).contains(UserName("ranger")))
          assert(u.exists(_.assumedGroup.isEmpty))
          assert(u.map(_.awsAccessKey).contains(AwsAccessKey("ranger6QeHX2dLdyGYIAK4iE4R7kSUue")))
          assert(u.map(_.awsSecretKey).contains(AwsSecretKey("3Zl8cBAkykUQLOYGjmI38Txi02TFdEAv")))
        }
      }

      "has valid accesskey, no sessiontoken and is not an NPA" in {
        val t = new TestObject
        getAwsCredentialWithToken(t.userName, Some(t.duration), t.assumedUserGroup).flatMap { awsCredWithToken =>

          isCredentialActive(awsCredWithToken.awsCredential.accessKey, None).map(a => assert(a.isEmpty))
        }
      }
    }
  }
}
