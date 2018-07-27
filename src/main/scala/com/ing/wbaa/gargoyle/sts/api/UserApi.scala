package com.ing.wbaa.gargoyle.sts.api

import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.ing.wbaa.gargoyle.sts.service.UserService

class UserApi(userService: UserService) {

  val routes: Route = verifyUser ~ getUser

  def verifyUser: Route = logRequestResult("debug") {
    get {
      path("isCredentialActive") {
        parameters('accessKey, 'sessionToken) { (accessKey, sessionToken) =>
          userService.isCredentialActive(accessKey, sessionToken) match {
            case true => complete(StatusCodes.OK)
            case _    => complete(StatusCodes.Forbidden)
          }
        }
      }
    }
  }

  def getUser: Route = logRequestResult("debug") {
    get {
      path("userInfo") {
        parameters('accessKey, 'sessionToken) {
          (accessKey, sessionToken) =>
            userService.getUserInfo(accessKey, sessionToken) match {
              case Some(userInfo) => complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, userInfo.toString))
              case _              => complete(StatusCodes.NotFound)
            }
        }
      }
    }
  }

}
