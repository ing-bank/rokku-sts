package com.ing.wbaa.gargoyle.sts.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.ing.wbaa.gargoyle.sts.service.UserInfo
import spray.json.RootJsonFormat

import scala.concurrent.Future

trait UserApi {

  def isCredentialActive(accessKey: String, sessionToken: String): Future[Boolean]

  def getUserInfo(accessKey: String, sessionToken: String): Future[Option[UserInfo]]

  val userRoutes: Route = verifyUser ~ getUser

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import spray.json.DefaultJsonProtocol._

  implicit val userInfoJsonFormat: RootJsonFormat[UserInfo] = jsonFormat4(UserInfo)

  def verifyUser: Route = logRequestResult("debug") {
    path("isCredentialActive") {
      get {
        parameters('accessKey, 'sessionToken) { (accessKey, sessionToken) =>
          onSuccess(isCredentialActive(accessKey, sessionToken)) { isActive =>
            complete(if (isActive) StatusCodes.OK else StatusCodes.Forbidden)
          }
        }
      }
    }
  }

  def getUser: Route = logRequestResult("debug") {
    path("userInfo") {
      get {
        parameters('accessKey, 'sessionToken) {
          (accessKey, sessionToken) =>
            onSuccess(getUserInfo(accessKey, sessionToken)) {
              case Some(userInfo) => complete(userInfo)
              case _              => complete(StatusCodes.NotFound)
            }
        }
      }
    }
  }

}
