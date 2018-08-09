package com.ing.wbaa.gargoyle.sts.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{ MissingQueryParamRejection, Route }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.ing.wbaa.gargoyle.sts.service.UserInfo
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }

import scala.concurrent.Future

class UserApiTest extends WordSpec
  with Matchers
  with ScalatestRouteTest
  with BeforeAndAfterAll {

  import com.ing.wbaa.gargoyle._

  def userRoutes: Route = {
    new UserApi() {
      def isCredentialActive(accessKey: String, sessionToken: String): Future[Boolean] =
        accessKey match {
          case "okAccessKey" => Future.successful(true)
          case _             => Future.successful(false)
        }

      override def getUserInfo(accessKey: String): Future[Option[UserInfo]] =
        accessKey match {
          case "okAccessKey" => Future.successful(Some(okUserInfo))
          case _             => Future.successful(None)
        }
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

    "return user info" in {
      Get(s"/userInfo?accessKey=$okAccessKey") ~> userRoutes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual """{"userId":"userOk","secretKey":"okSecretKey","groups":["group1","group2"],"arn":"arn:ing-wbaa:iam:::role/TheRole"}"""
      }
    }

    "return user not found because the wrong access key " in {
      Get(s"/userInfo?accessKey=$badAccessKey") ~> userRoutes ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
  }
}

