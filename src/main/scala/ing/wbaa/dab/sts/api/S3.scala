package ing.wbaa.dab.sts.api

import akka.http.scaladsl.model.{ ContentTypes, HttpEntity }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

class S3 {

  val routes = getToken

  def getToken: Route = {
    get {
      complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "ok"))
    }
  }

}
