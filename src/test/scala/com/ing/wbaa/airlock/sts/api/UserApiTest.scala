package com.ing.wbaa.airlock.sts.api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.{ MissingHeaderRejection, MissingQueryParamRejection, Route }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.ing.wbaa.airlock.sts.config.StsSettings
import com.ing.wbaa.airlock.sts.data.aws.{ AwsAccessKey, AwsSecretKey, AwsSessionToken }
import com.ing.wbaa.airlock.sts.data.{ STSUserInfo, UserGroup, UserName }
import org.scalatest.{ BeforeAndAfterAll, DiagrammedAssertions, WordSpec }

import scala.concurrent.Future

class UserApiTest extends WordSpec
  with DiagrammedAssertions
  with ScalatestRouteTest
  with BeforeAndAfterAll {

  trait testUserApi extends UserApi {
    override def isCredentialActive(awsAccessKey: AwsAccessKey, awsSessionToken: Option[AwsSessionToken]): Future[Option[STSUserInfo]] =
      Future.successful(Some(STSUserInfo(UserName("username"), Set(UserGroup("group1"), UserGroup("group2")), AwsAccessKey("a"), AwsSecretKey("s"))))
  }

  val bearerToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzZXJ2aWNlIjoiYWlybG9jayIsImlzcyI6ImFpcmxvY2sifQ.qLOdtBK-ksKd4KrjS4N5so9bzXsQHHvsXVeQuuMYC8s"

  val testSystem: ActorSystem = ActorSystem.create("test-system")

  private[this] val testRoute: Route = new testUserApi {
    override protected[this] def stsSettings: StsSettings = new StsSettings(testSystem.settings.config)
  }.userRoutes

  "User api" should {
    "check isCredentialActive" that {

      "returns user info" in {
        Get(s"/isCredentialActive?accessKey=accesskey&sessionToken=sessionToken")
          .addHeader(RawHeader("Authorization", bearerToken)) ~> testRoute ~> check {
            assert(status == StatusCodes.OK)
            val response = responseAs[String]
            assert(response == """{"userName":"username","userGroups":["group1","group2"],"accessKey":"a","secretKey":"s"}""")
          }
      }

      "fails to return user info without authentication" in {
        Get(s"/isCredentialActive?accessKey=accesskey&sessionToken=sessionToken") ~> testRoute ~> check {
          assert(rejection == MissingHeaderRejection("Authorization"))
        }
      }

      "check credential and return rejection because missing the accessKey param" in {
        Get("/isCredentialActive")
          .addHeader(RawHeader("Authorization", bearerToken)) ~> testRoute ~> check {
            assert(rejection == MissingQueryParamRejection("accessKey"))
          }
      }

      "check credential and return status forbidden because wrong the accessKey" in {
        Get(s"/isCredentialActive?accessKey=access&sessionToken=session")
          .addHeader(RawHeader("Authorization", bearerToken)) ~> new testUserApi {
            override protected[this] def stsSettings: StsSettings = new StsSettings(testSystem.settings.config)

            override def isCredentialActive(awsAccessKey: AwsAccessKey, awsSessionToken: Option[AwsSessionToken]): Future[Option[STSUserInfo]] =
              Future.successful(None)
          }.userRoutes ~> check {
            assert(status == StatusCodes.Forbidden)
          }
      }
    }
  }
}

