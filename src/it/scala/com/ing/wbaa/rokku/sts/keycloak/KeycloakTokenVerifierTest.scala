package com.ing.wbaa.rokku.sts.keycloak

import akka.actor.ActorSystem
import com.ing.wbaa.rokku.sts.config.KeycloakSettings
import com.ing.wbaa.rokku.sts.data.{ BearerToken, UserGroup, Username }
import com.ing.wbaa.rokku.sts.helper.{ KeycloackToken, OAuth2TokenRequest }
import org.keycloak.common.VerificationException
import org.keycloak.representations.JsonWebToken
import org.scalatest.Assertion
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.{ ExecutionContextExecutor, Future }

class KeycloakTokenVerifierTest extends AsyncWordSpec with Diagrams with OAuth2TokenRequest with KeycloakTokenVerifier {

  override implicit val testSystem: ActorSystem = ActorSystem.create("test-system")
  override implicit val exContext: ExecutionContextExecutor = testSystem.dispatcher

  val keycloakSettings: KeycloakSettings = new KeycloakSettings(testSystem.settings.config)

  private def withOAuth2TokenRequest(formData: Map[String, String])(testCode: KeycloackToken => Assertion): Future[Assertion] = {
    keycloackToken(formData).map(testCode)
  }

  private val validCredentialsUser1 = Map(
    "grant_type" -> "password",
    "username" -> "userone",
    "password" -> "password",
    "client_id" -> keycloakSettings.resource,
    "client_secret" -> keycloakSettings.clientSecret,
  )
  private val validCredentialsUser2 = Map(
    "grant_type" -> "password",
    "username" -> "testuser",
    "password" -> "password",
    "client_id" -> keycloakSettings.resource,
    "client_secret" -> keycloakSettings.clientSecret,
  )

  "Keycloak verifier" should {
    "return verified token for user 1" in withOAuth2TokenRequest(validCredentialsUser1) { keycloakToken =>
      val token = verifyAuthenticationToken(BearerToken(keycloakToken.access_token))
      assert(token.map(_.userName).contains(Username("userone")))
      assert(token.exists(_.userGroups.isEmpty))
    }

    "return verified token for user 2" in withOAuth2TokenRequest(validCredentialsUser2) { keycloakToken =>
      val token = verifyAuthenticationToken(BearerToken(keycloakToken.access_token))
      assert(token.map(_.userName).contains(Username("testuser")))
      assert(token.exists(g => g.userGroups(UserGroup("testgroup")) && g.userGroups(UserGroup("group3"))))
    }
  }

  "throw exception when an invalid token is provided" in {
    assertThrows[KeycloakException](verifyAuthenticationToken(BearerToken("invalid")))
  }

  "IssuerFor checker" should {
    "verifies client" in {
      val issuerForOK = new IssuedForListCheck(Set("a", "b", "sts", "")).test(new JsonWebToken() {
        issuedFor = "sts"
      })
      assert(issuerForOK)
    }

    "throws exception" in {
      assertThrows[VerificationException](new IssuedForListCheck(Set("a", "b", "sts", "")).test(new JsonWebToken() {
        issuedFor = "sts2"
      }))
    }
  }
}
