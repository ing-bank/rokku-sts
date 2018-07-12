package ing.wbaa.s3.sts.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{ MissingQueryParamRejection, Route }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ing.wbaa.s3.sts.service.UserService
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }

class UserApiTest extends WordSpec with Matchers with MockFactory with ScalatestRouteTest with BeforeAndAfterAll {

  import ing.wbaa.s3._

  def userRoutes: Route = {
    val userService: UserService = stub[UserService]
    userService.isCredentialActive _ when (okAccessKey, okSessionToken) returns true
    userService.isCredentialActive _ when (okAccessKey, badSessionToken) returns false
    userService.isCredentialActive _ when (badAccessKey, okSessionToken) returns false
    userService.isCredentialActive _ when (badAccessKey, badSessionToken) returns false
    userService.getUserInfo _ when (okAccessKey, okSessionToken) returns Some(okUserInfo)
    userService.getUserInfo _ when (badAccessKey, okSessionToken) returns None
    userService.getUserInfo _ when (badAccessKey, badSessionToken) returns None
    userService.getUserInfo _ when (okAccessKey, badSessionToken) returns None
    new UserApi(userService).routes
  }

  "User api" should {
    "check creadential and return rejection because missing the accessKey param" in {
      Get("/isCredentialActive") ~> userRoutes ~> check {
        rejection shouldEqual MissingQueryParamRejection("accessKey")
      }
    }

    "check creadential and return rejection because missing the sessionKey param" in {
      Get("/isCredentialActive?accessKey=123") ~> userRoutes ~> check {
        rejection shouldEqual MissingQueryParamRejection("sessionToken")
      }
    }

    "check creadential and return status ok" in {
      Get(s"/isCredentialActive?accessKey=$okAccessKey&sessionToken=$okSessionToken") ~> userRoutes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "check creadential and return status forbidden because wrong the accessKey" in {
      Get(s"/isCredentialActive?accessKey=$badAccessKey&sessionToken=$okSessionToken") ~> userRoutes ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "check creadential and return status forbidden because wrong the session token" in {
      Get(s"/isCredentialActive?accessKey=$okAccessKey&sessionToken=$badSessionToken") ~> userRoutes ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "return user info" in {
      Get(s"/userInfo?accessKey=$okAccessKey&sessionToken=$okSessionToken") ~> userRoutes ~> check {
        status shouldEqual StatusCodes.OK
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

