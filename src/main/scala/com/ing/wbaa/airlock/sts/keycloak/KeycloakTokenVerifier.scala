package com.ing.wbaa.airlock.sts.keycloak

import java.util

import com.ing.wbaa.airlock.sts.config.KeycloakSettings
import com.ing.wbaa.airlock.sts.data._
import com.typesafe.scalalogging.LazyLogging
import org.keycloak.TokenVerifier
import org.keycloak.adapters.KeycloakDeploymentBuilder
import org.keycloak.common.VerificationException
import org.keycloak.representations.{ AccessToken, JsonWebToken }
import org.keycloak.representations.adapters.config.AdapterConfig

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }

trait KeycloakTokenVerifier extends LazyLogging {

  protected[this] def keycloakSettings: KeycloakSettings

  implicit protected[this] def executionContext: ExecutionContext

  import scala.collection.JavaConverters._

  protected[this] def verifyAuthenticationToken(token: BearerToken): Option[AuthenticationUserInfo] = Try {

    val accessToken = TokenVerifier.create(token.value, classOf[AccessToken])
      .publicKey(keycloakDeployment.getPublicKeyLocator.getPublicKey(keycloakSettings.realmPublicKeyId, keycloakDeployment))
      .withChecks(TokenVerifier.SUBJECT_EXISTS_CHECK, TokenVerifier.IS_ACTIVE, new IssuedForListCheck(keycloakSettings.issuerForList))
    if (keycloakSettings.checkRealmUrl) accessToken.withChecks(new TokenVerifier.RealmUrlCheck(keycloakDeployment.getRealmInfoUrl))
    accessToken.verify.getToken
  } match {
    case Success(keycloakToken) =>
      logger.debug("Token successfully validated with Keycloak ")
      Some(
        AuthenticationUserInfo(
          UserName(keycloakToken.getPreferredUsername),
          keycloakToken.getOtherClaims
            .getOrDefault("user-groups", new util.ArrayList[String]())
            .asInstanceOf[util.ArrayList[String]].asScala.toSet.map(UserGroup),
          AuthenticationTokenId(keycloakToken.getId)
        ))
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

  class IssuedForListCheck(val expectedIssuedForList: Set[String]) extends TokenVerifier.Predicate[JsonWebToken] {
    @throws[VerificationException]
    override def test(jsonWebToken: JsonWebToken): Boolean = {
      val issuerFor = jsonWebToken.getIssuedFor
      if (expectedIssuedForList.contains(issuerFor)) true
      else throw new VerificationException(s"Expected issuedFor ($issuerFor) doesn't match")
    }
  }
}
