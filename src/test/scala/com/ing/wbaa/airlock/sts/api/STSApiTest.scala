package com.ing.wbaa.airlock.sts.api

import java.time.Instant

import akka.http.scaladsl.model.headers.Cookie
import akka.http.scaladsl.model.{ FormData, StatusCodes }
import akka.http.scaladsl.server.{ AuthorizationFailedRejection, MissingFormFieldRejection, MissingQueryParamRejection, Route }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.ing.wbaa.airlock.sts.data
import com.ing.wbaa.airlock.sts.data._
import com.ing.wbaa.airlock.sts.data.aws._
import org.scalatest.{ DiagrammedAssertions, WordSpec }

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.xml.NodeSeq

class STSApiTest extends WordSpec with DiagrammedAssertions with ScalatestRouteTest {

  class MockStsApi extends STSApi {

    override protected[this] def getSessionTokenResponseToXML(awsCredentialWithToken: AwsCredentialWithToken): NodeSeq =
      <getSessionToken></getSessionToken>

    override def assumeRoleWithWebIdentityResponseToXML(awsCredentialWithToken: AwsCredentialWithToken, roleArn: AwsRoleArn, roleSessionName: String, keycloakTokenId: AuthenticationTokenId): NodeSeq = {
      <assumeRoleWithWebIdentity></assumeRoleWithWebIdentity>
    }

    override def verifyAuthenticationToken(token: BearerToken): Option[AuthenticationUserInfo] =
      token.value match {
        case "valid" => Some(data.AuthenticationUserInfo(UserName("name"), Set(UserGroup("testgroup")), AuthenticationTokenId("token")))
        case _       => None
      }

    override protected[this] def getAwsCredentialWithToken(userName: UserName, duration: Option[Duration], assumedGroup: Option[UserAssumedGroup]): Future[AwsCredentialWithToken] = {
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
  }.stsRoutes

  val validOAuth2TokenHeader: RequestTransformer = addHeader("Authorization", "Bearer valid")
  val validOAuth2TokenCookie: RequestTransformer = addHeader(Cookie("X-Authorization-Token", "valid"))
  val invalidOAuth2TokenHeader: RequestTransformer = addHeader("Authorization", "Bearer invalid")
  val invalidOAuth2TokenCookie: RequestTransformer = addHeader(Cookie("X-Authorization-Token", "invalid"))

  val actionAssumeRoleWithWebIdentity = "?Action=AssumeRoleWithWebIdentity"
  val actionGetSessionToken = "?Action=GetSessionToken"
  val durationQuery = "&DurationSeconds=3600"
  val roleNameSessionQuery = "&RoleSessionName=app1"
  val arnQuery = "&RoleArn=arn:aws:iam::123456789012:role/testgroup"
  val webIdentityTokenQuery = "&WebIdentityToken=Atza%7CIQ"
  val providerIdQuery = "&ProviderId=testProvider.com"
  val tokenCodeQuery = "&TokenCode=sdfdsfgg"

  "STS api the GET method" should {

    "return an assume role - valid token in webIdentityTokenQuery parm" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$providerIdQuery$roleNameSessionQuery$arnQuery&WebIdentityToken=valid") ~>
        s3Routes ~> check {
          assert(status == StatusCodes.OK)
          assert(responseAs[String] == "<assumeRoleWithWebIdentity></assumeRoleWithWebIdentity>")
        }
    }

    "return an assume role - valid token in the header" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$providerIdQuery$roleNameSessionQuery$arnQuery$webIdentityTokenQuery") ~>
        validOAuth2TokenHeader ~> s3Routes ~> check {
          assert(status == StatusCodes.OK)
          assert(responseAs[String] == "<assumeRoleWithWebIdentity></assumeRoleWithWebIdentity>")
        }
    }

