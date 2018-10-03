package com.ing.wbaa.gargoyle.sts.service

import java.time.Instant
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.sts.config.GargoyleStsSettings
import com.ing.wbaa.gargoyle.sts.data.aws._
import com.ing.wbaa.gargoyle.sts.data.{ UserAssumedGroup, UserName }
import org.scalatest.{ AsyncWordSpec, DiagrammedAssertions }

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.Duration
import scala.util.Random

class UserTokenDbServiceTest extends AsyncWordSpec with DiagrammedAssertions {

  trait UserTokenDbServiceTest extends UserTokenDbService {
    val testSystem: ActorSystem = ActorSystem.create("test-system")
    override implicit def executionContext: ExecutionContext = testSystem.dispatcher
    override protected[this] def stsSettings: GargoyleStsSettings = GargoyleStsSettings(testSystem)

    override protected[this] def getAwsCredential(userName: UserName): Future[Option[AwsCredential]] =
      Future.successful(Some(AwsCredential(AwsAccessKey("a"), AwsSecretKey("s"))))

    override protected[this] def getUserSecretKeyAndIsNPA(awsAccessKey: AwsAccessKey): Future[Option[(UserName, AwsSecretKey, Boolean)]] =
      Future.successful(Some((UserName("u"), AwsSecretKey("s"), false)))

    override protected[this] def insertAwsCredentials(username: UserName, awsCredential: AwsCredential, isNpa: Boolean): Future[Boolean] =
      Future.successful(true)
  }

  private class TestObject {
    val userName: UserName = UserName(Random.alphanumeric.take(32).mkString)
    val duration: Duration = Duration(2, TimeUnit.HOURS)
    val assumedUserGroup: Option[UserAssumedGroup] = Some(UserAssumedGroup("group"))
  }

  "User token service" should {
    "get credentials and token" that {
      def assertExpirationValid(expiration: AwsSessionTokenExpiration, durationFromNow: Duration, allowedOffsetMillis: Long = 1000) = {
        val diff = Instant.now().toEpochMilli + durationFromNow.toMillis - expiration.value.toEpochMilli
        assert(diff >= 0 && diff < allowedOffsetMillis)
      }

      "are new credentials and a new token with specified duration" in {
        val testObject = new TestObject
        new UserTokenDbServiceTest {}.getAwsCredentialWithToken(testObject.userName, Some(testObject.duration), None).map { c =>
          assertExpirationValid(c.session.expiration, testObject.duration)
        }
      }

      "have existing credentials and a new token" in {
        val testObject = new TestObject
        val utds = new UserTokenDbServiceTest {}
        utds.getAwsCredentialWithToken(testObject.userName, None, None).flatMap { firstReturn =>
          utds.getAwsCredentialWithToken(testObject.userName, None, None).map { secondReturn =>
            assert(firstReturn.awsCredential == secondReturn.awsCredential)
            assert(firstReturn.session != secondReturn.session)
          }
        }
      }
    }

    "check if credentials are active" that {
      "has valid accesskey and sessiontoken is active with assumed groups" in {
        val t = new TestObject
        val utds = new UserTokenDbServiceTest {}
        utds.getAwsCredentialWithToken(t.userName, Some(t.duration), t.assumedUserGroup).flatMap { awsCredWithToken =>
          utds.isCredentialActive(awsCredWithToken.awsCredential.accessKey, Some(awsCredWithToken.session.sessionToken)).map { u =>
            assert(u.map(_.userName).contains(UserName("u")))
            assert(u.map(_.assumedGroup).contains(t.assumedUserGroup))
            assert(u.map(_.awsAccessKey).contains(AwsAccessKey("a")))
            assert(u.map(_.awsSecretKey).contains(AwsSecretKey("s")))
          }
        }
      }

      "has valid accesskey and sessiontoken is active without assumed groups" in {
        val t = new TestObject
        val utds = new UserTokenDbServiceTest {}
        utds.getAwsCredentialWithToken(t.userName, Some(t.duration), None).flatMap { awsCredWithToken =>
          utds.isCredentialActive(awsCredWithToken.awsCredential.accessKey, Some(awsCredWithToken.session.sessionToken)).map { u =>
            assert(u.map(_.userName).contains(UserName("u")))
            assert(u.exists(_.assumedGroup.isEmpty))
            assert(u.map(_.awsAccessKey).contains(AwsAccessKey("a")))
            assert(u.map(_.awsSecretKey).contains(AwsSecretKey("s")))
          }
        }
      }

      "has valid accesskey and sessiontoken is inactive" in {
        val t = new TestObject
        val utds = new UserTokenDbServiceTest {}
        utds.getAwsCredentialWithToken(t.userName, Some(Duration(-1, TimeUnit.HOURS)), t.assumedUserGroup)
          .flatMap { awsCredWithToken =>
            utds.isCredentialActive(awsCredWithToken.awsCredential.accessKey, Some(awsCredWithToken.session.sessionToken))
              .map(b => assert(b.isEmpty))
          }
      }

      "has invalid accesskey" in {
        val utds = new UserTokenDbServiceTest {}
        utds.isCredentialActive(AwsAccessKey("nonexistent"), None).map(a => assert(a.isEmpty))
      }

      "has valid accesskey and non existent sessiontoken" in {
        val t = new TestObject
        val utds = new UserTokenDbServiceTest {}
        utds.getAwsCredentialWithToken(t.userName, Some(t.duration), t.assumedUserGroup).flatMap { awsCredWithToken =>
          utds.isCredentialActive(awsCredWithToken.awsCredential.accessKey, Some(AwsSessionToken("nonexistent"))).map(
            a => assert(a.isEmpty)
          )
        }
      }

      "has valid accesskey, no sessiontoken and is not an NPA" in {
        val t = new TestObject
        val utds = new UserTokenDbServiceTest {}
        utds.getAwsCredentialWithToken(t.userName, Some(t.duration), t.assumedUserGroup).flatMap { awsCredWithToken =>

          utds.isCredentialActive(awsCredWithToken.awsCredential.accessKey, None).map(a => assert(a.isEmpty))
        }
      }
    }
  }
}
