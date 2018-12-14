package com.ing.wbaa.airlock.sts.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.util.{ Failure, Success }

trait ServerApi extends LazyLogging {

  protected[this] def checkDbConnection(): Future[Unit]

  val serverRoutes: Route = statusHandler

  private def statusHandler: Route =
    path("healthCheck") {
      get {
        onComplete(checkDbConnection()) {
          case Success(_) =>
            complete(StatusCodes.NoContent)
          case Failure(err) =>
            logger.error("Health check failed", err)
            complete(StatusCodes.InternalServerError)
        }
      }
    }
}
