package com.ing.wbaa.gargoyle.sts.oauth

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.sts.config.GargoyleKeycloakSettings
import com.ing.wbaa.gargoyle.sts.helper.{KeycloackToken, OAuth2TokenRequest}
import org.keycloak.common.VerificationException
import org.scalatest.{Assertion, AsyncWordSpec, DiagrammedAssertions}

import scala.concurrent.{ExecutionContext, Future}

class KeycloakTokenVerifierTest extends AsyncWordSpec with DiagrammedAssertions {

  final implicit val testSystem: ActorSystem = ActorSystem.create("test-system")

  private val gargoyleKeycloakSettings = new GargoyleKeycloakSettings(testSystem.settings.config) {
    override val realmPublicKeyId: String = "FJ86GcF3jTbNLOco4NvZkUCIUmfYCqoqtOQeMfbhNlE"
  }

  private val tokenVerifier = new KeycloakTokenVerifier {
    override protected[this] def keycloakSettings: GargoyleKeycloakSettings = gargoyleKeycloakSettings

    override implicit def executionContext: ExecutionContext = testSystem.dispatcher
  }

  private def withOAuth2TokenRequest(formData: Map[String, String])(testCode: KeycloackToken => Future[Assertion]): Future[Assertion] = {
    new OAuth2TokenRequest() {
      override protected implicit def system: ActorSystem = testSystem

      override protected[this] def keycloakSettings: GargoyleKeycloakSettings = gargoyleKeycloakSettings
    }.keycloackToken(formData).flatMap(testCode)
  }

  private val validCredentials = Map("grant_type" -> "password", "username" -> "userone", "password" -> "password", "client_id" -> "sts-gargoyle")

  "Keycloak verifier" should {
    "return verified token" in withOAuth2TokenRequest(validCredentials) { keycloakToken =>
      tokenVerifier.verifyToken(BearerToken(keycloakToken.access_token)).map(token => {
        assert(token.name == "User One")
        assert(token.name == "User One")
        assert(token.username == "userone")
        assert(token.email == "userone@test.com")
        assert(token.roles.contains("user"))
      })
    }

    "thrown VerificationException because invalid token is provided" in {
      recoverToSucceededIf[VerificationException] {
        tokenVerifier.verifyToken(BearerToken("invalid"))
      }
    }
  }


}
