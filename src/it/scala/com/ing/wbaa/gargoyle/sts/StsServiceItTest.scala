package com.ing.wbaa.gargoyle.sts

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.{Authority, Host}
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.model.{AWSSecurityTokenServiceException, AssumeRoleWithWebIdentityRequest, GetSessionTokenRequest}
import com.ing.wbaa.gargoyle.sts.config.GargoyleHttpSettings
import com.ing.wbaa.gargoyle.sts.oauth.OAuth2TokenVerifierImpl
import com.ing.wbaa.gargoyle.sts.service.{TokenServiceImpl, TokenXML, UserServiceImpl}
import org.scalatest._

import scala.concurrent.Future

class StsServiceItTest extends AsyncWordSpec with DiagrammedAssertions with AWSSTSClient {
  private[this] final implicit val testSystem: ActorSystem = ActorSystem.create("test-system")

  // Settings for tests:
  //  - Force a random port to listen on.
  //  - Explicitly bind to loopback, irrespective of any default value.
  private[this] val gargoyleHttpSettings = new GargoyleHttpSettings(testSystem.settings.config) {
    override val httpPort: Int = 0
    override val httpBind: String = "127.0.0.1"
  }

  // Fixture for starting and stopping a test proxy that tests can interact with.
  def withTestStsService(testCode: Authority => Assertion): Future[Assertion] = {
    val stsProxy = new GargoyleStsService
      with OAuth2TokenVerifierImpl
      with TokenServiceImpl
      with TokenXML
      with UserServiceImpl {
      override implicit def system: ActorSystem = testSystem

      override def httpSettings: GargoyleHttpSettings = gargoyleHttpSettings
    }
    stsProxy.startup
      .map { binding =>
        val authority = Authority(Host(binding.localAddress.getAddress), binding.localAddress.getPort)
        testCode(authority)
      }
      .andThen { case _ => stsProxy.shutdown() }
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

      assert(credentials.getAccessKeyId == "accesskey")
      assert(credentials.getSecretAccessKey == "secretkey")
      assert(credentials.getSessionToken == "okSessionToken")
      assert(credentials.getExpiration.getTime <= (System.currentTimeMillis() + 3600 * 1000))
    }

    "throw AWSSecurityTokenServiceException because invalid token" in withAwsClient { stsAwsClient =>
      assertThrows[AWSSecurityTokenServiceException] {
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

      assert(credentials.getAccessKeyId == "accesskey")
      assert(credentials.getSecretAccessKey == "secretkey")
      assert(credentials.getSessionToken == "okSessionToken")
      assert(credentials.getExpiration.getTime <= (System.currentTimeMillis() + 3600 * 1000))
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
