package ing.wbaa.gargoyle.sts.oauth

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
   * get the token from header or cookie or parameters
   *
   * @return the authorization token
   */
  private def bearerToken: Directive1[Option[String]] =
    for {
      tokenFromAuthBearerHeader <- optionalHeaderValueByType(classOf[Authorization]).map(extractBearerToken)
      tokenFromAuthCookie <- optionalCookie("X-Authorization-Token").map(_.map(_.value))
      tokenFromWebIdentityToken <- parameter("WebIdentityToken" ? "").map(t => if (t.isEmpty) None else Some(t))
    } yield tokenFromAuthBearerHeader.orElse(tokenFromAuthCookie).orElse(tokenFromWebIdentityToken)

  /**
   * extract the token from the autotization header
   *
   * @param authHeader - the http authorization header
   * @return the token
   */
  private def extractBearerToken(authHeader: Option[Authorization]): Option[String] =
    authHeader.collect {
      case Authorization(OAuth2BearerToken(token)) => token
    }
}

