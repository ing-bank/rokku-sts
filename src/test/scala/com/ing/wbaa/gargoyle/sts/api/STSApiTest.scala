package com.ing.wbaa.gargoyle.sts.api

import akka.http.scaladsl.model.headers.Cookie
import akka.http.scaladsl.model.{ FormData, StatusCodes }
import akka.http.scaladsl.server.{ AuthorizationFailedRejection, MissingFormFieldRejection, MissingQueryParamRejection, Route }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.ing.wbaa.gargoyle.sts.oauth.{ BearerToken, OAuth2TokenVerifier, VerifiedToken }
import com.ing.wbaa.gargoyle.sts.service.{ AssumeRoleWithWebIdentityResponse, CredentialsResponse, TokenService }
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.Future

class STSApiTest extends WordSpec with Matchers with MockFactory with ScalatestRouteTest {

  import com.ing.wbaa.gargoyle._

  def s3Routes: Route = {
    new STSApi() {
      val tokenService: TokenService = stub[TokenService]
      tokenService.getAssumeRoleWithWebIdentity _ when (*, *, *, 1000000000) returns Future.successful(None)
      tokenService.getAssumeRoleWithWebIdentity _ when (*, *, *, *) returns Future.successful(Some(assumeRoleWithWebIdentityResponse))
      tokenService.getSessionToken _ when (*, 1000000000) returns Future.successful(None)
      tokenService.getSessionToken _ when (*, *) returns Future.successful(Some(credentialsResponse))
      val oAuth2TokenVerifier: OAuth2TokenVerifier = stub[OAuth2TokenVerifier]
      oAuth2TokenVerifier.verifyToken _ when BearerToken("valid") returns
        Future.successful(VerifiedToken("token", "id", "name", "username", "email", Seq.empty, 0))
      oAuth2TokenVerifier.verifyToken _ when * returns Future.failed(new Exception("invalid token"))

      override def getAssumeRoleWithWebIdentity(
          roleArn: String,
          roleSessionName: String,
          token: VerifiedToken,
          durationSeconds: Int): Future[Option[AssumeRoleWithWebIdentityResponse]] =
        tokenService.getAssumeRoleWithWebIdentity(roleArn, roleSessionName, verifiedToken, durationSeconds)

      override def getSessionToken(token: VerifiedToken, durationSeconds: Int): Future[Option[CredentialsResponse]] =
        tokenService.getSessionToken(verifiedToken, durationSeconds)

      override def verifyToken(token: BearerToken): Future[VerifiedToken] = oAuth2TokenVerifier.verifyToken(token)
    }.stsRoutes
  }

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

