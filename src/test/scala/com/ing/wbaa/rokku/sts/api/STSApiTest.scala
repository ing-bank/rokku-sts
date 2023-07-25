package com.ing.wbaa.rokku.sts.api

import akka.actor.ActorSystem

import java.time.Instant
import akka.http.scaladsl.model.headers.Cookie
import akka.http.scaladsl.model.{ FormData, StatusCodes }
import akka.http.scaladsl.server.{ AuthorizationFailedRejection, MissingFormFieldRejection, MissingQueryParamRejection, Route }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.ing.wbaa.rokku.sts.config.StsSettings
import com.ing.wbaa.rokku.sts.data
import com.ing.wbaa.rokku.sts.data._
import com.ing.wbaa.rokku.sts.data.aws._
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.TimeUnit
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
        case "valid"    => Some(data.AuthenticationUserInfo(Username("name"), Set(UserGroup("testgroup")), AuthenticationTokenId("token"), Set(UserAssumeRole("testrole")), isNPA = false))
        case "validNPA" => Some(data.AuthenticationUserInfo(Username("name"), Set(UserGroup("testgroup")), AuthenticationTokenId("token"), Set(UserAssumeRole("testrole")), isNPA = true))
        case _          => None
      }

    override protected[this] def getAwsCredentialWithToken(userName: Username, groups: Set[UserGroup], duration: Duration): Future[AwsCredentialWithToken] = {
      Future.successful(AwsCredentialWithToken(
        AwsCredential(
          AwsAccessKey("accesskey"),
          AwsSecretKey("secretkey")
        ),
        AwsSession(
          AwsSessionToken("token"),
          AwsSessionTokenExpiration(Instant.ofEpochMilli(duration.toMillis))
        )
      ))
    }

    override protected[this] def getAwsCredentialWithToken(userName: Username, userGroups: Set[UserGroup], role: UserAssumeRole, duration: Duration): Future[AwsCredentialWithToken] = {
      Future.successful(AwsCredentialWithToken(
        AwsCredential(
          AwsAccessKey("accesskey"),
          AwsSecretKey("secretkey")
        ),
        AwsSession(
          AwsSessionToken("token"),
          AwsSessionTokenExpiration(Instant.ofEpochMilli(duration.toMillis))
        )
      ))
    }

    val testSystem: ActorSystem = ActorSystem.create("test-system")
    override protected[this] def stsSettings: StsSettings = new StsSettings(testSystem.settings.config) {
      override val defaultTokenSessionDuration: Duration = Duration(8, TimeUnit.HOURS)
      override val maxTokenSessionDuration: Duration = Duration(24, TimeUnit.HOURS)
      override val maxTokenSessionForNPADuration: Duration = Duration(10, TimeUnit.DAYS)
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
  val validNPAOAuth2TokenHeader: RequestTransformer = addHeader("Authorization", "Bearer validNPA")
  val validOAuth2TokenCookie: RequestTransformer = addHeader(Cookie("X-Authorization-Token", "valid"))
  val invalidOAuth2TokenHeader: RequestTransformer = addHeader("Authorization", "Bearer invalid")
  val invalidOAuth2TokenCookie: RequestTransformer = addHeader(Cookie("X-Authorization-Token", "invalid"))

  val actionGetSessionToken = "?Action=GetSessionToken"
  val actionAssumeRole = "?Action=AssumeRole"
  val duration1hQuery = "&DurationSeconds=3600"
  val duration48hQuery = "&DurationSeconds=172800"
  val duration20dQuery = "&DurationSeconds=1728000"
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
      Get(s"/$actionGetSessionToken$duration1hQuery") ~> validOAuth2TokenHeader ~> s3RoutesWithExpirationTime ~> check {
        assert(status == StatusCodes.OK)
        assert(responseAs[String] == "<getSessionToken><Expiration>1970-01-01T01:00:00Z</Expiration></getSessionToken>")
      }
    }

    "return max session token with 24h expiration time because longer time is not allowed for users" in {
      Get(s"/$actionGetSessionToken$duration48hQuery") ~> validOAuth2TokenHeader ~> s3RoutesWithExpirationTime ~> check {
        assert(status == StatusCodes.OK)
        assert(responseAs[String] == "<getSessionToken><Expiration>1970-01-02T00:00:00Z</Expiration></getSessionToken>")
      }
    }

    "return a session token with 48h expiration time because NPA can have longer time" in {
      Get(s"/$actionGetSessionToken$duration48hQuery") ~> validNPAOAuth2TokenHeader ~> s3RoutesWithExpirationTime ~> check {
        assert(status == StatusCodes.OK)
        assert(responseAs[String] == "<getSessionToken><Expiration>1970-01-03T00:00:00Z</Expiration></getSessionToken>")
      }
    }

    "return max session token with 10 days expiration time because longer time is not allowed for NPA" in {
      Get(s"/$actionGetSessionToken$duration20dQuery") ~> validNPAOAuth2TokenHeader ~> s3RoutesWithExpirationTime ~> check {
        assert(status == StatusCodes.OK)
        assert(responseAs[String] == "<getSessionToken><Expiration>1970-01-11T00:00:00Z</Expiration></getSessionToken>")
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
      Post("/", FormData(queryToFormData(actionGetSessionToken, duration1hQuery))) ~> validOAuth2TokenHeader ~> s3RoutesWithExpirationTime ~> check {
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
      Get(s"/$actionAssumeRole$duration1hQuery$arnQuery$roleNameSessionQuery") ~> validOAuth2TokenHeader ~> s3RoutesWithExpirationTime ~> check {
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
      Get(s"/$actionAssumeRole$roleNameSessionQuery") ~> validOAuth2TokenHeader ~> s3Routes ~> check {
        assert(rejections.contains(MissingQueryParamRejection("RoleArn")))
      }
    }

    "return rejection because no roleSessionName parameter" in {
      Get(s"/$actionAssumeRole$arnQuery") ~> validOAuth2TokenHeader ~> s3Routes ~> check {
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
      Post("/", FormData(queryToFormData(actionAssumeRole, arnQuery, roleNameSessionQuery, duration1hQuery))) ~> validOAuth2TokenHeader ~> s3Routes ~> check {
        assert(status == StatusCodes.OK)
        assert(responseAs[String] == "<assumeRole></assumeRole>")
      }
    }

    "return rejection because invalid credentials" in {
      Post("/", FormData(queryToFormData(actionAssumeRole, arnQuery, roleNameSessionQuery, duration1hQuery))) ~> invalidOAuth2TokenHeader ~> s3Routes ~> check {
        assert(rejections.contains(AuthorizationFailedRejection))
      }
    }

    "return rejection because no arn parameter" in {
      Post("/", FormData(queryToFormData(actionAssumeRole, roleNameSessionQuery, duration1hQuery))) ~> validOAuth2TokenHeader ~> s3Routes ~> check {
        assert(rejections.contains(MissingFormFieldRejection("RoleArn")))
      }
    }

    "return rejection because no roleSessionName parameter" in {
      Post("/", FormData(queryToFormData(actionAssumeRole, arnQuery))) ~> validOAuth2TokenHeader ~> s3Routes ~> check {
        assert(rejections.contains(MissingFormFieldRejection("RoleSessionName")))
      }
    }

    "return rejection because no credentials" in {
      Post("/", FormData(queryToFormData(actionAssumeRole, arnQuery, roleNameSessionQuery, duration1hQuery))) ~> s3Routes ~> check {
        assert(rejections.contains(AuthorizationFailedRejection))
      }
    }

    "return a session token with 1h expiration time because valid credentials" in {
      Post("/", FormData(queryToFormData(actionAssumeRole, arnQuery, roleNameSessionQuery, duration1hQuery))) ~> validOAuth2TokenHeader ~> s3RoutesWithExpirationTime ~> check {
        assert(status == StatusCodes.OK)
        assert(responseAs[String] == "<assumeRole><Expiration>1970-01-01T01:00:00Z</Expiration></assumeRole>")
      }
    }
  }
}

