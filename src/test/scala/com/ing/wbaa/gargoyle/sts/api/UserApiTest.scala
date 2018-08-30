package com.ing.wbaa.gargoyle.sts.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{ MissingQueryParamRejection, Route }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.ing.wbaa.gargoyle.sts.data.aws.{ AwsAccessKey, AwsSecretKey, AwsSessionToken }
import com.ing.wbaa.gargoyle.sts.data.{ STSUserInfo, UserGroup, UserName }
import org.scalatest.{ BeforeAndAfterAll, DiagrammedAssertions, WordSpec }

import scala.concurrent.Future

class UserApiTest extends WordSpec
  with DiagrammedAssertions
  with ScalatestRouteTest
  with BeforeAndAfterAll {

  trait testUserApi extends UserApi {
    override def isTokenActive(awsAccessKey: AwsAccessKey, awsSessionToken: AwsSessionToken): Future[Boolean] =
      Future.successful(true)

    override def getUserWithAssumedGroups(awsAccessKey: AwsAccessKey, awsSessionToken: AwsSessionToken): Future[Option[STSUserInfo]] =
      Future.successful(Some(STSUserInfo(UserName("username"), Some(UserGroup("usergroup")), AwsAccessKey("a"), AwsSecretKey("s"))))
  }

  private[this] val testRoute: Route = new testUserApi {}.userRoutes

  "User api" should {
    "check credential and return rejection because missing the accessKey param" in {
      Get("/isCredentialActive") ~> testRoute ~> check {
        assert(rejection == MissingQueryParamRejection("accessKey"))
      }
    }

    "check credential and return rejection because missing the sessionKey param" in {
      Get("/isCredentialActive?accessKey=123") ~> testRoute ~> check {
        assert(rejection == MissingQueryParamRejection("sessionToken"))
      }
    }

    "check credential and return status ok" in {
      Get(s"/isCredentialActive?accessKey=access&sessionToken=session") ~> testRoute ~> check {
        assert(status == StatusCodes.OK)
      }
    }

    "check credential and return status forbidden because wrong the accessKey" in {
      Get(s"/isCredentialActive?accessKey=access&sessionToken=session") ~> new testUserApi {
        override def isTokenActive(awsAccessKey: AwsAccessKey, awsSessionToken: AwsSessionToken): Future[Boolean] =
          Future.successful(false)
      }.userRoutes ~> check {
        assert(status == StatusCodes.Forbidden)
      }
    }

    "return user info" in {
      Get(s"/userInfo?accessKey=accesskey&sessionToken=sessionToken") ~> testRoute ~> check {
        assert(status == StatusCodes.OK)
        val response = responseAs[String]
        assert(response == """{"userName":"username","userGroup":"usergroup","accessKey":"a","secretKey":"s"}""")
      }
    }

    "return user not found because the wrong access key " in {
      Get(s"/userInfo?accessKey=acccesskey&sessionToken=sessionToken") ~> new testUserApi {
        override def getUserWithAssumedGroups(awsAccessKey: AwsAccessKey, awsSessionToken: AwsSessionToken): Future[Option[STSUserInfo]] =
          Future.successful(None)
      }.userRoutes ~> check {
        assert(status == StatusCodes.NotFound)
      }
    }
  }
}

