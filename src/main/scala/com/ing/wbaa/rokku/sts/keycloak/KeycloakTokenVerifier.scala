package com.ing.wbaa.rokku.sts.keycloak

import java.util

import com.ing.wbaa.rokku.sts.config.KeycloakSettings
import com.ing.wbaa.rokku.sts.data._
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

  import scala.jdk.CollectionConverters._

  /**
   * Temporary we define NPA by Name - later we will change it to some keycloak role
   * @param keycloakToken
   * @return true if NPA
   */
  private def isNPA(keycloakToken: AccessToken): Boolean = {
    val NPAClaim = Option(keycloakToken.getOtherClaims.getOrDefault(keycloakSettings.NPAClaim, "")) match {
      case Some(groups: util.ArrayList[_]) => groups.asScala.toList.map(_.toString)
      case Some(group: String)             => List(group)
      case _                               => List.empty[String]
    }
    val isNPA = NPAClaim.nonEmpty && NPAClaim.contains(keycloakSettings.NPAClaimContains)
    logger.debug("user getName={}", keycloakToken.getName)
    logger.debug("user NPA claim={}, NPA claim required={}", NPAClaim.mkString("[", ", ", "]"), keycloakSettings.NPAClaimContains)
    logger.debug("is NPA={}", isNPA)
    isNPA
  }

  protected[this] def verifyAuthenticationToken(token: BearerToken): Option[AuthenticationUserInfo] = Try {

    val accessToken = TokenVerifier.create(token.value, classOf[AccessToken])
      .publicKey(keycloakDeployment.getPublicKeyLocator.getPublicKey(keycloakSettings.realmPublicKeyId, keycloakDeployment))
      .withChecks(TokenVerifier.SUBJECT_EXISTS_CHECK, TokenVerifier.IS_ACTIVE, new IssuedForListCheck(keycloakSettings.issuerForList))
    if (keycloakSettings.checkRealmUrl) accessToken.withChecks(new TokenVerifier.RealmUrlCheck(keycloakDeployment.getRealmInfoUrl))
    accessToken.verify.getToken
  } match {
    case Success(keycloakToken) =>
      logger.info("Token successfully validated with Keycloak user = {}", keycloakToken.getPreferredUsername)
      Some(
        AuthenticationUserInfo(
          Username(keycloakToken.getPreferredUsername),
          keycloakToken.getOtherClaims
            .getOrDefault("user-groups", new util.ArrayList[String]())
            .asInstanceOf[util.ArrayList[String]].asScala.toSet.map(UserGroup),
          AuthenticationTokenId(keycloakToken.getId),
          keycloakToken.getRealmAccess.getRoles.asScala.toSet.map(UserAssumeRole),
          isNPA(keycloakToken)
        ))
    case Failure(exc: VerificationException) =>
      logger.warn("Token (value={}) verification failed ex={}", token.value, exc.getMessage)
      throw new KeycloakException(exc.getMessage)
    case Failure(exc) =>
      logger.error("Unexpected exception during token verification", exc)
      throw new KeycloakException(exc.getMessage)
  }

  private[this] lazy val keycloakDeployment = {
    val config = new AdapterConfig()
    config.setRealm(keycloakSettings.realm)
    config.setAuthServerUrl(s"${keycloakSettings.url}${keycloakSettings.httpRelativePath}/")
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

class KeycloakException(message: String) extends Exception(message)
