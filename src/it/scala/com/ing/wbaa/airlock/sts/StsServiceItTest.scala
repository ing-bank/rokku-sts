package com.ing.wbaa.airlock.sts

import java.time.Instant

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.{Authority, Host}
import akka.stream.ActorMaterializer
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.model.{AWSSecurityTokenServiceException, GetSessionTokenRequest}
import com.ing.wbaa.airlock.sts.config.{HttpSettings, KeycloakSettings, MariaDBSettings, StsSettings}
import com.ing.wbaa.airlock.sts.data.UserName
import com.ing.wbaa.airlock.sts.data.aws._
import com.ing.wbaa.airlock.sts.helper.{KeycloackToken, OAuth2TokenRequest}
import com.ing.wbaa.airlock.sts.keycloak.KeycloakTokenVerifier
import com.ing.wbaa.airlock.sts.service.UserTokenDbService
import com.ing.wbaa.airlock.sts.service.db.MariaDb
import com.ing.wbaa.airlock.sts.service.db.dao.STSUserAndGroupDAO
import org.scalatest._

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Random

class StsServiceItTest extends AsyncWordSpec with DiagrammedAssertions
  with AWSSTSClient with OAuth2TokenRequest {
  override implicit val testSystem: ActorSystem = ActorSystem.create("test-system")
  override implicit val materializer: ActorMaterializer = ActorMaterializer()(testSystem)
  override implicit val exContext: ExecutionContextExecutor = testSystem.dispatcher

  private val validCredentials = Map("grant_type" -> "password", "username" -> "userone", "password" -> "password", "client_id" -> "sts-airlock")
  private val invalidCredentials = validCredentials + ("password" -> "xxx")

  private[this] val airlockHttpSettings: HttpSettings = new HttpSettings(testSystem.settings.config) {
    override val httpPort: Int = 0
    override val httpBind: String = "127.0.0.1"
  }

  override val keycloakSettings: KeycloakSettings = new KeycloakSettings(testSystem.settings.config) {
    override val realmPublicKeyId: String = "FJ86GcF3jTbNLOco4NvZkUCIUmfYCqoqtOQeMfbhNlE"
  }

  def withOAuth2TokenRequest(formData: Map[String, String])(testCode: KeycloackToken => Assertion): Future[Assertion] = {
    keycloackToken(formData).map(testCode(_))
  }

  // Fixture for starting and stopping a test proxy that tests can interact with.
  def withTestStsService(testCode: Authority => Future[Assertion]): Future[Assertion] = {
    val sts = new AirlockStsService
      with KeycloakTokenVerifier
      with UserTokenDbService
      with STSUserAndGroupDAO
      with MariaDb {
      override implicit def system: ActorSystem = testSystem

      override protected[this] def httpSettings: HttpSettings = airlockHttpSettings

      override protected[this] def keycloakSettings: KeycloakSettings = new KeycloakSettings(testSystem.settings.config) {
        override val realmPublicKeyId: String = "FJ86GcF3jTbNLOco4NvZkUCIUmfYCqoqtOQeMfbhNlE"
      }

      override protected[this] def stsSettings: StsSettings = StsSettings(testSystem)

      override protected[this] def mariaDBSettings: MariaDBSettings = new MariaDBSettings(testSystem.settings.config)

      override protected[this] def insertToken(awsSessionToken: AwsSessionToken, username: UserName, expirationDate: AwsSessionTokenExpiration): Future[Boolean] =
        Future.successful(true)

      override protected[this] def getToken(awsSessionToken: AwsSessionToken): Future[Option[(UserName, AwsSessionTokenExpiration)]] =
        Future.successful(None)

      override def generateAwsSession(duration: Option[Duration]): AwsSession = AwsSession(
        AwsSessionToken("sessiontoken" + Random.alphanumeric.take(32).mkString),
        AwsSessionTokenExpiration(Instant.now().plusSeconds(20))
      )

    }
    sts.startup.flatMap { binding =>
      testCode(Authority(Host(binding.localAddress.getAddress), binding.localAddress.getPort))
        .andThen { case _ => sts.shutdown() }
    }
  }

  def withAwsClient(testCode: AWSSecurityTokenService => Future[Assertion]): Future[Assertion] =
    withTestStsService { authority =>
      val stsAwsClient: AWSSecurityTokenService = stsClient(authority)
      testCode(stsAwsClient).andThen { case _ => stsAwsClient.shutdown() }
    }

  "STS getSessionToken" should {

    "return credentials for valid token" in withAwsClient { stsAwsClient =>
      withOAuth2TokenRequest(validCredentials) { keycloakToken =>
        val credentials = stsAwsClient.getSessionToken(new GetSessionTokenRequest()
          .withTokenCode(keycloakToken.access_token))
          .getCredentials

        assert(!credentials.getAccessKeyId.isEmpty)
        assert(!credentials.getSecretAccessKey.isEmpty)
        assert(credentials.getSessionToken.startsWith("sessiontoken"))
        assert(credentials.getExpiration.getTime <= Instant.now().plusSeconds(20).toEpochMilli)
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
}
