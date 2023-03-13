package com.ing.wbaa.rokku.sts.api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.{ MissingHeaderRejection, MalformedHeaderRejection, AuthorizationFailedRejection, MissingQueryParamRejection, Route }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.ing.wbaa.rokku.sts.config.StsSettings
import com.ing.wbaa.rokku.sts.data.aws.{ AwsAccessKey, AwsSecretKey, AwsSessionToken }
import com.ing.wbaa.rokku.sts.data.{ STSUserInfo, UserGroup, Username }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Future

class UserApiTest extends AnyWordSpec
  with Diagrams
  with ScalatestRouteTest
  with BeforeAndAfterAll {

  trait testUserApi extends UserApi {
    override def isCredentialActive(awsAccessKey: AwsAccessKey, awsSessionToken: Option[AwsSessionToken]): Future[Option[STSUserInfo]] =
      Future.successful(Some(STSUserInfo(Username("username"), Set(UserGroup("group1"), UserGroup("group2")), AwsAccessKey("a"), AwsSecretKey("s"), None)))
  }

  val testSystem: ActorSystem = ActorSystem.create("test-system")

  private[this] val testRoute: Route = new testUserApi {
    override protected[this] def stsSettings: StsSettings = new StsSettings(testSystem.settings.config)
  }.userRoutes

  def generateBearerToken(serviceClaimValue: String = "rokku") = {
    val stsSettings: StsSettings = new StsSettings(testSystem.settings.config)
    val algorithm = Algorithm.HMAC256(stsSettings.decodeSecret)
    JWT.create()
      .withIssuer("rokku")
      .withClaim("service", serviceClaimValue)
      .sign(algorithm)
  }

  "User api" should {
    "check isCredentialActive" that {

      "returns user info" in {
        Get(s"/isCredentialActive?accessKey=accesskey&sessionToken=sessionToken")
          .addHeader(RawHeader("Authorization", generateBearerToken())) ~> testRoute ~> check {
            assert(status == StatusCodes.OK)
            val response = responseAs[String]
            assert(response == """{"accessKey":"a","secretKey":"s","userGroups":["group1","group2"],"userName":"username","userRole":""}""")
          }
      }

      "fails to return user info without authentication" in {
        Get(s"/isCredentialActive?accessKey=accesskey&sessionToken=sessionToken") ~> testRoute ~> check {
          assert(rejection == MissingHeaderRejection("Authorization"))
        }
      }

      "check credential and return rejection because the accessKey param is missing" in {
        Get("/isCredentialActive")
          .addHeader(RawHeader("Authorization", generateBearerToken())) ~> testRoute ~> check {
            assert(rejection == MissingQueryParamRejection("accessKey"))
          }
      }

      "check credential and return status forbidden because the accessKey is wrong" in {
        Get(s"/isCredentialActive?accessKey=access&sessionToken=session")
          .addHeader(RawHeader("Authorization", generateBearerToken())) ~> new testUserApi {
            override protected[this] def stsSettings: StsSettings = new StsSettings(testSystem.settings.config)
            override def isCredentialActive(awsAccessKey: AwsAccessKey, awsSessionToken: Option[AwsSessionToken]): Future[Option[STSUserInfo]] =
              Future.successful(None)
          }.userRoutes ~> check {
            assert(status == StatusCodes.Forbidden)
          }
      }

      "check credential and return status bad request because the bearerToken is not a valid JWT" in {
        Get(s"/isCredentialActive?accessKey=access&sessionToken=session")
          .addHeader(RawHeader("Authorization", "fakeToken")) ~> testRoute ~> check {
            assert(rejection == MalformedHeaderRejection("bearer token", "malformed token=fakeToken"))
          }
        Get(s"/isCredentialActive?accessKey=access&sessionToken=session")
          .addHeader(RawHeader("Authorization", "fakeToken")) ~> Route.seal(testRoute) ~> check {
            assert(status == StatusCodes.BadRequest)
          }
      }

      "check credential and return status forbidden because the bearerToken is invalid" in {
        Get(s"/isCredentialActive?accessKey=access&sessionToken=session")
          .addHeader(RawHeader("Authorization", generateBearerToken("invalid"))) ~> testRoute ~> check {
            assert(rejection == AuthorizationFailedRejection)
          }
        Get(s"/isCredentialActive?accessKey=access&sessionToken=session")
          .addHeader(RawHeader("Authorization", generateBearerToken("invalid"))) ~> Route.seal(testRoute) ~> check {
            assert(status == StatusCodes.Forbidden)
          }
      }

      "check credential and return status bad request because the accessKey contains non-alphanumeric characters" in {
        Get(s"/isCredentialActive?accessKey=access-key!with@special*characters&sessionToken=session")
          .addHeader(RawHeader("Authorization", generateBearerToken())) ~> testRoute ~> check {
            assert(status == StatusCodes.Forbidden)
          }
      }

      "check credential and return status bad request because the sessionToken contains non-alphanumeric characters" in {
        Get(s"/isCredentialActive?accessKey=access&sessionToken=session!with@special*characters")
          .addHeader(RawHeader("Authorization", generateBearerToken())) ~> testRoute ~> check {
            assert(status == StatusCodes.Forbidden)
          }
      }

    }
  }
}

