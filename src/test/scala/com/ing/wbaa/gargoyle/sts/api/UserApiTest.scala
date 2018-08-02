package com.ing.wbaa.gargoyle.sts.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{ MissingQueryParamRejection, Route }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.ing.wbaa.gargoyle.sts.service.{ UserInfo, UserService }
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }

import scala.concurrent.Future

class UserApiTest extends WordSpec
  with Matchers
  with MockFactory
  with ScalatestRouteTest
  with BeforeAndAfterAll {

  import com.ing.wbaa.gargoyle._

  def userRoutes: Route = {
    new UserApi() {
      val userService: UserService = stub[UserService]
      userService.isCredentialActive _ when (okAccessKey, okSessionToken) returns Future.successful(true)
      userService.isCredentialActive _ when (okAccessKey, badSessionToken) returns Future.successful(false)
      userService.isCredentialActive _ when (badAccessKey, okSessionToken) returns Future.successful(false)
      userService.isCredentialActive _ when (badAccessKey, badSessionToken) returns Future.successful(false)
      userService.getUserInfo _ when (okAccessKey, okSessionToken) returns Future.successful(Some(okUserInfo))
      userService.getUserInfo _ when (badAccessKey, okSessionToken) returns Future.successful(None)
      userService.getUserInfo _ when (badAccessKey, badSessionToken) returns Future.successful(None)
      userService.getUserInfo _ when (okAccessKey, badSessionToken) returns Future.successful(None)

      override def isCredentialActive(accessKey: String, sessionToken: String): Future[Boolean] =
        userService.isCredentialActive(accessKey, sessionToken)

      override def getUserInfo(accessKey: String, sessionToken: String): Future[Option[UserInfo]] =
        userService.getUserInfo(accessKey, sessionToken)
    }.userRoutes
  }

  "User api" should {
    "check credential and return rejection because missing the accessKey param" in {
      Get("/isCredentialActive") ~> userRoutes ~> check {
        rejection shouldEqual MissingQueryParamRejection("accessKey")
      }
    }

    "check credential and return rejection because missing the sessionKey param" in {
      Get("/isCredentialActive?accessKey=123") ~> userRoutes ~> check {
        rejection shouldEqual MissingQueryParamRejection("sessionToken")
      }
    }

    "check credential and return status ok" in {
      Get(s"/isCredentialActive?accessKey=$okAccessKey&sessionToken=$okSessionToken") ~> userRoutes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "check credential and return status forbidden because wrong the accessKey" in {
      Get(s"/isCredentialActive?accessKey=$badAccessKey&sessionToken=$okSessionToken") ~> userRoutes ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "check credential and return status forbidden because wrong the session token" in {
      Get(s"/isCredentialActive?accessKey=$okAccessKey&sessionToken=$badSessionToken") ~> userRoutes ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "return user info" in {
      Get(s"/userInfo?accessKey=$okAccessKey&sessionToken=$okSessionToken") ~> userRoutes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual """{"userId":"userOk","secretKey":"okSecretKey","groups":["group1","group2"],"arn":"arn:ing-wbaa:iam:::role/TheRole"}"""
      }
    }

    "return user not found because the wrong access key " in {
      Get(s"/userInfo?accessKey=$badAccessKey&sessionToken=$okSessionToken") ~> userRoutes ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "return user not found because the wrong access key and session token" in {
      Get(s"/userInfo?accessKey=$badAccessKey&sessionToken=$badSessionToken") ~> userRoutes ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "return user not found because the wrong session token" in {
      Get(s"/userInfo?accessKey=$okAccessKey&sessionToken=$badSessionToken") ~> userRoutes ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
  }
}

