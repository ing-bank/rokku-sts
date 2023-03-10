package com.ing.wbaa.rokku.sts

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import com.ing.wbaa.rokku.sts.api.AdminApi
import com.ing.wbaa.rokku.sts.api.NpaApi
import com.ing.wbaa.rokku.sts.api.STSApi
import com.ing.wbaa.rokku.sts.api.ServerApi
import com.ing.wbaa.rokku.sts.api.UserApi
import com.ing.wbaa.rokku.sts.config.HttpSettings
import com.ing.wbaa.rokku.sts.handler.StsExceptionHandlers
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

trait RokkuStsService
  extends LazyLogging
  with STSApi
  with UserApi
  with ServerApi
  with NpaApi
  with AdminApi {

  implicit def system: ActorSystem

  implicit val executionContext: ExecutionContext = system.dispatcher

  protected[this] def httpSettings: HttpSettings

  private final val terminationCallbacks: ListBuffer[() => Unit] = ListBuffer()

  // The routes we serve
  final val allRoutes: Route =
    handleExceptions(StsExceptionHandlers.exceptionHandler) {
      toStrictEntity(3.seconds) {
        cors() {
          stsRoutes ~ userRoutes ~ serverRoutes ~ npaRoutes ~ adminRoutes
        }
      }
    }

  // Details about the server binding.
  final val startup: Future[Http.ServerBinding] = {

    Http().newServerAt(httpSettings.httpBind, httpSettings.httpPort).bind(allRoutes)
      .andThen {
        case Success(binding) =>
          logger.info(s"Sts service started listening: ${binding.localAddress}")
          sys.addShutdownHook {
            logger.info("Received termination signal")
            terminationCallbacks.foreach(c => c())
            shutdown()
          }
        case Failure(reason) => logger.error("Sts service failed to start.", reason)
      }
  }

  def shutdown(): Future[Done] = {
    startup.flatMap(_.unbind())
      .andThen {
        case Success(_)      => logger.info("Sts service stopped.")
        case Failure(reason) => logger.error("Sts service failed to stop.", reason)
      }
  }

  def registerTerminationCallback(f: () => Unit): Unit = {
    terminationCallbacks += f
  }
}
