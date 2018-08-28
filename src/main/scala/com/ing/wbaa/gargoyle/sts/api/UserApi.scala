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

  protected[this] def isTokenActive(awsAccessKey: AwsAccessKey, awsSessionToken: AwsSessionToken): Future[Boolean]

  protected[this] def getUserWithAssumedGroups(awsAccessKey: AwsAccessKey, awsSessionToken: AwsSessionToken): Future[Option[STSUserInfo]]

  val userRoutes: Route = verifyUser ~ getUser

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import spray.json.DefaultJsonProtocol._

  // TODO: remove this and properly parse userinfo
  case class UserInfoToReturn(userName: String, userGroups: Option[String])

  implicit val userInfoJsonFormat: RootJsonFormat[UserInfoToReturn] = jsonFormat2(UserInfoToReturn)

  def verifyUser: Route = logRequestResult("debug") {
    path("isCredentialActive") {
      get {
        parameters(("accessKey", "sessionToken")) { (accessKey, sessionToken) =>
          onSuccess(isTokenActive(AwsAccessKey(accessKey), AwsSessionToken(sessionToken))) { isActive =>
            val result = if (isActive) {
              logger.info("isCredentialActive ok for accessKey={}, sessionToken={}", accessKey, sessionToken)
              StatusCodes.OK
            } else {
              logger.info("isCredentialActive forbidden for accessKey={}, sessionToken={}", accessKey, sessionToken)
              StatusCodes.Forbidden
            }
            complete(result)
          }
        }
      }
    }
  }

  def getUser: Route = logRequestResult("debug") {
    path("userInfo") {
      get {
        parameters(('accessKey, 'sessionToken)) {
          (accessKey, sessionToken) =>
            onSuccess(getUserWithAssumedGroups(AwsAccessKey(accessKey), AwsSessionToken(sessionToken))) {
              case Some(userInfo) =>
                logger.info("user info ok for accessKey={}", accessKey)
                complete(UserInfoToReturn(userInfo.userName.value, userInfo.assumedGroups.map(_.value)))
              case _ =>
                logger.info("user info not found for accessKey={}", accessKey)
                complete(StatusCodes.NotFound)
            }
        }
      }
    }
  }
}
