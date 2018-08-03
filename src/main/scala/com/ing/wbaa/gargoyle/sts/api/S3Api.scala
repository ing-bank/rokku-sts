package com.ing.wbaa.gargoyle.sts.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.ing.wbaa.gargoyle.sts.oauth.OAuth2Directives.oAuth2Authorization
import com.ing.wbaa.gargoyle.sts.oauth.OAuth2TokenVerifier
import com.ing.wbaa.gargoyle.sts.service.{ TokenService, TokenXML }
import com.typesafe.scalalogging.LazyLogging

class S3Api(oAuth2TokenVerifier: OAuth2TokenVerifier, tokenService: TokenService)
  extends LazyLogging
  with TokenXML {

  private val getOrPost = get | post & pathSingleSlash
  private val actionDirective = parameter("Action") | formField("Action")
  private val assumeRoleDirective = parameters('RoleArn, 'RoleSessionName, 'WebIdentityToken, "DurationSeconds".as[Int] ? 3600) |
    formFields('RoleArn, 'RoleSessionName, 'WebIdentityToken, "DurationSeconds".as[Int] ? 3600)
  private val getSessionTokenDirective = parameters('DurationSeconds.as[Int]) | formField('DurationSeconds.as[Int] ? 3600)

  val routes: Route = getAccessToken

  def getAccessToken: Route = logRequestResult("debug") {
    getOrPost {
      actionDirective {
        case action if "AssumeRoleWithWebIdentity".equals(action) => assumeRoleWithWebIdentityHandler
        case action if "GetSessionToken".equals(action)           => getSessionTokenHandler
        case action =>
          logger.warn("unhandled action {}", action)
          complete(StatusCodes.BadRequest)
      }
    }
  }

  private def getSessionTokenHandler: Route = {
    getSessionTokenDirective { durationSeconds =>
      oAuth2Authorization(oAuth2TokenVerifier) { token =>
        tokenService.getSessionToken(durationSeconds) match {
          case Some(sessionToken) =>
            complete(getSessionTokenResponseToXML(sessionToken))
          case _ => complete(StatusCodes.Forbidden)
        }
      }
    }
  }

  private def assumeRoleWithWebIdentityHandler: Route = {
    assumeRoleDirective { (roleArn, roleSessionName, webIdentityToken, durationSeconds) =>
      oAuth2Authorization(oAuth2TokenVerifier) { token =>
        tokenService.getAssumeRoleWithWebIdentity(roleArn, roleSessionName, webIdentityToken, durationSeconds) match {
          case Some(assumeRoleWithWebIdentity) =>
            complete(assumeRoleWithWebIdentityResponseToXML(assumeRoleWithWebIdentity))
          case _ => complete(StatusCodes.Forbidden)
        }
      }
    }
  }
}