    "return forbidden because the DurationSeconds parameter is to big" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$roleNameSessionQuery$arnQuery$roleNameSessionQuery$webIdentityTokenQuery&DurationSeconds=1000000000") ~>
        validOAuth2TokenHeader ~> s3Routes ~> check {
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

    "return an assume role" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$providerIdQuery$roleNameSessionQuery$arnQuery$webIdentityTokenQuery") ~>
        validOAuth2TokenHeader ~> s3Routes ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[String] shouldEqual
            """<AssumeRoleWithWebIdentityResponse>
            |      <AssumeRoleWithWebIdentityResult>
            |        <SubjectFromWebIdentityToken>amzn1.account.AF6RHO7KZU5XRVQJGXK6HB56KR2A</SubjectFromWebIdentityToken>
            |        <Audience>client.5498841531868486423.1548@apps.example.com</Audience>
            |        <AssumedRoleUser>
            |      <Arn>arn:aws:sts::123456789012:assumed-role/FederatedWebIdentityRole/app1</Arn>
            |      <AssumedRoleId>AROACLKWSDQRAOEXAMPLE:app1</AssumedRoleId>
            |    </AssumedRoleUser>
            |        <Credentials>
            |      <SessionToken>okSessionToken</SessionToken>
            |      <SecretAccessKey>secretKey</SecretAccessKey>
            |      <Expiration>1970-01-01T00:00:03.601Z</Expiration>
            |      <AccessKeyId>okAccessKey</AccessKeyId>
            |    </Credentials>
            |        <Provider>ing.wbaa</Provider>
            |      </AssumeRoleWithWebIdentityResult>
            |      <ResponseMetadata>
            |        <RequestId>ad4156e9-bce1-11e2-82e6-6b6efEXAMPLE</RequestId>
            |      </ResponseMetadata>
            |    </AssumeRoleWithWebIdentityResponse>""".stripMargin
        }
    }

    "for action AssumeRoleWithWebIdentity return rejection because invalid authentication in the header" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$roleNameSessionQuery$arnQuery$roleNameSessionQuery$webIdentityTokenQuery") ~>
        invalidOAuth2TokenHeader ~> s3Routes ~> check {
          rejection shouldEqual AuthorizationFailedRejection
        }
    }

    "for action AssumeRoleWithWebIdentity return rejection because invalid authentication in the cookie" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$roleNameSessionQuery$arnQuery$roleNameSessionQuery$webIdentityTokenQuery") ~>
        invalidOAuth2TokenCookie ~> s3Routes ~> check {
          rejection shouldEqual AuthorizationFailedRejection
        }
    }

    "for action AssumeRoleWithWebIdentity return rejection because invalid credential in the WebIdentityToken param" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$roleNameSessionQuery$arnQuery$roleNameSessionQuery$webIdentityTokenQuery") ~>
        s3Routes ~> check {
          rejection shouldEqual AuthorizationFailedRejection
        }
    }

    "return an assume role because valid credential are in the WebIdentityToken param" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$providerIdQuery$roleNameSessionQuery$arnQuery&WebIdentityToken=valid") ~>
        s3Routes ~> check {
          status shouldEqual StatusCodes.OK
        }
    }

    "return an assume role because valid credential are in the cookie" in {
      Get(s"/$actionAssumeRoleWithWebIdentity$providerIdQuery$roleNameSessionQuery$arnQuery$webIdentityTokenQuery") ~>
        validOAuth2TokenCookie ~> s3Routes ~> check {
          status shouldEqual StatusCodes.OK
        }
    }

    "return a session token because valid credential in the header" in {
      Get(s"/$actionGetSessionToken") ~> validOAuth2TokenHeader ~> s3Routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "return a session token because valid credential in the cookie" in {
      Get(s"/$actionGetSessionToken") ~> validOAuth2TokenCookie ~> s3Routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual
          """<GetSessionTokenResponse>
            |      <GetSessionTokenResult><Credentials>
            |      <SessionToken>okSessionToken</SessionToken>
            |      <SecretAccessKey>secretKey</SecretAccessKey>
            |      <Expiration>1970-01-01T00:00:03.601Z</Expiration>
            |      <AccessKeyId>okAccessKey</AccessKeyId>
            |    </Credentials></GetSessionTokenResult>
            |      <ResponseMetadata>
            |        <RequestId>58c5dbae-abef-11e0-8cfe-09039844ac7d</RequestId>
            |      </ResponseMetadata>
            |    </GetSessionTokenResponse>""".stripMargin
      }
    }

    "return forbidden because the DurationSeconds is to big" in {
      Get(s"/$actionGetSessionToken&DurationSeconds=1000000000") ~> validOAuth2TokenHeader ~> s3Routes ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "for action GetSessionToken return rejection because invalid authentication in the cookie" in {
      Get(s"/$actionGetSessionToken&DurationSeconds=1000") ~> invalidOAuth2TokenCookie ~> s3Routes ~> check {
        rejection shouldEqual AuthorizationFailedRejection
      }
    }

    "for action GetSessionToken return rejection because bad authentication in the header" in {
      Get(s"/$actionGetSessionToken&DurationSeconds=1000") ~> invalidOAuth2TokenHeader ~> s3Routes ~> check {
        rejection shouldEqual AuthorizationFailedRejection
      }
    }

    "for action GetSessionToken return rejection because no authentication token" in {
      Get(s"/$actionGetSessionToken&DurationSeconds=1000") ~> s3Routes ~> check {
        rejection shouldEqual AuthorizationFailedRejection
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

    "return forbidden because the DurationSeconds parameter is to big" in {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, roleNameSessionQuery,
        arnQuery, providerIdQuery, roleNameSessionQuery, webIdentityTokenQuery) + ("DurationSeconds" -> "1000000000"))) ~>
        validOAuth2TokenHeader ~> s3Routes ~> check {
          status shouldEqual StatusCodes.Forbidden
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

    "return an assume role" in {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, roleNameSessionQuery,
        arnQuery, providerIdQuery, roleNameSessionQuery, webIdentityTokenQuery))) ~>
        validOAuth2TokenHeader ~>
        s3Routes ~> check {
          status shouldEqual StatusCodes.OK
        }
    }

    "for action AssumeRoleWithWebIdentity return rejection because invalid authentication in the header" in {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, roleNameSessionQuery,
        arnQuery, providerIdQuery, roleNameSessionQuery, webIdentityTokenQuery))) ~>
        invalidOAuth2TokenHeader ~> s3Routes ~> check {
          rejections should contain atLeastOneElementOf List(AuthorizationFailedRejection)
        }
    }

    "for action AssumeRoleWithWebIdentity return rejection because invalid authentication in the cookie" in {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, roleNameSessionQuery,
        arnQuery, providerIdQuery, roleNameSessionQuery, webIdentityTokenQuery))) ~>
        invalidOAuth2TokenCookie ~> s3Routes ~> check {
          rejections should contain atLeastOneElementOf List(AuthorizationFailedRejection)
        }
    }

    "for action AssumeRoleWithWebIdentity return rejection because invalid credential in the WebIdentityToken param" in {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, roleNameSessionQuery,
        arnQuery, providerIdQuery, roleNameSessionQuery, webIdentityTokenQuery))) ~>
        s3Routes ~> check {
          rejections should contain atLeastOneElementOf List(AuthorizationFailedRejection)
        }
    }

    "return an assume role because valid credential are in the WebIdentityToken param" in {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, roleNameSessionQuery,
        arnQuery, providerIdQuery, roleNameSessionQuery) + ("WebIdentityToken" -> "valid"))) ~>
        s3Routes ~> check {
          status shouldEqual StatusCodes.OK
        }
    }

    "return an assume role because valid credential are in the cookie" in {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, roleNameSessionQuery,
        arnQuery, providerIdQuery, roleNameSessionQuery, webIdentityTokenQuery))) ~>
        validOAuth2TokenCookie ~> s3Routes ~> check {
          status shouldEqual StatusCodes.OK
        }
    }

    "return a session token because valid credential in the header" in {
      Post("/", FormData(queryToFormData(actionGetSessionToken))) ~> validOAuth2TokenHeader ~> s3Routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "return a session token because valid credential in the cookie" in {
      Post("/", FormData(queryToFormData(actionGetSessionToken))) ~> validOAuth2TokenCookie ~> s3Routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "return a session token because valid credential in the TokenCode" in {
      Post("/", FormData(queryToFormData(actionGetSessionToken, durationQuery) + ("TokenCode" -> "valid"))) ~> s3Routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "return forbidden because the DurationSeconds is to big" in {
      Post("/", FormData(queryToFormData(actionGetSessionToken) + ("DurationSeconds" -> "1000000000"))) ~> validOAuth2TokenHeader ~> s3Routes ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "return rejection because the TokenCode is invalid" in {
      Post("/", FormData(queryToFormData(actionGetSessionToken, tokenCodeQuery, durationQuery))) ~> s3Routes ~> check {
        rejections should contain atLeastOneElementOf List(AuthorizationFailedRejection)
      }
    }

    "for action GetSessionToken return rejection because invalid authentication in the cookie" in {
      Post("/", FormData(queryToFormData(actionGetSessionToken, durationQuery))) ~> invalidOAuth2TokenCookie ~> s3Routes ~> check {
        rejections should contain atLeastOneElementOf List(AuthorizationFailedRejection)
      }
    }

    "for action GetSessionToken return rejection because bad authentication in the header" in {
      Post("/", FormData(queryToFormData(actionGetSessionToken, durationQuery))) ~> invalidOAuth2TokenHeader ~> s3Routes ~> check {
        rejections should contain atLeastOneElementOf List(AuthorizationFailedRejection)
      }
    }

    "for action GetSessionToken return rejection because no authentication token" in {
      Post("/", FormData(queryToFormData(actionGetSessionToken, durationQuery))) ~> s3Routes ~> check {
        rejections should contain atLeastOneElementOf List(AuthorizationFailedRejection)
      }
    }
  }

}

