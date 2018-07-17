package ing.wbaa.gargoyle.sts.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ing.wbaa.gargoyle.sts.oauth.OAuth2Directives.oAuth2Authorization
import ing.wbaa.gargoyle.sts.oauth.OAuth2TokenVerifier
import ing.wbaa.gargoyle.sts.service.TokenService

class S3Api(oAuth2TokenVerifier: OAuth2TokenVerifier, tokenService: TokenService) {

  val routes: Route = getAccessToken

  def getAccessToken: Route = logRequestResult("debug") {
    get {
      pathSingleSlash {
        parameter("Action") {
          case action if "AssumeRoleWithWebIdentity".equals(action) =>
            parameters('RoleArn, 'RoleSessionName, 'WebIdentityToken, "DurationSeconds".as[Int] ? 3600) { (roleArn, roleSessionName, webIdentityToken, durationSeconds) =>
              oAuth2Authorization(oAuth2TokenVerifier) { token =>
                tokenService.getAssumeRoleWithWebIdentity(roleArn, roleSessionName, webIdentityToken, durationSeconds) match {
                  case Some(assumeRoleWithWebIdentity) => complete(HttpEntity(contentType = ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), assumeRoleWithWebIdentity.toString))
                  case _                               => complete(StatusCodes.Forbidden)
                }
              }
            }
          case action if "GetSessionToken".equals(action) =>
            parameters('DurationSeconds.as[Int] ? 3600) { durationSeconds =>
              oAuth2Authorization(oAuth2TokenVerifier) { token =>
                tokenService.getSessionToken(durationSeconds) match {
                  case Some(sessionToken) => complete(HttpEntity(contentType = ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), sessionToken.toString))
                  case _                  => complete(StatusCodes.Forbidden)
                }
              }
            }
          case _ => complete(StatusCodes.BadRequest)
        }
      }
    }
  }
}
