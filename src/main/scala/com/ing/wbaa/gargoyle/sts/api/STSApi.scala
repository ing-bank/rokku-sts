package com.ing.wbaa.gargoyle.sts.api

import java.util.concurrent.TimeUnit

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.ing.wbaa.gargoyle.sts.api.xml.TokenXML
import com.ing.wbaa.gargoyle.sts.data._
import com.ing.wbaa.gargoyle.sts.data.aws._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.concurrent.duration.Duration

trait STSApi extends LazyLogging with TokenXML {

  import directive.STSDirectives.authorizeToken

  private val getOrPost = get | post & pathSingleSlash
  private val actionDirective = parameter("Action") | formField("Action")

  private val parseDurationSeconds: Option[Int] => Option[Duration] =
    _.map(durationSeconds => Duration(durationSeconds, TimeUnit.SECONDS))

  private val assumeRoleInputs = {
    val inputList = ('RoleArn, 'RoleSessionName, 'WebIdentityToken, "DurationSeconds".as[Int].?)
    (parameters(inputList) | formFields(inputList)).tmap (t => t.copy(_4 = parseDurationSeconds(t._4)))
  }
  private val getSessionTokenInputs = {
    val input = "DurationSeconds".as[Int].?
    (parameters(input) | formField(input)).tmap(t => t.copy(parseDurationSeconds(t._1)))
  }

  protected[this] def getAwsCredentialWithToken(userName: UserName, duration: Option[Duration], assumedGroup: Option[UserGroup]): Future[AwsCredentialWithToken]

  // Keycloak
  protected[this] def verifyKeycloakToken(token: BearerToken): Option[(KeycloakUserInfo, KeycloakTokenId)]

  protected[this] def canUserAssumeRole(keycloakUserInfo: KeycloakUserInfo, roleArn: String): Future[Option[UserGroup]]

  def stsRoutes: Route = logRequestResult("debug") {
    getOrPost {
      actionDirective {
        case "AssumeRoleWithWebIdentity" => assumeRoleWithWebIdentityHandler
        case "GetSessionToken"           => getSessionTokenHandler
        case action =>
          logger.warn("unhandled action {}", action)
          complete(StatusCodes.BadRequest)
      }
    }
  }

  private def getSessionTokenHandler: Route = {
    getSessionTokenInputs { durationSeconds =>
      authorizeToken(verifyKeycloakToken) { case (keycloakUserInfo: KeycloakUserInfo, _) =>
        onSuccess(getAwsCredentialWithToken(keycloakUserInfo.userName, durationSeconds, None)) { awsCredentialWithToken =>
          complete(getSessionTokenResponseToXML(awsCredentialWithToken))
        }
      }
    }
  }

  private def assumeRoleWithWebIdentityHandler: Route = {
    assumeRoleInputs { (roleArn, roleSessionName, _, durationSeconds) =>
      authorizeToken(verifyKeycloakToken) { case (keycloakUserInfo: KeycloakUserInfo, keycloakTokenId: KeycloakTokenId) =>
        onSuccess(canUserAssumeRole(keycloakUserInfo, roleArn)) {
          case Some(assumedGroup) =>
            onSuccess(getAwsCredentialWithToken(keycloakUserInfo.userName, durationSeconds, Some(assumedGroup))) { awsCredentialWithToken =>
              logger.info("assumeRoleWithWebIdentityHandler granted")
              complete(assumeRoleWithWebIdentityResponseToXML(
                awsCredentialWithToken,
                UserInfo(keycloakUserInfo.userName, Some(assumedGroup)),
                roleArn,
                roleSessionName,
                keycloakTokenId
              ))
            }

          case None =>
            logger.info("assumeRoleWithWebIdentityHandler forbidden")
            complete(StatusCodes.Forbidden)
        }
      }
    }
  }
}
