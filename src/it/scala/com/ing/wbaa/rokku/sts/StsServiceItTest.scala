package com.ing.wbaa.rokku.sts

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.Authority
import akka.http.scaladsl.model.Uri.Host
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.model.AWSSecurityTokenServiceException
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest
import com.amazonaws.services.securitytoken.model.GetSessionTokenRequest
import com.ing.wbaa.rokku.sts.config.HttpSettings
import com.ing.wbaa.rokku.sts.config.KeycloakSettings
import com.ing.wbaa.rokku.sts.config.RedisSettings
import com.ing.wbaa.rokku.sts.config.StsSettings
import com.ing.wbaa.rokku.sts.config.VaultSettings
import com.ing.wbaa.rokku.sts.data.UserAssumeRole
import com.ing.wbaa.rokku.sts.data.Username
import com.ing.wbaa.rokku.sts.data.aws._
import com.ing.wbaa.rokku.sts.helper.KeycloackToken
import com.ing.wbaa.rokku.sts.helper.OAuth2TokenRequest
import com.ing.wbaa.rokku.sts.keycloak.KeycloakClient
import com.ing.wbaa.rokku.sts.keycloak.KeycloakTokenVerifier
import com.ing.wbaa.rokku.sts.service.UserTokenDbService
import com.ing.wbaa.rokku.sts.service.db.Redis
import com.ing.wbaa.rokku.sts.service.db.RedisModel
import com.ing.wbaa.rokku.sts.service.db.dao.STSUserDAO
import com.ing.wbaa.rokku.sts.vault.VaultService
import org.scalatest.Assertion
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AsyncWordSpec

import java.time.Instant
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Random

class StsServiceItTest extends AsyncWordSpec with Diagrams
  with AWSSTSClient with OAuth2TokenRequest {
  override implicit val testSystem: ActorSystem = ActorSystem.create("test-system")
  override implicit val exContext: ExecutionContextExecutor = testSystem.dispatcher

  val keycloakSettings: KeycloakSettings = new KeycloakSettings(testSystem.settings.config)
  private val validCredentials = Map(
    "grant_type" -> "password",
    "username" -> "userone",
    "password" -> "password",
    "client_id" -> keycloakSettings.resource,
    "client_secret" -> keycloakSettings.clientSecret,
  )
  private val invalidCredentials = validCredentials + ("password" -> "xxx")
  private val validAdminArn = "arn:aws:iam::account-id:role/admin"
  private val forbiddenSuperUserArn = "arn:aws:iam:account-id:role/superuser"

  private[this] val rokkuHttpSettings: HttpSettings = new HttpSettings(testSystem.settings.config) {
    override val httpPort: Int = 0
    override val httpBind: String = "127.0.0.1"
  }

  def withOAuth2TokenRequest(formData: Map[String, String])(testCode: KeycloackToken => Assertion): Future[Assertion] = {
    keycloackToken(formData).map(testCode(_))
  }

  // Fixture for starting and stopping a test proxy that tests can interact with.
  def withTestStsService(testCode: Authority => Future[Assertion]): Future[Assertion] = {
    val sts = new RokkuStsService with KeycloakTokenVerifier with UserTokenDbService with STSUserDAO with Redis with RedisModel with VaultService with KeycloakClient {
      override implicit def system: ActorSystem = testSystem

      override protected[this] def httpSettings: HttpSettings = rokkuHttpSettings

      val keycloakSettings: KeycloakSettings = new KeycloakSettings(testSystem.settings.config)

      override protected[this] def stsSettings: StsSettings = StsSettings(testSystem)

      override protected[this] def redisSettings: RedisSettings = new RedisSettings(testSystem.settings.config)

      override protected[this] def insertToken(awsSessionToken: AwsSessionToken, username: Username, expirationDate: AwsSessionTokenExpiration): Future[Boolean] =
        Future.successful(true)

      override protected[this] def insertToken(awsSessionToken: AwsSessionToken, username: Username, role: UserAssumeRole, expirationDate: AwsSessionTokenExpiration): Future[Boolean] =
        Future.successful(true)

      override protected[this] def getToken(awsSessionToken: AwsSessionToken, username: Username): Future[Option[(Username, UserAssumeRole, AwsSessionTokenExpiration)]] =
        Future.successful(None)

      override def generateAwsSession(duration: Option[Duration]): AwsSession = AwsSession(
        AwsSessionToken("sessiontoken" + Random.alphanumeric.take(32).mkString),
        AwsSessionTokenExpiration(Instant.now().plusSeconds(20))
      )

      override protected[this] def vaultSettings: VaultSettings = new VaultSettings(testSystem.settings.config)
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

  "STS assumeRole" should {
    "return credentials for a valid token" in withAwsClient { stsAwsClient =>
      withOAuth2TokenRequest(validCredentials) { keycloakToken =>
        val credentials = stsAwsClient.assumeRole(new AssumeRoleRequest()
          .withTokenCode(keycloakToken.access_token)
          .withRoleArn(validAdminArn)
          .withRoleSessionName("test"))
          .getCredentials

        assert(!credentials.getAccessKeyId.isEmpty)
        assert(!credentials.getSecretAccessKey.isEmpty)
        assert(credentials.getSessionToken.startsWith("sessiontoken"))
        assert(credentials.getExpiration.getTime <= Instant.now().plusSeconds(20).toEpochMilli)
      }
    }

    "throw AWSSecurityTokenServiceException because there is invalid arn" in withAwsClient { stsAwsClient =>
      withOAuth2TokenRequest(validCredentials) { keycloakToken =>
        assertThrows[AWSSecurityTokenServiceException] {
          stsAwsClient.assumeRole(new AssumeRoleRequest()
            .withTokenCode(keycloakToken.access_token)
            .withRoleArn(forbiddenSuperUserArn)
            .withRoleSessionName("test"))
            .getCredentials
        }
      }
    }

    "throw AWSSecurityTokenServiceException because there is invalid token" in withAwsClient { stsAwsClient =>
      withOAuth2TokenRequest(invalidCredentials) { keycloakToken =>
        assertThrows[AWSSecurityTokenServiceException] {
          stsAwsClient.assumeRole(new AssumeRoleRequest()
            .withTokenCode(keycloakToken.access_token)
            .withRoleArn(validAdminArn)
            .withRoleSessionName("test"))
            .getCredentials
        }
      }
    }

  }
}
