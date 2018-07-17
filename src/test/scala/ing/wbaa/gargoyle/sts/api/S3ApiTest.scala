package ing.wbaa.gargoyle.sts.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Cookie
import akka.http.scaladsl.server.{ AuthorizationFailedRejection, MissingQueryParamRejection, Route }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ing.wbaa.gargoyle.sts.oauth.{ OAuth2TokenVerifier, VerifiedToken }
import ing.wbaa.gargoyle.sts.service.{ AssumeRoleWithWebIdentityResponse, GetSessionTokenResponse, TokenServiceImpl }
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.Future

class S3ApiTest extends WordSpec with Matchers with MockFactory with ScalatestRouteTest {

  def s3Routes: Route = {
    val tokenService = stub[TokenServiceImpl]
    tokenService.getAssumeRoleWithWebIdentity _ when (*, *, *, 1000000000) returns None
    tokenService.getAssumeRoleWithWebIdentity _ when (*, *, *, *) returns Some(AssumeRoleWithWebIdentityResponse())
    tokenService.getSessionToken _ when 1000000000 returns None
    tokenService.getSessionToken _ when * returns Some(GetSessionTokenResponse())

    val oAuth2TokenVerifier = stub[OAuth2TokenVerifier]
    oAuth2TokenVerifier.verifyToken _ when "valid" returns Future.successful(VerifiedToken("token", "id", "name", "username", "email", Seq.empty, 0))
    oAuth2TokenVerifier.verifyToken _ when * returns Future.failed(new Exception("invalid token"))
    new S3Api(oAuth2TokenVerifier, tokenService).routes
  }

  val validOAuth2TokenHeader: RequestTransformer = addHeader("Authorization", "Bearer valid")
  val validOAuth2TokenCookie: RequestTransformer = addHeader(Cookie("X-Authorization-Token", "valid"))
  val invalidOAuth2TokenHeader: RequestTransformer = addHeader("Authorization", "Bearer invalid")
  val invalidOAuth2TokenCookie: RequestTransformer = addHeader(Cookie("X-Authorization-Token", "invalid"))

