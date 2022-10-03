package com.ing.wbaa.rokku.sts.helper

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.scaladsl.Sink
import com.ing.wbaa.rokku.sts.config.KeycloakSettings

import scala.concurrent.{ ExecutionContextExecutor, Future }

case class KeycloackToken(access_token: String)

/**
 * OAuth2 request for token
 */
trait OAuth2TokenRequest {

  protected implicit def testSystem: ActorSystem
  protected implicit def exContext: ExecutionContextExecutor

  protected[this] def keycloakSettings: KeycloakSettings

  import spray.json._
  import DefaultJsonProtocol._

  private implicit val keycloakTokenJson: RootJsonFormat[KeycloackToken] = jsonFormat1(KeycloackToken)

  private def getTokenResponse(formData: Map[String, String]): Future[HttpResponse] = {
    val contentType = RawHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
    Http().singleRequest(HttpRequest(
      uri = Uri(s"${keycloakSettings.url}${keycloakSettings.httpRelativePath}/realms/${keycloakSettings.realm}/protocol/openid-connect/token"),
      method = HttpMethods.POST,
      headers = List(contentType),
      entity = akka.http.scaladsl.model.FormData(formData).toEntity))
  }

  def keycloackToken(formData: Map[String, String]): Future[KeycloackToken] =
    getTokenResponse(formData).map(_.entity.dataBytes.map(_.utf8String)
      .map(_.parseJson.convertTo[KeycloackToken])
      .runWith(Sink.seq)).flatMap(_.map(_.head)).recover { case _ => KeycloackToken("invalid") }

}
