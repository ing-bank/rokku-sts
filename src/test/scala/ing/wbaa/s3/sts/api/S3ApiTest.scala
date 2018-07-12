package ing.wbaa.s3.sts.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{ MissingQueryParamRejection, Route }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{ Matchers, WordSpec }

class S3ApiTest extends WordSpec with Matchers with ScalatestRouteTest {

  val s3Routs: Route = new S3Api().routes

  "S3 api" should {
    "return rejection because missing the Action parameter" in {
      Get("/") ~> s3Routs ~> check {
        rejection shouldEqual MissingQueryParamRejection("Action")
      }
    }

    "return an assume role" in {
      Get("/?Action=AssumeRoleWithWebIdentity") ~> s3Routs ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "return a session token" in {
      Get("/?Action=GetSessionToken") ~> s3Routs ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "return a bad request because the action in unknown" in {
      Get("/?Action=unknownAction") ~> s3Routs ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }
}

