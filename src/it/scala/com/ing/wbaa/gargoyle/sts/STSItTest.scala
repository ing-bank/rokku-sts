package com.ing.wbaa.gargoyle.sts

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.{Authority, Host}
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.model.{AWSSecurityTokenServiceException, AssumeRoleWithWebIdentityRequest, GetSessionTokenRequest}
import com.ing.wbaa.gargoyle.sts.config.GargoyleHttpSettings
import org.scalatest._

import scala.concurrent.Future

class STSItTest extends AsyncWordSpec with DiagrammedAssertions with AWSSTSClient {
  private[this] final implicit val system: ActorSystem = ActorSystem.create("test-system")

  // Settings for tests:
  //  - Force a random port to listen on.
  //  - Explicitly bind to loopback, irrespective of any default value.
  private[this] val gargoyleHttpSettings = new GargoyleHttpSettings(system.settings.config) {
    override val httpPort: Int = 0
    override val httpBind: String = "127.0.0.1"
  }

  // Fixture for starting and stopping a test proxy that tests can interact with.
  def withTestStsService(testCode: Authority => Assertion): Future[Assertion] = {
    val testProxy = StsService(gargoyleHttpSettings)
    testProxy.bind
      .map { binding =>
        val authority = Authority(Host(binding.localAddress.getAddress), binding.localAddress.getPort)
        testCode(authority)
      }
      .andThen { case _ => testProxy.shutdown() }
  }

  def withAwsClient(testCode: AWSSecurityTokenService => Assertion): Future[Assertion] =
    withTestStsService { authority =>
      val stsAwsClient: AWSSecurityTokenService = stsClient(authority)

      try {
        testCode(stsAwsClient)
      } finally {
        stsAwsClient.shutdown()
      }
    }

  "STS getSessionToken" should {
    "return credentials for valid token" in withAwsClient { stsAwsClient =>
      val credentials = stsAwsClient.getSessionToken(new GetSessionTokenRequest()
        .withTokenCode("validToken"))
        .getCredentials

      assert(credentials.getAccessKeyId.stripMargin.trim == "okAccessKey")
      assert(credentials.getSecretAccessKey.stripMargin.trim == "secretKey")
      assert(credentials.getSessionToken.stripMargin.trim == "okSessionToken")
      assert(credentials.getExpiration.getTime == 1562874929611L)
    }

    "throw AWSSecurityTokenServiceException because invalid token" in withAwsClient { stsAwsClient =>
      assertThrows[AWSSecurityTokenServiceException]{
        stsAwsClient.getSessionToken(new GetSessionTokenRequest()
          .withTokenCode("invalidToken"))
          .getCredentials
      }
    }
  }

  "STS assumeRoleWithWebIdentity" should {
    "return credentials for valid token" in withAwsClient { stsAwsClient =>
      val credentials = stsAwsClient.assumeRoleWithWebIdentity(new AssumeRoleWithWebIdentityRequest()
        .withRoleArn("arn")
        .withProviderId("provider")
        .withRoleSessionName("sessionName")
        .withWebIdentityToken("validToken"))
        .getCredentials

      assert(credentials.getAccessKeyId.stripMargin.trim == "okAccessKey")
      assert(credentials.getSecretAccessKey.stripMargin.trim == "secretKey")
      assert(credentials.getSessionToken.stripMargin.trim == "okSessionToken")
      assert(credentials.getExpiration.getTime == 1571958023000L)
    }

    "throw AWSSecurityTokenServiceException because invalid token" in withAwsClient { stsAwsClient =>
      assertThrows[AWSSecurityTokenServiceException] {
        stsAwsClient.assumeRoleWithWebIdentity(new AssumeRoleWithWebIdentityRequest()
          .withRoleArn("arn")
          .withProviderId("provider")
          .withRoleSessionName("sessionName")
          .withWebIdentityToken("invalidToken"))
          .getCredentials
      }
    }
  }
}