  "S3 api" should {
    "return rejection because missing the Action parameter" in {
      Get("/") ~> s3Routes ~> check {
        rejection shouldEqual MissingQueryParamRejection("Action")
      }
    }

    "return a bad request because the action in unknown" in {
      Get("/?Action=unknownAction") ~> s3Routes ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "return forbidden because the DurationSeconds parameter is to big" in {
      Get("/?Action=AssumeRoleWithWebIdentity&DurationSeconds=1000000000&RoleSessionName=app1&RoleArn=arn:aws:iam::123456789012:role/FederatedWebIdentityRole&RoleSessionName=app1&WebIdentityToken=Atza%7CIQ") ~>
        validOAuth2TokenHeader ~> s3Routes ~> check {
          status shouldEqual StatusCodes.Forbidden
        }
    }

    "return rejection because missing the RoleSessionName parameter" in {
      Get("/?Action=AssumeRoleWithWebIdentity&DurationSeconds=3600&ProviderId=testRrovider.com&RoleArn=arn:aws:iam::123456789012:role/FederatedWebIdentityRole&WebIdentityToken=Atza%7CIQ") ~>
        s3Routes ~> check {
          rejection shouldEqual MissingQueryParamRejection("RoleSessionName")
        }
    }

    "return rejection because missing the WebIdentityToken parameter" in {
      Get("/?Action=AssumeRoleWithWebIdentity&DurationSeconds=3600&RoleSessionName=app1&RoleArn=arn:aws:iam::123456789012:role/FederatedWebIdentityRole&RoleSessionName=app1") ~> s3Routes ~> check {
        rejection shouldEqual MissingQueryParamRejection("WebIdentityToken")
      }
    }

    "return rejection because missing the RoleArn parameter" in {
      Get("/?Action=AssumeRoleWithWebIdentity&DurationSeconds=3600&RoleSessionName=app1&RoleSessionName=app1&WebIdentityToken=Atza%7CIQ") ~> s3Routes ~> check {
        rejection shouldEqual MissingQueryParamRejection("RoleArn")
      }
    }

    "return an assume role" in {
      Get("/?Action=AssumeRoleWithWebIdentity&DurationSeconds=3600&ProviderId=testRrovider.com&RoleSessionName=app1&RoleArn=arn:aws:iam::123456789012:role/FederatedWebIdentityRole&WebIdentityToken=Atza%7CIQ") ~>
        validOAuth2TokenHeader ~> s3Routes ~> check {
          status shouldEqual StatusCodes.OK
        }
    }

    "for action AssumeRoleWithWebIdentity return rejection because invalid authentication in the header" in {
      Get("/?Action=AssumeRoleWithWebIdentity&DurationSeconds=3600&RoleSessionName=app1&RoleArn=arn:aws:iam::123456789012:role/FederatedWebIdentityRole&RoleSessionName=app1&WebIdentityToken=123") ~>
        invalidOAuth2TokenHeader ~> s3Routes ~> check {
          rejection shouldEqual AuthorizationFailedRejection
        }
    }

    "for action AssumeRoleWithWebIdentity return rejection because invalid authentication in the cookie" in {
      Get("/?Action=AssumeRoleWithWebIdentity&DurationSeconds=3600&RoleSessionName=app1&RoleArn=arn:aws:iam::123456789012:role/FederatedWebIdentityRole&RoleSessionName=app1&WebIdentityToken=123") ~>
        invalidOAuth2TokenCookie ~> s3Routes ~> check {
          rejection shouldEqual AuthorizationFailedRejection
        }
    }

    "for action AssumeRoleWithWebIdentity return rejection because invalid credential in the WebIdentityToken param" in {
      Get("/?Action=AssumeRoleWithWebIdentity&DurationSeconds=3600&RoleSessionName=app1&RoleArn=arn:aws:iam::123456789012:role/FederatedWebIdentityRole&RoleSessionName=app1&WebIdentityToken=123") ~>
        s3Routes ~> check {
          rejection shouldEqual AuthorizationFailedRejection
        }
    }

    "return an assume role because valid credential are in the WebIdentityToken param" in {
      Get("/?Action=AssumeRoleWithWebIdentity&DurationSeconds=3600&ProviderId=testProvider&RoleSessionName=app1&RoleArn=arn:aws:iam::123456789012:role/FederatedWebIdentityRole&WebIdentityToken=valid") ~>
        s3Routes ~> check {
          status shouldEqual StatusCodes.OK
        }
    }

    "return an assume role because valid credential are in the cookie" in {
      Get("/?Action=AssumeRoleWithWebIdentity&DurationSeconds=3600&ProviderId=testRrovider.com&RoleSessionName=app1&RoleArn=arn:aws:iam::123456789012:role/FederatedWebIdentityRole&WebIdentityToken=valid") ~>
        validOAuth2TokenCookie ~> s3Routes ~> check {
          status shouldEqual StatusCodes.OK
        }
    }

    "return a session token because valid credential in the header" in {
      Get("/?Action=GetSessionToken") ~> validOAuth2TokenHeader ~> s3Routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "return a session token because valid credential in the cookie" in {
      Get("/?Action=GetSessionToken") ~> validOAuth2TokenCookie ~> s3Routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "return forbidden because the DurationSeconds is to big" in {
      Get("/?Action=GetSessionToken&DurationSeconds=1000000000") ~> validOAuth2TokenHeader ~> s3Routes ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "for action GetSessionToken return rejection because invalid authentication in the cookie" in {
      Get("/?Action=GetSessionToken&DurationSeconds=1000") ~> invalidOAuth2TokenCookie ~> s3Routes ~> check {
        rejection shouldEqual AuthorizationFailedRejection
      }
    }

    "for action GetSessionToken return rejection because bad authentication in the header" in {
      Get("/?Action=GetSessionToken&DurationSeconds=1000") ~> invalidOAuth2TokenHeader ~> s3Routes ~> check {
        rejection shouldEqual AuthorizationFailedRejection
      }
    }

    "for action GetSessionToken return rejection because no authentication token" in {
      Get("/?Action=GetSessionToken&DurationSeconds=1000") ~> s3Routes ~> check {
        rejection shouldEqual AuthorizationFailedRejection
      }
    }
  }
}

