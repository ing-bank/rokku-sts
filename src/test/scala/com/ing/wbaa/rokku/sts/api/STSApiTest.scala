package com.ing.wbaa.rokku.sts.api

import java.time.Instant

import akka.http.scaladsl.model.headers.Cookie
import akka.http.scaladsl.model.{ FormData, StatusCodes }
import akka.http.scaladsl.server.{ AuthorizationFailedRejection, MissingFormFieldRejection, MissingQueryParamRejection, Route }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.ing.wbaa.rokku.sts.data
import com.ing.wbaa.rokku.sts.data._
import com.ing.wbaa.rokku.sts.data.aws._
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.xml.NodeSeq

class STSApiTest extends AnyWordSpec with Diagrams with ScalatestRouteTest {

  class MockStsApi extends STSApi {

    override protected[this] def getSessionTokenResponseToXML(awsCredentialWithToken: AwsCredentialWithToken): NodeSeq =
      <getSessionToken></getSessionToken>

    override protected[this] def assumeRoleResponseToXML(
        awsCredentialWithToken: AwsCredentialWithToken,
        roleArn: AwsRoleArn,
        roleSessionName: String,
        keycloakTokenId: AuthenticationTokenId
    ): NodeSeq = <assumeRole></assumeRole>

    override def verifyAuthenticationToken(token: BearerToken): Option[AuthenticationUserInfo] =
      token.value match {
        case "valid" => Some(data.AuthenticationUserInfo(UserName("name"), Set(UserGroup("testgroup")), AuthenticationTokenId("token"), Set(UserAssumeRole("testrole"))))
        case _       => None
      }

    override protected[this] def getAwsCredentialWithToken(userName: UserName, groups: Set[UserGroup], duration: Option[Duration]): Future[AwsCredentialWithToken] = {
      Future.successful(AwsCredentialWithToken(
        AwsCredential(
          AwsAccessKey("accesskey"),
          AwsSecretKey("secretkey")
        ),
        AwsSession(
          AwsSessionToken("token"),
          AwsSessionTokenExpiration(Instant.ofEpochMilli(duration.getOrElse(1.second).toMillis))
        )
      ))
    }

    override protected[this] def getAwsCredentialWithToken(userName: UserName, userGroups: Set[UserGroup], role: UserAssumeRole, duration: Option[Duration]): Future[AwsCredentialWithToken] = {
      Future.successful(AwsCredentialWithToken(
        AwsCredential(
          AwsAccessKey("accesskey"),
          AwsSecretKey("secretkey")
        ),
        AwsSession(
          AwsSessionToken("token"),
          AwsSessionTokenExpiration(Instant.ofEpochMilli(duration.getOrElse(1.second).toMillis))
        )
      ))
    }
  }

  private val s3Routes: Route = new MockStsApi().stsRoutes
  private val s3RoutesWithExpirationTime: Route = new MockStsApi() {
    override protected[this] def getSessionTokenResponseToXML(awsCredentialWithToken: AwsCredentialWithToken): NodeSeq =
      <getSessionToken><Expiration>{ awsCredentialWithToken.session.expiration.value }</Expiration></getSessionToken>
    override protected[this] def assumeRoleResponseToXML(
        awsCredentialWithToken: AwsCredentialWithToken,
        roleArn: AwsRoleArn,
        roleSessionName: String,
        keycloakTokenId: AuthenticationTokenId
    ): NodeSeq = <assumeRole><Expiration>{ awsCredentialWithToken.session.expiration.value }</Expiration></assumeRole>
  }.stsRoutes

  val validOAuth2TokenHeader: RequestTransformer = addHeader("Authorization", "Bearer valid")
  val validOAuth2TokenCookie: RequestTransformer = addHeader(Cookie("X-Authorization-Token", "valid"))
  val invalidOAuth2TokenHeader: RequestTransformer = addHeader("Authorization", "Bearer invalid")
  val invalidOAuth2TokenCookie: RequestTransformer = addHeader(Cookie("X-Authorization-Token", "invalid"))

  val actionGetSessionToken = "?Action=GetSessionToken"
  val actionAssumeRole = "?Action=AssumeRole"
  val durationQuery = "&DurationSeconds=3600"
  val roleNameSessionQuery = "&RoleSessionName=app1"
  val arnQuery = "&RoleArn=arn:aws:iam::123456789012:role/testrole"
  val webIdentityTokenQuery = "&WebIdentityToken=Atza%7CIQ"
  val providerIdQuery = "&ProviderId=testProvider.com"
  val tokenCodeQuery = "&TokenCode=sdfdsfgg"

  "STS api the GET method for GetSessionToken" should {

    "return rejection because missing the Action parameter" in {
      Get(s"/") ~> s3Routes ~> check {
        assert(rejections.contains(MissingQueryParamRejection("Action")))
      }
    }

    "return a bad request because the action in unknown" in {
      Get(s"/?Action=unknownAction") ~> s3Routes ~> check {
        assert(status == StatusCodes.BadRequest)
      }
    }

    "return a session token because valid credentials" in {
      Get(s"/$actionGetSessionToken") ~> validOAuth2TokenHeader ~> s3Routes ~> check {
        assert(status == StatusCodes.OK)
        assert(responseAs[String] == "<getSessionToken></getSessionToken>")
      }
    }

    "return a session token with 1h expiration time because valid credentials" in {
      Get(s"/$actionGetSessionToken$durationQuery") ~> validOAuth2TokenHeader ~> s3RoutesWithExpirationTime ~> check {
        assert(status == StatusCodes.OK)
        assert(responseAs[String] == "<getSessionToken><Expiration>1970-01-01T01:00:00Z</Expiration></getSessionToken>")
      }
    }

    "return rejection because invalid credentials" in {
      Get(s"/$actionGetSessionToken") ~> invalidOAuth2TokenHeader ~> s3Routes ~> check {
        assert(rejections.contains(AuthorizationFailedRejection))
      }
    }

    "return rejection because no credentials" in {
      Get(s"/$actionGetSessionToken") ~> s3Routes ~> check {
        assert(rejections.contains(AuthorizationFailedRejection))
      }
    }

  }

