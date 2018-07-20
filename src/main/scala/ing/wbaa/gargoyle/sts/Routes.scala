package ing.wbaa.gargoyle.sts

import akka.http.scaladsl.server.{ Route, RouteConcatenation }
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import ing.wbaa.gargoyle.sts.api.{ S3Api, UserApi }
import ing.wbaa.gargoyle.sts.oauth.OAuth2TokenVerifierImpl
import ing.wbaa.gargoyle.sts.service.{ TokenServiceImpl, UserServiceImpl }

trait Routes extends RouteConcatenation {
  this: Actors with CoreActorSystem =>

  val routes: Route = cors() {
    new UserApi(new UserServiceImpl()).routes ~
      new S3Api(new OAuth2TokenVerifierImpl(), new TokenServiceImpl()).routes
  }
}
