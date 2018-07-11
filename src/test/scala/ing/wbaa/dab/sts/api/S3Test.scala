package ing.wbaa.dab.sts.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{ Matchers, WordSpec }

class S3Test extends WordSpec with Matchers with ScalatestRouteTest {

  val s3Routs: Route = new S3().routes

  "S3 api" should {
    "request for a credential is rejected because lack of authentication" in {
      Get("/") ~> s3Routs ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

  }
}

