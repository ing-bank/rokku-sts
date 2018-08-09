package com.ing.wbaa.gargoyle.sts.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ Directive, Route }
import com.ing.wbaa.gargoyle.sts.oauth.OAuth2Directives.oAuth2Authorization
import com.ing.wbaa.gargoyle.sts.oauth.{ BearerToken, VerifiedToken }
import com.ing.wbaa.gargoyle.sts.service.{ AssumeRoleWithWebIdentityResponse, CredentialsResponse }
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.xml.NodeSeq

trait STSApi extends LazyLogging {

  private val getOrPost = get | post & pathSingleSlash
  private val actionDirective = parameter("Action") | formField("Action")
  private val assumeRoleInputList = ('RoleArn, 'RoleSessionName, 'WebIdentityToken, 'DurationSeconds.as[Int] ? 3600)
  private val assumeRoleDirective = parameters(assumeRoleInputList) | formFields(assumeRoleInputList)
  private val getSessionTokenDirective: Directive[Tuple1[Int]] =
    parameters('DurationSeconds.as[Int]) | formField('DurationSeconds.as[Int] ? 3600)

  protected[this] def getAssumeRoleWithWebIdentity(
      roleArn: String,
      roleSessionName: String,
      token: VerifiedToken,
      durationSeconds: Int): Future[Option[AssumeRoleWithWebIdentityResponse]]

  protected[this] def getSessionToken(token: VerifiedToken, durationSeconds: Int): Future[Option[CredentialsResponse]]

  protected[this] def getSessionTokenResponseToXML(credentials: CredentialsResponse): NodeSeq

  protected[this] def assumeRoleWithWebIdentityResponseToXML(aRWWIResponse: AssumeRoleWithWebIdentityResponse): NodeSeq

  protected[this] def verifyToken(token: BearerToken): Future[VerifiedToken]

  import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._

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
