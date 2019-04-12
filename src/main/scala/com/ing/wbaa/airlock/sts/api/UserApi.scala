package com.ing.wbaa.airlock.sts.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.ing.wbaa.airlock.sts.data.aws.{ AwsAccessKey, AwsSessionToken }
import com.ing.wbaa.airlock.sts.data.{ STSUserInfo, UserGroup }
import com.ing.wbaa.airlock.sts.util.JwtToken
import com.typesafe.scalalogging.LazyLogging
import spray.json.RootJsonFormat

import scala.concurrent.Future

trait UserApi extends LazyLogging with JwtToken {

  protected[this] def isCredentialActive(awsAccessKey: AwsAccessKey, awsSessionToken: Option[AwsSessionToken]): Future[Option[STSUserInfo]]

  val userRoutes: Route = isCredentialActive

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import spray.json.DefaultJsonProtocol._

  case class UserInfoToReturn(userName: String, userGroups: Set[String], accessKey: String, secretKey: String)

  implicit val userGroup: RootJsonFormat[UserGroup] = jsonFormat(UserGroup, "value")
  implicit val userInfoJsonFormat: RootJsonFormat[UserInfoToReturn] = jsonFormat4(UserInfoToReturn)

  def isCredentialActive: Route = logRequestResult("debug") {
    path("isCredentialActive") {
      get {
        headerValueByName("Authorization") { bearerToken =>

          if (verifyInternalToken(bearerToken)) {

            parameters(('accessKey, 'sessionToken.?)) { (accessKey, sessionToken) =>
              onSuccess(isCredentialActive(AwsAccessKey(accessKey), sessionToken.map(AwsSessionToken))) {

                case Some(userInfo) =>
                  logger.info("isCredentialActive ok for accessKey={}, sessionToken={}", accessKey, sessionToken)
                  complete((StatusCodes.OK, UserInfoToReturn(
                    userInfo.userName.value,
                    userInfo.userGroup.map(_.value),
                    userInfo.awsAccessKey.value,
                    userInfo.awsSecretKey.value)))

                case None =>
                  logger.warn("isCredentialActive forbidden for accessKey={}, sessionToken={}", accessKey, sessionToken)
                  complete(StatusCodes.Forbidden)
              }
            }
          } else {
            logger.warn("isCredentialActive not verified for token={}", bearerToken)
            complete(StatusCodes.Forbidden)
          }
        }
      }
    }
  }
}