    "return an assume role - valid token in the cookie" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$providerIdQuery$roleNameSessionQuery$arnQuery$webIdentityTokenQuery") ~>
        validOAuth2TokenCookie ~> s3Routes ~> check {
          assert(status == StatusCodes.OK)
          assert(responseAs[String] == "<assumeRoleWithWebIdentity></assumeRoleWithWebIdentity>")
        }
    }

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

    "return forbidden because getAssumeRoleWithWebIdentity return None" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$roleNameSessionQuery&RoleArn=arn:aws:iam::123456789012:role/invalidrole$roleNameSessionQuery$webIdentityTokenQuery") ~>
        validOAuth2TokenHeader ~> s3Routes ~> check {
          status == StatusCodes.Forbidden
        }
    }

    "return rejection because missing the RoleSessionName parameter" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$providerIdQuery$arnQuery$webIdentityTokenQuery") ~>
        s3Routes ~> check {
          assert(rejections.contains(MissingQueryParamRejection("RoleSessionName")))
        }
    }

    "return rejection because missing the WebIdentityToken parameter" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$roleNameSessionQuery$arnQuery$roleNameSessionQuery") ~> s3Routes ~> check {
        assert(rejections.contains(MissingQueryParamRejection("WebIdentityToken")))
      }
    }

    "return rejection because missing the RoleArn parameter" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$roleNameSessionQuery$roleNameSessionQuery$webIdentityTokenQuery") ~> s3Routes ~> check {
        assert(rejections.contains(MissingQueryParamRejection("RoleArn")))
      }
    }

    "return rejection because invalid token in param " in {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$roleNameSessionQuery$arnQuery$roleNameSessionQuery$webIdentityTokenQuery") ~>
        s3Routes ~> check {
          assert(rejection == AuthorizationFailedRejection)
        }
    }

    "return rejection because invalid token in the header" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$roleNameSessionQuery$arnQuery$roleNameSessionQuery$webIdentityTokenQuery") ~>
        invalidOAuth2TokenHeader ~> s3Routes ~> check {
          assert(rejection == AuthorizationFailedRejection)
        }
    }

    "return rejection because invalid token in the cookie" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$roleNameSessionQuery$arnQuery$roleNameSessionQuery$webIdentityTokenQuery") ~>
        invalidOAuth2TokenCookie ~> s3Routes ~> check {
          assert(rejection == AuthorizationFailedRejection)
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
      }.toMap
  }

  "STS api the POST method" should {
    "return an assume role" in {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, roleNameSessionQuery,
        arnQuery, providerIdQuery, roleNameSessionQuery) + ("WebIdentityToken" -> "valid"))) ~>
        s3Routes ~> check {
          assert(status == StatusCodes.OK)
        }
    }

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

    "return rejection because missing the RoleSessionName parameter" in {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, providerIdQuery, durationQuery, arnQuery, webIdentityTokenQuery))) ~>
        s3Routes ~> check {
          assert(rejections.contains(MissingFormFieldRejection("RoleSessionName")))
        }
    }

    "return rejection because missing the WebIdentityToken parameter" in {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, roleNameSessionQuery,
        arnQuery, providerIdQuery, roleNameSessionQuery))) ~>
        s3Routes ~> check {
          assert(rejections.contains(MissingFormFieldRejection("WebIdentityToken")))
        }
    }

    "return rejection because missing the RoleArn parameter" in {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, roleNameSessionQuery,
        providerIdQuery, roleNameSessionQuery, webIdentityTokenQuery))) ~>
        s3Routes ~> check {
          assert(rejections.contains(MissingFormFieldRejection("RoleArn")))
        }
    }

    "return rejection because verifyToken failed" in {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, roleNameSessionQuery,
        arnQuery, providerIdQuery, roleNameSessionQuery, webIdentityTokenQuery))) ~>
        s3Routes ~> check {
          assert(rejections.contains(AuthorizationFailedRejection))
        }
    }

    "return a session token with 1h expiration time because valid credentials" in {
      Post("/", FormData(queryToFormData(actionGetSessionToken, durationQuery))) ~> validOAuth2TokenHeader ~> s3RoutesWithExpirationTime ~> check {
        assert(status == StatusCodes.OK)
        assert(responseAs[String] == "<getSessionToken><Expiration>1970-01-01T01:00:00Z</Expiration></getSessionToken>")
      }
    }
  }
}

