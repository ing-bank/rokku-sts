package com.ing.wbaa.airlock.sts.api

import java.util.concurrent.TimeUnit

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.ing.wbaa.airlock.sts.api.xml.TokenXML
import com.ing.wbaa.airlock.sts.data._
import com.ing.wbaa.airlock.sts.data.aws._
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
    (parameters(inputList) | formFields(inputList)).tmap(t =>
      t.copy(_1 = AwsRoleArn(t._1), _4 = parseDurationSeconds(t._4))
    )
  }
  private val getSessionTokenInputs = {
    val input = "DurationSeconds".as[Int].?
    (parameter(input) & formField(input)).tmap {
      case (param, field) =>
        if (param.isDefined) parseDurationSeconds(param)
        else parseDurationSeconds(field)
    }
  }

  protected[this] def getAwsCredentialWithToken(userName: UserName, duration: Option[Duration], assumedGroup: Option[UserAssumedGroup]): Future[AwsCredentialWithToken]

  // Keycloak
  protected[this] def verifyAuthenticationToken(token: BearerToken): Option[AuthenticationUserInfo]

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
      authorizeToken(verifyAuthenticationToken) { keycloakUserInfo =>
        onSuccess(getAwsCredentialWithToken(keycloakUserInfo.userName, durationSeconds, None)) { awsCredentialWithToken =>
          complete(getSessionTokenResponseToXML(awsCredentialWithToken))
        }
      }
    }
  }

  private def assumeRoleWithWebIdentityHandler: Route = {
    assumeRoleInputs { (roleArn, roleSessionName, _, durationSeconds) =>
      authorizeToken(verifyAuthenticationToken) { keycloakUserInfo =>
        roleArn.getGroupUserCanAssume(keycloakUserInfo) match {
          case Some(assumedGroup) =>
            onSuccess(getAwsCredentialWithToken(keycloakUserInfo.userName, durationSeconds, Some(assumedGroup))) { awsCredentialWithToken =>
              logger.info("assumeRoleWithWebIdentityHandler granted")
              complete(assumeRoleWithWebIdentityResponseToXML(
                awsCredentialWithToken,
                roleArn,
                roleSessionName,
                keycloakUserInfo.keycloakTokenId
              ))
            }

          case None =>
            logger.info(s"assumeRoleWithWebIdentityHandler forbidden for arn: ${roleArn.arn}")
            complete(StatusCodes.Forbidden)
        }
      }
    }
  }
}
