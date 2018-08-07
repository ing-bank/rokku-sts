package com.ing.wbaa.gargoyle.sts.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ Directive, Route }
import com.ing.wbaa.gargoyle.sts.oauth.OAuth2Directives.oAuth2Authorization
import com.ing.wbaa.gargoyle.sts.oauth.OAuth2TokenVerifier
import com.ing.wbaa.gargoyle.sts.service.{ TokenService, TokenXML }
import com.typesafe.scalalogging.LazyLogging

trait STSApi
  extends OAuth2TokenVerifier
  with TokenService
  with TokenXML
  with LazyLogging {

  private val getOrPost = get | post & pathSingleSlash
  private val actionDirective = parameter("Action") | formField("Action")
  private val assumeRoleInputList = ('RoleArn, 'RoleSessionName, 'WebIdentityToken, 'DurationSeconds.as[Int] ? 3600)
  private val assumeRoleDirective = parameters(assumeRoleInputList) | formFields(assumeRoleInputList)
  private val getSessionTokenDirective: Directive[Tuple1[Int]] =
    parameters('DurationSeconds.as[Int]) | formField('DurationSeconds.as[Int] ? 3600)

  def stsRoutes: Route = logRequestResult("debug") {
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
      oAuth2Authorization(verifyToken) { token =>
        onSuccess(getSessionToken(token, durationSeconds)) {
          case Some(sessionToken) =>
            complete(getSessionTokenResponseToXML(sessionToken))
          case _ => complete(StatusCodes.Forbidden)
        }
      }
    }
  }

  private def assumeRoleWithWebIdentityHandler: Route = {
    assumeRoleDirective { (roleArn, roleSessionName, _, durationSeconds) =>
      oAuth2Authorization(verifyToken) { token =>
        onSuccess(getAssumeRoleWithWebIdentity(roleArn, roleSessionName, token, durationSeconds)) {
          case Some(assumeRoleWithWebIdentity) =>
            logger.info("assumeRoleWithWebIdentityHandler granted {}", assumeRoleWithWebIdentity)
            complete(assumeRoleWithWebIdentityResponseToXML(assumeRoleWithWebIdentity))
          case _ =>
            logger.info("assumeRoleWithWebIdentityHandler forbidden")
            complete(StatusCodes.Forbidden)
        }
      }
    }
  }
}
