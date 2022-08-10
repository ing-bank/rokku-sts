package com.ing.wbaa.rokku.sts.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.ing.wbaa.rokku.sts.data.aws.{ AwsAccessKey, AwsSessionToken }
import com.ing.wbaa.rokku.sts.data.{ RequestId, STSUserInfo, UserAssumeRole, UserGroup }
import com.ing.wbaa.rokku.sts.handler.LoggerHandlerWithId
import com.ing.wbaa.rokku.sts.util.{ JwtToken, JwtTokenException }
import spray.json.RootJsonFormat

import scala.concurrent.Future

trait UserApi extends JwtToken {

  protected[this] def isCredentialActive(awsAccessKey: AwsAccessKey, awsSessionToken: Option[AwsSessionToken]): Future[Option[STSUserInfo]]

  val userRoutes: Route = isCredentialActive
  private val logger = new LoggerHandlerWithId

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import spray.json.DefaultJsonProtocol._

  case class UserInfoToReturn(userName: String, userGroups: Set[String], accessKey: String, secretKey: String, userRole: String)

  implicit val userGroup: RootJsonFormat[UserGroup] = jsonFormat(UserGroup, "value")
  implicit val userInfoJsonFormat: RootJsonFormat[UserInfoToReturn] = jsonFormat5(UserInfoToReturn)

  def isCredentialActive: Route = logRequestResult("debug") {
    path("isCredentialActive") {
      get {
        headerValueByName("Authorization") { bearerToken =>
          optionalHeaderValueByName("x-rokku-request-id") { reqIdOpt =>
            implicit val id = reqIdOpt match {
              case Some(v) => RequestId(v)
              case None    => RequestId("")
            }

            var isBearerTokenValid = false
            try {
              isBearerTokenValid = verifyInternalToken(bearerToken)
              if (isBearerTokenValid) {
                parameters("accessKey", "sessionToken".?) { (accessKey, sessionToken) =>
                  onSuccess(isCredentialActive(AwsAccessKey(accessKey), sessionToken.map(AwsSessionToken))) {

                    case Some(userInfo) =>
                      logger.info("isCredentialActive ok for accessKey={}, sessionToken={}", accessKey, sessionToken)
                      complete((StatusCodes.OK, UserInfoToReturn(
                        userInfo.userName.value,
                        userInfo.userGroup.map(_.value),
                        userInfo.awsAccessKey.value,
                        userInfo.awsSecretKey.value,
                        userInfo.userRole.getOrElse(UserAssumeRole("")).value)))

                    case None =>
                      logger.warn("isCredentialActive forbidden for accessKey={}, sessionToken={}", accessKey, sessionToken)
                      complete(StatusCodes.Forbidden)
                  }
                }
              } else {
                logger.warn("isCredentialActive not verified for token={}", bearerToken)
                complete(StatusCodes.Forbidden)
              }
            } catch {
              case ex: JwtTokenException =>
                logger.warn("isCredentialActive malformed token={}, $s", bearerToken, ex.getMessage)
                complete(StatusCodes.BadRequest)
            }
          }
        }
      }
    }
  }
}
