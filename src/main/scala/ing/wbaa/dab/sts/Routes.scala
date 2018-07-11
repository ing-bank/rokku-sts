package ing.wbaa.dab.sts

import akka.http.scaladsl.server.{ Route, RouteConcatenation }
import akka.util.Timeout
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ing.wbaa.dab.sts.api.S3

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

trait Routes extends RouteConcatenation {
  this: Actors with CoreActorSystem =>

  val routes: Route = cors() {
    implicit val exContext: ExecutionContextExecutor = system.dispatcher
    implicit val timeout: Timeout = Timeout(5.seconds)
    new S3().routes
  }
}

