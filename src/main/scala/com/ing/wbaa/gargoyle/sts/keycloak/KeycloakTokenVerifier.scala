package com.ing.wbaa.gargoyle.sts.keycloak

import com.ing.wbaa.gargoyle.sts.config.GargoyleKeycloakSettings
import com.ing.wbaa.gargoyle.sts.data._
import com.typesafe.scalalogging.LazyLogging
import org.keycloak.RSATokenVerifier
import org.keycloak.adapters.KeycloakDeploymentBuilder
import org.keycloak.common.VerificationException
import org.keycloak.representations.adapters.config.AdapterConfig

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }

trait KeycloakTokenVerifier extends LazyLogging {

  protected[this] def keycloakSettings: GargoyleKeycloakSettings

  implicit protected[this] def executionContext: ExecutionContext

  import scala.collection.JavaConverters._

  protected[this] def verifyAuthenticationToken(token: BearerToken): Option[AuthenticationUserInfo] = Try {
    RSATokenVerifier
      .create(token.value)
      .publicKey(keycloakDeployment.getPublicKeyLocator.getPublicKey(keycloakSettings.realmPublicKeyId, keycloakDeployment))
      .realmUrl(keycloakDeployment.getRealmInfoUrl)
      .checkRealmUrl(keycloakSettings.checkRealmUrl)
      .verify
      .getToken
  } match {
    case Success(keycloakToken) =>
      logger.debug("Token successfully validated with Keycloak")
      Some((UserInfo(
        keycloakToken.getPreferredUsername,
        keycloakToken.getRealmAccess.getRoles.asScala.toSet
      ), KeycloakTokenId(
          keycloakToken.getId
        )))
    case Failure(exc: VerificationException) =>
      logger.info("Token verification failed", exc)
      None
    case Failure(exc) =>
      logger.error("Unexpected exception during token verification", exc)
      None
  }

  private[this] lazy val keycloakDeployment = {
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
