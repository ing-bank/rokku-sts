package com.ing.wbaa.gargoyle.sts.api

import akka.http.scaladsl.model.headers.Cookie
import akka.http.scaladsl.model.{ FormData, StatusCodes }
import akka.http.scaladsl.server.{ AuthorizationFailedRejection, MissingFormFieldRejection, MissingQueryParamRejection, Route }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.ing.wbaa.gargoyle.sts.oauth.{ BearerToken, VerifiedToken }
import com.ing.wbaa.gargoyle.sts.service.{ AssumeRoleWithWebIdentityResponse, CredentialsResponse }
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.Future
import scala.xml.NodeSeq

class STSApiTest extends WordSpec with Matchers with ScalatestRouteTest {

  import com.ing.wbaa.gargoyle._

  class MockStsApi extends STSApi {
    override def getAssumeRoleWithWebIdentity(
        roleArn: String,
        roleSessionName: String,
        token: VerifiedToken,
        durationSeconds: Int): Future[Option[AssumeRoleWithWebIdentityResponse]] =
      Future.successful(Some(assumeRoleWithWebIdentityResponse))

    override def getSessionToken(token: VerifiedToken, durationSeconds: Int): Future[Option[CredentialsResponse]] =
      Future.successful(Some(credentialsResponse))

    override protected[this] def getSessionTokenResponseToXML(credentials: CredentialsResponse): NodeSeq =
      <getSessionToken></getSessionToken>

    override protected[this] def assumeRoleWithWebIdentityResponseToXML(aRWWIResponse: AssumeRoleWithWebIdentityResponse): NodeSeq =
      <assumeRoleWithWebIdentity></assumeRoleWithWebIdentity>

    override def verifyToken(token: BearerToken): Option[VerifiedToken] =
      token.value match {
        case "valid" => Some(VerifiedToken("token", "id", "name", "username", "email", Seq.empty, 0))
        case _       => None
      }
  }

  private val s3Routes: Route = new MockStsApi().stsRoutes

  val validOAuth2TokenHeader: RequestTransformer = addHeader("Authorization", "Bearer valid")
  val validOAuth2TokenCookie: RequestTransformer = addHeader(Cookie("X-Authorization-Token", "valid"))
  val invalidOAuth2TokenHeader: RequestTransformer = addHeader("Authorization", "Bearer invalid")
  val invalidOAuth2TokenCookie: RequestTransformer = addHeader(Cookie("X-Authorization-Token", "invalid"))

  val actionAssumeRoleWithWebIdentity = "?Action=AssumeRoleWithWebIdentity"
  val actionGetSessionToken = "?Action=GetSessionToken"
  val durationQuery = "&DurationSeconds=3600"
  val roleNameSessionQuery = "&RoleSessionName=app1"
  val arnQuery = "&RoleArn=arn:aws:iam::123456789012:role/FederatedWebIdentityRole"
  val webIdentityTokenQuery = "&WebIdentityToken=Atza%7CIQ"
  val providerIdQuery = "&ProviderId=testProvider.com"
  val tokenCodeQuery = "&TokenCode=sdfdsfgg"

