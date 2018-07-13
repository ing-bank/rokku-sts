package ing.wbaa.gargoyle.sts.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{ MissingQueryParamRejection, Route }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ing.wbaa.gargoyle.sts.service.{ AssumeRoleWithWebIdentityResponse, GetSessionTokenResponse, TokenServiceImpl }
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ Matchers, WordSpec }

class S3ApiTest extends WordSpec with Matchers with MockFactory with ScalatestRouteTest {

  def s3Routes: Route = {
    val tokenService = stub[TokenServiceImpl]
    tokenService.getAssumeRoleWithWebIdentity _ when (*, *, *, 1000000000) returns None
    tokenService.getAssumeRoleWithWebIdentity _ when (*, *, *, *) returns Some(AssumeRoleWithWebIdentityResponse())
    tokenService.getSessionToken _ when 1000000000 returns None
    tokenService.getSessionToken _ when * returns Some(GetSessionTokenResponse())
    new S3Api(tokenService).routes
  }

  "S3 api" should {
    "return rejection because missing the Action parameter" in {
      Get("/") ~> s3Routes ~> check {
        rejection shouldEqual MissingQueryParamRejection("Action")
      }
    }

    "return forbidden because the DurationSeconds parameter is to big" in {
      Get("/?Action=AssumeRoleWithWebIdentity&DurationSeconds=1000000000&RoleSessionName=app1&RoleArn=arn:aws:iam::123456789012:role/FederatedWebIdentityRole&RoleSessionName=app1&WebIdentityToken=Atza%7CIQ") ~> s3Routes ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "return rejection because missing the RoleSessionName parameter" in {
      Get("/?Action=AssumeRoleWithWebIdentity&DurationSeconds=3600&ProviderId=testRrovider.com&RoleArn=arn:aws:iam::123456789012:role/FederatedWebIdentityRole&WebIdentityToken=Atza%7CIQ") ~> s3Routes ~> check {
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
      Get("/?Action=AssumeRoleWithWebIdentity&DurationSeconds=3600&ProviderId=testRrovider.com&RoleSessionName=app1&RoleArn=arn:aws:iam::123456789012:role/FederatedWebIdentityRole&WebIdentityToken=Atza%7CIQ") ~> s3Routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "return a session token" in {
      Get("/?Action=GetSessionToken") ~> s3Routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "return forbidden becuse the DurationSeconds is to big" in {
      Get("/?Action=GetSessionToken&DurationSeconds=1000000000") ~> s3Routes ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "return a bad request because the action in unknown" in {
      Get("/?Action=unknownAction") ~> s3Routes ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }
}

