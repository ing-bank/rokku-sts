package com.ing.wbaa.rokku.sts.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.BeforeAndAfterAll
import org.scalatest.diagrams.Diagrams
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Future

class ServerApiTest extends AnyWordSpec
  with Matchers
  with Diagrams
  with ScalatestRouteTest
  with BeforeAndAfterAll {

  private[this] val serverRoutes = new ServerApi {
    override protected[this] def checkDbConnection(): Future[Unit] = Future.successful(())
  }.serverRoutes

  private[this] val faultyServerRoutes = new ServerApi {
    override protected[this] def checkDbConnection(): Future[Unit] = Future.failed(new Exception("Faulty connection"))
  }.serverRoutes

  "Server api" should {
    "check healthCheck" that {

      "returns 204 when the connection is stable" in {
        Get("/healthCheck") ~> serverRoutes ~> check {
          status shouldEqual StatusCodes.NoContent
        }
      }

      "returns 500 when the connection is faulty" in {
        Get("/healthCheck") ~> faultyServerRoutes ~> check {
          status shouldEqual StatusCodes.InternalServerError
        }
      }

    }
  }
}
