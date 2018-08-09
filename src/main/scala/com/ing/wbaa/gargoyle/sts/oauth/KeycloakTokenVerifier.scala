package com.ing.wbaa.gargoyle.sts.oauth

import com.ing.wbaa.gargoyle.sts.config.GargoyleKeycloakSettings
import org.keycloak.RSATokenVerifier
import org.keycloak.adapters.KeycloakDeploymentBuilder
import org.keycloak.representations.adapters.config.AdapterConfig

import scala.concurrent.{ ExecutionContext, Future }

trait KeycloakTokenVerifier {

  protected[this] def keycloakSettings: GargoyleKeycloakSettings
  implicit def executionContext: ExecutionContext

  import scala.collection.JavaConverters._

  def verifyToken(token: BearerToken): Future[VerifiedToken] = {
    Future {
      val keycloakToken = RSATokenVerifier.verifyToken(
        token.value,
        keycloakDeployment.getPublicKeyLocator.getPublicKey(keycloakSettings.realmPublicKeyId, keycloakDeployment),
        keycloakDeployment.getRealmInfoUrl
      )

      VerifiedToken(
        token.value,
        keycloakToken.getId,
        keycloakToken.getName,
        keycloakToken.getPreferredUsername,
        keycloakToken.getEmail,
        keycloakToken.getRealmAccess.getRoles.asScala.toSeq,
        0)
    }
  }

  private[this] val keycloakDeployment = {
    val config = new AdapterConfig()
    config.setRealm(keycloakSettings.realm)
    config.setAuthServerUrl(s"${keycloakSettings.url}/auth")
    config.setSslRequired("external")
    config.setResource(keycloakSettings.resource)
    config.setPublicClient(true)
    config.setConfidentialPort(0)
    KeycloakDeploymentBuilder.build(config)
  }
}
