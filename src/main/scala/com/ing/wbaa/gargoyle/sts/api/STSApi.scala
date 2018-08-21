package com.ing.wbaa.gargoyle.sts.api

import java.util.concurrent.TimeUnit

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.ing.wbaa.gargoyle.sts.api.xml.TokenXML
import com.ing.wbaa.gargoyle.sts.data._
import com.ing.wbaa.gargoyle.sts.data.aws.AwsCredentialWithToken
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.concurrent.duration.Duration

trait STSApi extends LazyLogging with TokenXML {

  import directive.STSDirectives.authorizeToken

  private val getOrPost = get | post & pathSingleSlash
  private val actionDirective = parameter("Action") | formField("Action")

  private val parseDurationSeconds: Option[Int] => Option[Duration] =
    durOpt => durOpt.map(durationSeconds => Duration(durationSeconds, TimeUnit.SECONDS))

  private val assumeRoleInputs = {
    val inputList = ('RoleArn, 'RoleSessionName, 'WebIdentityToken, "DurationSeconds".as[Int].?)
    (parameters(inputList) | formFields(inputList)).tmap {
      case Tuple4(one, two, three, durOpt) => Tuple4(one, two, three, parseDurationSeconds(durOpt))
    }
  }
  private val getSessionTokenInputs = {
    val input = "DurationSeconds".as[Int].?
    (parameters(input) | formField(input)).tmap {
      case Tuple1(durOpt) => parseDurationSeconds(durOpt)
    }
  }

  protected[this] def getAwsCredentialWithToken(userInfo: UserInfo, durationSeconds: Option[Duration]): Future[AwsCredentialWithToken]

  protected[this] def canUserAssumeRole(userInfo: UserInfo, roleArn: String): Future[Boolean]

  protected[this] def verifyToken(token: BearerToken): Option[(UserInfo, KeycloakTokenId)]

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
      authorizeToken(verifyToken) { case (userInfo: UserInfo, _) =>
        onSuccess(getAwsCredentialWithToken(userInfo, durationSeconds)) { awsCredentialWithToken =>
          complete(getSessionTokenResponseToXML(awsCredentialWithToken))
        }
      }
    }
  }

  private def assumeRoleWithWebIdentityHandler: Route = {
    assumeRoleInputs { (roleArn, roleSessionName, _, durationSeconds) =>
      authorizeToken(verifyToken) { case (userInfo: UserInfo, keycloakTokenId: KeycloakTokenId) =>
        onSuccess(canUserAssumeRole(userInfo, roleArn)) {
          case true =>
            onSuccess(getAwsCredentialWithToken(userInfo, durationSeconds)) { awsCredentialWithToken =>
              logger.info("assumeRoleWithWebIdentityHandler granted")
              complete(assumeRoleWithWebIdentityResponseToXML(awsCredentialWithToken, userInfo, roleArn, roleSessionName, keycloakTokenId))
            }

          case false =>
            logger.info("assumeRoleWithWebIdentityHandler forbidden")
            complete(StatusCodes.Forbidden)
        }
      }
    }
  }
}
