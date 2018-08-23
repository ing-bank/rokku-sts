package com.ing.wbaa.gargoyle.sts

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.{Authority, Host}
import akka.stream.ActorMaterializer
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.model.{AWSSecurityTokenServiceException, AssumeRoleWithWebIdentityRequest, GetSessionTokenRequest}
import com.ing.wbaa.gargoyle.sts.config.{GargoyleHttpSettings, GargoyleKeycloakSettings}
import com.ing.wbaa.gargoyle.sts.helper.{KeycloackToken, OAuth2TokenRequest}
import com.ing.wbaa.gargoyle.sts.keycloak.KeycloakTokenVerifier
import com.ing.wbaa.gargoyle.sts.service.TokenUserStore
import org.scalatest._

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContextExecutor, Future}

class StsServiceItTest extends AsyncWordSpec with DiagrammedAssertions
  with AWSSTSClient with OAuth2TokenRequest {
  override implicit val testSystem: ActorSystem = ActorSystem.create("test-system")
  override implicit val materializer: ActorMaterializer = ActorMaterializer()(testSystem)
  override implicit val exContext: ExecutionContextExecutor = testSystem.dispatcher

  private val validCredentials = Map("grant_type" -> "password", "username" -> "userone", "password" -> "password", "client_id" -> "sts-gargoyle")
  private val invalidCredentials = validCredentials + ("password" -> "xxx")

  private[this] val gargoyleHttpSettings = new GargoyleHttpSettings(testSystem.settings.config) {
    override val httpPort: Int = 0
    override val httpBind: String = "127.0.0.1"
  }

  override val gargoyleKeycloakSettings = new GargoyleKeycloakSettings(testSystem.settings.config) {
    override val realmPublicKeyId: String = "FJ86GcF3jTbNLOco4NvZkUCIUmfYCqoqtOQeMfbhNlE"
  }

  def withOAuth2TokenRequest(formData: Map[String, String])(testCode: KeycloackToken => Assertion): Future[Assertion] = {
    keycloackToken(formData).map(testCode(_))
  }

  // Fixture for starting and stopping a test proxy that tests can interact with.
  def withTestStsService(testCode: Authority => Future[Assertion]): Future[Assertion] = {
    val sts = new GargoyleStsService
      with KeycloakTokenVerifier
      with TokenUserStore {
      override implicit def system: ActorSystem = testSystem

      override def httpSettings: GargoyleHttpSettings = gargoyleHttpSettings

      override protected[this] def keycloakSettings: GargoyleKeycloakSettings = gargoyleKeycloakSettings
    }
    sts.startup.flatMap { binding =>
        testCode(Authority(Host(binding.localAddress.getAddress), binding.localAddress.getPort))
          .andThen{case _ => sts.shutdown()}
    }
  }

  def withAwsClient(testCode: AWSSecurityTokenService => Future[Assertion]): Future[Assertion] =
    withTestStsService { authority =>
      val stsAwsClient: AWSSecurityTokenService = stsClient(authority)
        testCode(stsAwsClient).andThen{case _ => stsAwsClient.shutdown()}
    }

  "STS getSessionToken" should {

    "return credentials for valid token" in withAwsClient { stsAwsClient =>
      withOAuth2TokenRequest(validCredentials) { keycloakToken =>
        val credentials = stsAwsClient.getSessionToken(new GetSessionTokenRequest()
          .withTokenCode(keycloakToken.access_token))
          .getCredentials

        assert(credentials.getAccessKeyId.length == 32)
        assert(credentials.getSecretAccessKey.length == 32)
        assert(credentials.getSessionToken.length == 32)
        assert(credentials.getExpiration.getTime <= (System.currentTimeMillis() + Duration(8, TimeUnit.HOURS).toMillis))
      }
    }

    "throw AWSSecurityTokenServiceException because invalid token" in withAwsClient { stsAwsClient =>
      withOAuth2TokenRequest(invalidCredentials) { keycloakToken =>
        assertThrows[AWSSecurityTokenServiceException] {
          stsAwsClient.getSessionToken(new GetSessionTokenRequest()
            .withTokenCode(keycloakToken.access_token))
            .getCredentials
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
          .withWebIdentityToken(keycloakToken.access_token))
          .getCredentials

        assert(credentials.getAccessKeyId.length == 32)
        assert(credentials.getSecretAccessKey.length == 32)
        assert(credentials.getSessionToken.length == 32)
        assert(credentials.getExpiration.getTime <= (System.currentTimeMillis() + Duration(8, TimeUnit.HOURS).toMillis))
      }
    }

    "throw AWSSecurityTokenServiceException because invalid token" in withAwsClient { stsAwsClient =>
      withOAuth2TokenRequest(invalidCredentials) { keycloakToken =>
        assertThrows[AWSSecurityTokenServiceException] {
          stsAwsClient.assumeRoleWithWebIdentity(new AssumeRoleWithWebIdentityRequest()
            .withRoleArn("arn")
            .withProviderId("provider")
            .withRoleSessionName("sessionName")
            .withWebIdentityToken(keycloakToken.access_token))
            .getCredentials
        }
      }
    }
  }
}
