package ing.wbaa.s3.sts

import akka.http.scaladsl.server.{ Route, RouteConcatenation }
import akka.util.Timeout
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import ing.wbaa.s3.sts.api.{ S3Api, UserApi }
import ing.wbaa.s3.sts.service.{ TokenServiceImpl, UserServiceImpl }

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

trait Routes extends RouteConcatenation {
  this: Actors with CoreActorSystem =>

  val routes: Route = cors() {
    implicit val exContext: ExecutionContextExecutor = system.dispatcher
    implicit val timeout: Timeout = Timeout(5.seconds)
    new UserApi(new UserServiceImpl()).routes ~
      new S3Api(new TokenServiceImpl()).routes
  }
}
