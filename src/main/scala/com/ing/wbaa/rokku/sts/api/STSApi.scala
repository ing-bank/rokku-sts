package com.ing.wbaa.rokku.sts.api

import java.util.concurrent.TimeUnit

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.ing.wbaa.rokku.sts.api.xml.TokenXML
import com.ing.wbaa.rokku.sts.data._
import com.ing.wbaa.rokku.sts.data.aws._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.{ Failure, Success }

trait STSApi extends LazyLogging with TokenXML {

  import directive.STSDirectives.authorizeToken

  private val getOrPost = get | post & pathSingleSlash
  private val actionDirective = parameter("Action") | formField("Action")

  private val parseDurationSeconds: Option[Int] => Option[Duration] =
    _.map(durationSeconds => Duration(durationSeconds, TimeUnit.SECONDS))

  private val getSessionTokenInputs = {
    val input = "DurationSeconds".as[Int].?
    (parameter(input) & formField(input)).tmap {
      case (param, field) =>
        if (param.isDefined) parseDurationSeconds(param)
        else parseDurationSeconds(field)
    }
  }

  protected[this] def getAwsCredentialWithToken(userName: UserName, userGroups: Set[UserGroup], duration: Option[Duration]): Future[AwsCredentialWithToken]

  // Keycloak
  protected[this] def verifyAuthenticationToken(token: BearerToken): Option[AuthenticationUserInfo]

  def stsRoutes: Route = logRequestResult("debug") {
    getOrPost {
      actionDirective {
        case "GetSessionToken" => getSessionTokenHandler
        case action =>
          logger.warn("unhandled action {}", action)
          complete(StatusCodes.BadRequest)
      }
    }
  }

  private def getSessionTokenHandler: Route = {
    getSessionTokenInputs { durationSeconds =>
      authorizeToken(verifyAuthenticationToken) { keycloakUserInfo =>
        onComplete(getAwsCredentialWithToken(keycloakUserInfo.userName, keycloakUserInfo.userGroups, durationSeconds)) {
          case Success(awsCredentialWithToken) => complete(getSessionTokenResponseToXML(awsCredentialWithToken))
          case Failure(_)                      => complete(StatusCodes.BadRequest)
        }
      }
    }
  }

}
