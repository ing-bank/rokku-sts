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

  implicit def executionContext: ExecutionContext

  import scala.collection.JavaConverters._

  def verifyKeycloakToken(token: BearerToken): Option[KeycloakUserInfo] = {
    Try {
      RSATokenVerifier.verifyToken(
        token.value,
        keycloakDeployment.getPublicKeyLocator.getPublicKey(keycloakSettings.realmPublicKeyId, keycloakDeployment),
        keycloakDeployment.getRealmInfoUrl
      )
    } match {
      case Success(keycloakToken) =>
        logger.debug("Token successfully validated with Keycloak")
        Some(KeycloakUserInfo(
          UserName(keycloakToken.getPreferredUsername),
          keycloakToken.getRealmAccess.getRoles.asScala.toSet.map(UserGroup),
          KeycloakTokenId(keycloakToken.getId)
        ))
      case Failure(exc: VerificationException) =>
        logger.info("Token verification failed", exc)
        None
      case Failure(exc) =>
        logger.error("Unexpected exception during token verification", exc)
        None
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
