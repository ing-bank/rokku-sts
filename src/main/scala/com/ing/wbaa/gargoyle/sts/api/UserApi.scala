package com.ing.wbaa.gargoyle.sts.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.ing.wbaa.gargoyle.sts.data.STSUserInfo
import com.ing.wbaa.gargoyle.sts.data.aws.{ AwsAccessKey, AwsSessionToken }
import com.typesafe.scalalogging.LazyLogging
import spray.json.RootJsonFormat

import scala.concurrent.Future

trait UserApi extends LazyLogging {

  protected[this] def isCredentialActive(awsAccessKey: AwsAccessKey, awsSessionToken: Option[AwsSessionToken]): Future[Option[STSUserInfo]]

  val userRoutes: Route = isCredentialActive

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import spray.json.DefaultJsonProtocol._

  // TODO: remove this and properly parse userinfo
  case class UserInfoToReturn(userName: String, userGroup: Option[String], accessKey: String, secretKey: String)

  implicit val userInfoJsonFormat: RootJsonFormat[UserInfoToReturn] = jsonFormat4(UserInfoToReturn)

  def isCredentialActive: Route = logRequestResult("debug") {
    path("isCredentialActive") {
      get {
        parameters(('accessKey, 'sessionToken.?)) { (accessKey, sessionToken) =>
          onSuccess(isCredentialActive(AwsAccessKey(accessKey), sessionToken.map(AwsSessionToken))) {

            case Some(userInfo) =>
              logger.info("isCredentialActive ok for accessKey={}, sessionToken={}", accessKey, sessionToken)
              complete((StatusCodes.OK, UserInfoToReturn(
                userInfo.userName.value,
                userInfo.assumedGroup.map(_.value),
                userInfo.awsAccessKey.value,
                userInfo.awsSecretKey.value)))

            case None =>
              logger.info("isCredentialActive forbidden for accessKey={}, sessionToken={}", accessKey, sessionToken)
              complete(StatusCodes.Forbidden)
          }
        }
      }
    }
  }
}
