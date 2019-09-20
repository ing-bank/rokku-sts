package com.ing.wbaa.rokku.sts

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import com.ing.wbaa.rokku.sts.api.{ STSApi, ServerApi, UserApi, AdminApi }
import com.ing.wbaa.rokku.sts.config.HttpSettings
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

trait RokkuStsService
  extends LazyLogging
  with STSApi
  with UserApi
  with ServerApi
  with AdminApi {

  implicit def system: ActorSystem

  implicit val materializer: ActorMaterializer = ActorMaterializer()(system)

  implicit val executionContext: ExecutionContext = system.dispatcher

  protected[this] def httpSettings: HttpSettings

  import com.ing.wbaa.rokku.sts.handler.StsExceptionHandlers.exceptionHandler

  // The routes we serve
  final val allRoutes: Route =
    toStrictEntity(3.seconds) {
      cors() {
        stsRoutes ~ userRoutes ~ serverRoutes ~ adminRoutes
      }
    }

  // Details about the server binding.
  final val startup: Future[Http.ServerBinding] = {

    Http(system).bindAndHandle(allRoutes, httpSettings.httpBind, httpSettings.httpPort)
      .andThen {
        case Success(binding) => logger.info(s"Sts service started listening: ${binding.localAddress}")
        case Failure(reason)  => logger.error("Sts service failed to start.", reason)
      }
  }

  def shutdown(): Future[Done] = {
    startup.flatMap(_.unbind)
      .andThen {
        case Success(_)      => logger.info("Sts service stopped.")
        case Failure(reason) => logger.error("Sts service failed to stop.", reason)
      }
  }
}