  "STS api the GET method" should {

    "return an assume role - valid token in webIdentityTokenQuery parm" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$providerIdQuery$roleNameSessionQuery$arnQuery&WebIdentityToken=valid") ~>
        s3Routes ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[String] shouldEqual "<assumeRoleWithWebIdentity></assumeRoleWithWebIdentity>"
        }
    }

    "return an assume role - valid token in the header" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$providerIdQuery$roleNameSessionQuery$arnQuery$webIdentityTokenQuery") ~>
        validOAuth2TokenHeader ~> s3Routes ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[String] shouldEqual "<assumeRoleWithWebIdentity></assumeRoleWithWebIdentity>"
        }
    }

    "return an assume role - valid token in the cookie" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$providerIdQuery$roleNameSessionQuery$arnQuery$webIdentityTokenQuery") ~>
        validOAuth2TokenCookie ~> s3Routes ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[String] shouldEqual "<assumeRoleWithWebIdentity></assumeRoleWithWebIdentity>"
        }
    }

    "return rejection because missing the Action parameter" in {
      Get(s"/") ~> s3Routes ~> check {
        rejections should contain atLeastOneElementOf List(MissingQueryParamRejection("Action"))
      }
    }

    "return a bad request because the action in unknown" in {
      Get(s"/?Action=unknownAction") ~> s3Routes ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "return forbidden because getAssumeRoleWithWebIdentity return None" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$roleNameSessionQuery$arnQuery$roleNameSessionQuery$webIdentityTokenQuery") ~>
        validOAuth2TokenHeader ~>
        new MockStsApi() {
          override def getAssumeRoleWithWebIdentity(
              roleArn: String,
              roleSessionName: String,
              token: VerifiedToken,
              durationSeconds: Int): Future[Option[AssumeRoleWithWebIdentityResponse]] =
            Future.successful(None)
        }.stsRoutes ~> check {
          status shouldEqual StatusCodes.Forbidden
        }
    }

    "return rejection because missing the RoleSessionName parameter" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$providerIdQuery$arnQuery$webIdentityTokenQuery") ~>
        s3Routes ~> check {
          rejections should contain atLeastOneElementOf List(MissingQueryParamRejection("RoleSessionName"))
        }
    }

    "return rejection because missing the WebIdentityToken parameter" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$roleNameSessionQuery$arnQuery$roleNameSessionQuery") ~> s3Routes ~> check {
        rejections should contain atLeastOneElementOf List(MissingQueryParamRejection("WebIdentityToken"))
      }
    }

    "return rejection because missing the RoleArn parameter" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$roleNameSessionQuery$roleNameSessionQuery$webIdentityTokenQuery") ~> s3Routes ~> check {
        rejections should contain atLeastOneElementOf List(MissingQueryParamRejection("RoleArn"))
      }
    }

    "return rejection because invalid token in param " in {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$roleNameSessionQuery$arnQuery$roleNameSessionQuery$webIdentityTokenQuery") ~>
        s3Routes ~> check {
          rejection shouldEqual AuthorizationFailedRejection
        }
    }

    "return rejection because invalid token in the header" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$roleNameSessionQuery$arnQuery$roleNameSessionQuery$webIdentityTokenQuery") ~>
        invalidOAuth2TokenHeader ~> s3Routes ~> check {
          rejection shouldEqual AuthorizationFailedRejection
        }
    }

    "return rejection because invalid token in the cookie" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$roleNameSessionQuery$arnQuery$roleNameSessionQuery$webIdentityTokenQuery") ~>
        invalidOAuth2TokenCookie ~> s3Routes ~> check {
          rejection shouldEqual AuthorizationFailedRejection
        }
    }

    "return a session token because valid credentials" in {
      Get(s"/$actionGetSessionToken") ~> validOAuth2TokenHeader ~> s3Routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "<getSessionToken></getSessionToken>"
      }
    }

    "return rejection because invalid credentials" in {
      Get(s"/$actionGetSessionToken") ~> invalidOAuth2TokenHeader ~> s3Routes ~> check {
        rejections should contain atLeastOneElementOf List(AuthorizationFailedRejection)
      }
    }

    "return forbidden because getSessionToken returns None" in {
      Get(s"/$actionGetSessionToken") ~> validOAuth2TokenHeader ~> new MockStsApi() {
        override def getSessionToken(token: VerifiedToken, durationSeconds: Int): Future[Option[CredentialsResponse]] =
          Future.successful(None)
      }.stsRoutes ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "return rejection because no credentials" in {
      Get(s"/$actionGetSessionToken") ~> s3Routes ~> check {
        rejections should contain atLeastOneElementOf List(AuthorizationFailedRejection)
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
          status shouldEqual StatusCodes.OK
        }
    }

    "return rejection because missing the Action parameter" in {
      Post("/") ~> s3Routes ~> check {
        rejections should contain atLeastOneElementOf List(MissingQueryParamRejection("Action"))
      }
    }

    "return a bad request because the action in unknown" in {
      Post("/", FormData("Action" -> "unknownAction")) ~> s3Routes ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "return rejection because missing the RoleSessionName parameter" in {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, providerIdQuery, durationQuery, arnQuery, webIdentityTokenQuery))) ~>
        s3Routes ~> check {
          rejections should contain atLeastOneElementOf List(MissingFormFieldRejection("RoleSessionName"))
        }
    }

    "return rejection because missing the WebIdentityToken parameter" in {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, roleNameSessionQuery,
        arnQuery, providerIdQuery, roleNameSessionQuery))) ~>
        s3Routes ~> check {
          rejections should contain atLeastOneElementOf List(MissingFormFieldRejection("WebIdentityToken"))
        }
    }

    "return rejection because missing the RoleArn parameter" in {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, roleNameSessionQuery,
        providerIdQuery, roleNameSessionQuery, webIdentityTokenQuery))) ~>
        s3Routes ~> check {
          rejections should contain atLeastOneElementOf List(MissingFormFieldRejection("RoleArn"))
        }
    }

    "return rejection because verifyToken failed" in {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, roleNameSessionQuery,
        arnQuery, providerIdQuery, roleNameSessionQuery, webIdentityTokenQuery))) ~>
        s3Routes ~> check {
          rejections should contain atLeastOneElementOf List(AuthorizationFailedRejection)
        }
    }
  }
}

