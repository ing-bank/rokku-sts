package ing.wbaa.gargoyle.sts.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ing.wbaa.gargoyle.sts.service.TokenService

class S3Api(tokenService: TokenService) {

  val routes: Route = getAccessToken

  def getAccessToken: Route = logRequestResult("debug") {
    get {
      pathSingleSlash {
        parameter("Action") {
          case action if "AssumeRoleWithWebIdentity".equals(action) =>
            parameters('RoleArn, 'RoleSessionName, 'WebIdentityToken, "DurationSeconds".as[Int] ? 3600) { (roleArn, roleSessionName, webIdentityToken, durationSeconds) =>
              tokenService.getAssumeRoleWithWebIdentity(roleArn, roleSessionName, webIdentityToken, durationSeconds) match {
                case Some(assumeRoleWithWebIdentity) => complete(HttpEntity(contentType = ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), assumeRoleWithWebIdentity.toString))
                case _                               => complete(StatusCodes.Forbidden)
              }
            }
          case action if "GetSessionToken".equals(action) =>
            parameters('DurationSeconds.as[Int] ? 3600) { durationSeconds =>
              tokenService.getSessionToken(durationSeconds) match {
                case Some(sessionToken) => complete(HttpEntity(contentType = ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), sessionToken.toString))
                case _                  => complete(StatusCodes.Forbidden)
              }
            }
          case _ => complete(StatusCodes.BadRequest)
        }
      }
    }
  }

}
