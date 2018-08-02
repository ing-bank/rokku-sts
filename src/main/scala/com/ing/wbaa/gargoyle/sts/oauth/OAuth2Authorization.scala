package com.ing.wbaa.gargoyle.sts.oauth

import akka.http.scaladsl.model.headers.{ Authorization, OAuth2BearerToken }
import akka.http.scaladsl.server.Directives.{ reject, _ }
import akka.http.scaladsl.server._
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

/**
 * OAuth2 class to verify tokens
 *
 * @param oAuth2tokenVerifier - the token verifier to validate tokens
 */
class OAuth2Authorization(oAuth2tokenVerifier: OAuth2TokenVerifier) {

  val logger = Logger(LoggerFactory.getLogger("OAuth2Authorization"))

  /**
   * get the token from http reguest and verify by the provided tokenVerifier
   *
   * @return the verifiedToken or rejection
   */
  def authorizeToken: Directive1[VerifiedToken] = {
    bearerToken.flatMap {
      case Some(token) =>
        logger.debug("received oauth token={}", token)
        onComplete(oAuth2tokenVerifier.verifyToken(token)).flatMap {
          _.map(provide)
            .recover {
              case ex: Throwable =>
                logger.error("Authorization Token could not be verified", ex)
                reject(AuthorizationFailedRejection).toDirective[Tuple1[VerifiedToken]]
            }.get
        }
      case None =>
        logger.debug("no credential token")
        reject(AuthorizationFailedRejection)
    }
  }

  /**
   * because the token can be in many places we have to check:
   * - header - OAuth2BearerToken
   * - cookie - X-Authorization-Token
   * - parameters - WebIdentityToken or TokenCode
   * - body - WebIdentityToken or TokenCode
   *
   * @return the directive with authorization token
   */
  private def bearerToken: Directive1[Option[BearerToken]] =
    for {
      tokenFromAuthBearerHeader <- optionalTokenFromAuthBearerHeader
      tokenFromAuthCookie <- optionalTokenFromCookie
      tokenFromWebIdentityToken <- optionalTokenFromWebIdentityToken
      tokenFromTokenCode <- optionalTokenFromTokenCode
    } yield tokenFromAuthBearerHeader
      .orElse(tokenFromAuthCookie)
      .orElse(tokenFromWebIdentityToken)
      .orElse(tokenFromTokenCode)

  private def optionalTokenFromTokenCode = {
    val tokenCodeString = "TokenCode" ? ""
    for {
      tokenFromParam <- parameter(tokenCodeString).map(stringToBearerTokenOption)
      tokenFromField <- formField(tokenCodeString).map(stringToBearerTokenOption)
    } yield tokenFromParam.orElse(tokenFromField)
  }

  private def optionalTokenFromWebIdentityToken = {
    val webIdentityTokenString = "WebIdentityToken" ? ""
    for {
      tokenFromParam <- parameter(webIdentityTokenString).map(stringToBearerTokenOption)
      tokenFromField <- formField(webIdentityTokenString).map(stringToBearerTokenOption)
    } yield tokenFromParam.orElse(tokenFromField)
  }

  private def optionalTokenFromCookie = {
    optionalCookie("X-Authorization-Token").map(_.map(c => BearerToken(c.value)))
  }

  private def optionalTokenFromAuthBearerHeader = {
    optionalHeaderValueByType(classOf[Authorization]).map(extractBearerToken)
  }

  private def extractBearerToken(authHeader: Option[Authorization]): Option[BearerToken] =
    authHeader.collect {
      case Authorization(OAuth2BearerToken(token)) => BearerToken(token)
    }

  private val stringToBearerTokenOption: String => Option[BearerToken] = t => if (t.isEmpty) None else Some(BearerToken(t))
}

case class BearerToken(value: String) extends AnyVal