  def queryToFormData(queries: String*): Map[String, String] = {
    queries.map(_.substring(1).split("="))
      .map {
        case Array(k, v) => (k, v)
        case _           => ("", "")
      }.toMap
  }

  "STS api the POST method for GetSessionToken" should {
    "return rejection because missing the Action parameter" in {
      Post("/") ~> s3Routes ~> check {
        assert(rejections.contains(MissingQueryParamRejection("Action")))
      }
    }

    "return a bad request because the action in unknown" in {
      Post("/", FormData("Action" -> "unknownAction")) ~> s3Routes ~> check {
        assert(status == StatusCodes.BadRequest)
      }
    }

    "return a session token with 1h expiration time because valid credentials" in {
      Post("/", FormData(queryToFormData(actionGetSessionToken, durationQuery))) ~> validOAuth2TokenHeader ~> s3RoutesWithExpirationTime ~> check {
        assert(status == StatusCodes.OK)
        assert(responseAs[String] == "<getSessionToken><Expiration>1970-01-01T01:00:00Z</Expiration></getSessionToken>")
      }
    }
  }

  "STS api the GET method for assumeRole" should {

    "return a session token because credentials are valid" in {
      Get(s"/$actionAssumeRole$arnQuery$roleNameSessionQuery") ~> validOAuth2TokenHeader ~> s3Routes ~> check {
        assert(status == StatusCodes.OK)
        assert(responseAs[String] == "<assumeRole></assumeRole>")
      }
    }

    "return a session token with 1h expiration time because credentials are valid" in {
      Get(s"/$actionAssumeRole$durationQuery$arnQuery$roleNameSessionQuery") ~> validOAuth2TokenHeader ~> s3RoutesWithExpirationTime ~> check {
        assert(status == StatusCodes.OK)
        assert(responseAs[String] == "<assumeRole><Expiration>1970-01-01T01:00:00Z</Expiration></assumeRole>")
      }
    }

    "return rejection because invalid credentials" in {
      Get(s"/$actionAssumeRole$arnQuery$roleNameSessionQuery") ~> invalidOAuth2TokenHeader ~> s3Routes ~> check {
        assert(rejections.contains(AuthorizationFailedRejection))
      }
    }

    "return rejection because no arn parameter" in {
      Get(s"/$actionAssumeRole$roleNameSessionQuery") ~> invalidOAuth2TokenHeader ~> s3Routes ~> check {
        assert(rejections.contains(MissingQueryParamRejection("RoleArn")))
      }
    }

    "return rejection because no roleSessionName parameter" in {
      Get(s"/$actionAssumeRole$arnQuery") ~> invalidOAuth2TokenHeader ~> s3Routes ~> check {
        assert(rejections.contains(MissingQueryParamRejection("RoleSessionName")))
      }
    }

    "return rejection because no credentials" in {
      Get(s"/$actionAssumeRole$arnQuery$roleNameSessionQuery") ~> s3Routes ~> check {
        assert(rejections.contains(AuthorizationFailedRejection))
      }
    }
  }

  "STS api the POST method for assumeRole" should {

    "return a session token because credentials are valid" in {
      Post("/", FormData(queryToFormData(actionAssumeRole, arnQuery, roleNameSessionQuery, durationQuery))) ~> validOAuth2TokenHeader ~> s3Routes ~> check {
        assert(status == StatusCodes.OK)
        assert(responseAs[String] == "<assumeRole></assumeRole>")
      }
    }

    "return rejection because invalid credentials" in {
      Post("/", FormData(queryToFormData(actionAssumeRole, arnQuery, roleNameSessionQuery, durationQuery))) ~> invalidOAuth2TokenHeader ~> s3Routes ~> check {
        assert(rejections.contains(AuthorizationFailedRejection))
      }
    }

    "return rejection because no arn parameter" in {
      Post("/", FormData(queryToFormData(actionAssumeRole, roleNameSessionQuery, durationQuery))) ~> invalidOAuth2TokenHeader ~> s3Routes ~> check {
        assert(rejections.contains(MissingFormFieldRejection("RoleArn")))
      }
    }

    "return rejection because no roleSessionName parameter" in {
      Post("/", FormData(queryToFormData(actionAssumeRole, arnQuery))) ~> invalidOAuth2TokenHeader ~> s3Routes ~> check {
        assert(rejections.contains(MissingFormFieldRejection("RoleSessionName")))
      }
    }

    "return rejection because no credentials" in {
      Post("/", FormData(queryToFormData(actionAssumeRole, arnQuery, roleNameSessionQuery, durationQuery))) ~> s3Routes ~> check {
        assert(rejections.contains(AuthorizationFailedRejection))
      }
    }

    "return a session token with 1h expiration time because valid credentials" in {
      Post("/", FormData(queryToFormData(actionAssumeRole, arnQuery, roleNameSessionQuery, durationQuery))) ~> validOAuth2TokenHeader ~> s3RoutesWithExpirationTime ~> check {
        assert(status == StatusCodes.OK)
        assert(responseAs[String] == "<assumeRole><Expiration>1970-01-01T01:00:00Z</Expiration></assumeRole>")
      }
    }
  }
}

