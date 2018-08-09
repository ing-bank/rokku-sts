package com.ing.wbaa.gargoyle.sts

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.{Authority, Host}
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.model.{AWSSecurityTokenServiceException, AssumeRoleWithWebIdentityRequest, GetSessionTokenRequest}
import com.ing.wbaa.gargoyle.sts.config.{GargoyleHttpSettings, GargoyleKeycloakSettings}
import com.ing.wbaa.gargoyle.sts.helper.{KeycloackToken, OAuth2TokenRequest}
import com.ing.wbaa.gargoyle.sts.oauth.KeycloakTokenVerifier
import com.ing.wbaa.gargoyle.sts.service.{TokenService, TokenXML, UserService}
import org.scalatest._
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.Future
import scala.concurrent.duration._

class StsServiceItTest extends AsyncWordSpec with DiagrammedAssertions
  with ScalaFutures
  with AWSSTSClient {
  final implicit val testSystem: ActorSystem = ActorSystem.create("test-system")
  val timeout = Timeout(10.second)

  private val validCredentials = Map("grant_type" -> "password", "username" -> "userone", "password" -> "password", "client_id" -> "sts-gargoyle")
  private val invalidCredentials = validCredentials + ("password" -> "xxx")

  private[this] val gargoyleHttpSettings = new GargoyleHttpSettings(testSystem.settings.config) {
    override val httpPort: Int = 0
    override val httpBind: String = "127.0.0.1"
  }

  private val gargoyleKeycloakSettings = new GargoyleKeycloakSettings(testSystem.settings.config) {
    override val realmPublicKeyId: String = "FJ86GcF3jTbNLOco4NvZkUCIUmfYCqoqtOQeMfbhNlE"
  }

  def withOAuth2TokenRequest(formData: Map[String, String])(testCode: Future[KeycloackToken] => Assertion): Assertion = {
    testCode(new OAuth2TokenRequest() {
      override protected implicit def system: ActorSystem = testSystem

      override protected[this] def keycloakSettings: GargoyleKeycloakSettings = gargoyleKeycloakSettings
    }.keycloackToken(formData))
  }

  // Fixture for starting and stopping a test proxy that tests can interact with.
  def withTestStsService(testCode: Authority => Assertion): Future[Assertion] = {
    val sts = new GargoyleStsService
      with KeycloakTokenVerifier
      with TokenService
      with TokenXML
      with UserService {
      override implicit def system: ActorSystem = testSystem

      override def httpSettings: GargoyleHttpSettings = gargoyleHttpSettings

      override protected[this] def keycloakSettings: GargoyleKeycloakSettings = gargoyleKeycloakSettings
    }
    sts.startup.map { binding =>
      try testCode(Authority(Host(binding.localAddress.getAddress), binding.localAddress.getPort))
      finally sts.shutdown()
    }
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
      withOAuth2TokenRequest(validCredentials) { keycloakToken =>
        val credentials = stsAwsClient.getSessionToken(new GetSessionTokenRequest()
          .withTokenCode(keycloakToken.futureValue(timeout).access_token))
          .getCredentials

        assert(credentials.getAccessKeyId == "accesskey")
        assert(credentials.getSecretAccessKey == "secretkey")
        assert(credentials.getSessionToken == "okSessionToken")
        assert(credentials.getExpiration.getTime <= (System.currentTimeMillis() + 3600 * 1000))
      }
    }

    "throw AWSSecurityTokenServiceException because invalid token" in withAwsClient { stsAwsClient =>
      withOAuth2TokenRequest(invalidCredentials) { keycloakToken =>
        assertThrows[AWSSecurityTokenServiceException] {
          val credentials = stsAwsClient.getSessionToken(new GetSessionTokenRequest()
            .withTokenCode(keycloakToken.futureValue(timeout).access_token))
            .getCredentials

          assert(credentials.getAccessKeyId == "accesskey")
        }
      }
    }
  }

  "STS assumeRoleWithWebIdentity" should {
    "return credentials for valid token" in withAwsClient { stsAwsClient =>
      withOAuth2TokenRequest(validCredentials) { keycloakToken =>
        val credentials = stsAwsClient.assumeRoleWithWebIdentity(new AssumeRoleWithWebIdentityRequest()
          .withRoleArn("arn")
          .withProviderId("provider")
          .withRoleSessionName("sessionName")
          .withWebIdentityToken(keycloakToken.futureValue(timeout).access_token))
          .getCredentials

        assert(credentials.getAccessKeyId == "accesskey")
        assert(credentials.getSecretAccessKey == "secretkey")
        assert(credentials.getSessionToken == "okSessionToken")
        assert(credentials.getExpiration.getTime <= (System.currentTimeMillis() + 3600 * 1000))
      }
    }

    "throw AWSSecurityTokenServiceException because invalid token" in withAwsClient { stsAwsClient =>
      withOAuth2TokenRequest(invalidCredentials) { keycloakToken =>
        assertThrows[AWSSecurityTokenServiceException] {
          stsAwsClient.assumeRoleWithWebIdentity(new AssumeRoleWithWebIdentityRequest()
            .withRoleArn("arn")
            .withProviderId("provider")
            .withRoleSessionName("sessionName")
            .withWebIdentityToken(keycloakToken.futureValue(timeout).access_token))
            .getCredentials
        }
      }
    }
  }
}
