package com.ing.wbaa.gargoyle.sts.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{ MissingQueryParamRejection, Route }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.ing.wbaa.gargoyle.sts.data.aws.{ AwsAccessKey, AwsSecretKey, AwsSessionToken }
import com.ing.wbaa.gargoyle.sts.data.{ STSUserInfo, UserAssumedGroup, UserName }
import org.scalatest.{ BeforeAndAfterAll, DiagrammedAssertions, WordSpec }

import scala.concurrent.Future

class UserApiTest extends WordSpec
  with DiagrammedAssertions
  with ScalatestRouteTest
  with BeforeAndAfterAll {

  trait testUserApi extends UserApi {
    override def isCredentialActive(awsAccessKey: AwsAccessKey, awsSessionToken: Option[AwsSessionToken]): Future[Option[STSUserInfo]] =
      Future.successful(Some(STSUserInfo(UserName("username"), Some(UserAssumedGroup("usergroup")), AwsAccessKey("a"), AwsSecretKey("s"))))
  }

  private[this] val testRoute: Route = new testUserApi {}.userRoutes

  "User api" should {
    "check isCredentialActive" that {

      "returns user info" in {
        Get(s"/isCredentialActive?accessKey=accesskey&sessionToken=sessionToken") ~> testRoute ~> check {
          assert(status == StatusCodes.OK)
          val response = responseAs[String]
          assert(response == """{"userName":"username","userGroup":"usergroup","accessKey":"a","secretKey":"s"}""")
        }
      }

      "check credential and return rejection because missing the accessKey param" in {
        Get("/isCredentialActive") ~> testRoute ~> check {
          assert(rejection == MissingQueryParamRejection("accessKey"))
        }
      }

      "check credential and return status forbidden because wrong the accessKey" in {
        Get(s"/isCredentialActive?accessKey=access&sessionToken=session") ~> new testUserApi {
          override def isCredentialActive(awsAccessKey: AwsAccessKey, awsSessionToken: Option[AwsSessionToken]): Future[Option[STSUserInfo]] =
            Future.successful(None)
        }.userRoutes ~> check {
          assert(status == StatusCodes.Forbidden)
        }
      }
    }
  }
}

