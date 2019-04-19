package com.ing.wbaa.rokku.sts.api

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ AuthorizationFailedRejection, Route }
import com.ing.wbaa.rokku.sts.api.directive.STSDirectives.authorizeToken
import com.ing.wbaa.rokku.sts.config.StsSettings
import com.ing.wbaa.rokku.sts.data.aws.{ AwsAccessKey, AwsCredential, AwsSecretKey }
import com.ing.wbaa.rokku.sts.data.{ AuthenticationUserInfo, BearerToken, UserGroup, UserName }
import com.ing.wbaa.rokku.sts.service.db.security.Encryption
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.util.{ Failure, Success }

trait AdminApi extends LazyLogging with Encryption {

  protected[this] def stsSettings: StsSettings

  val adminRoutes: Route = pathPrefix("admin") {
    addNPA
  }

  case class ResponseMessage(code: String, message: String, target: String)

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import spray.json.DefaultJsonProtocol._

  implicit val responseMessageFormat = jsonFormat3(ResponseMessage)

  // Keycloak
  protected[this] def verifyAuthenticationToken(token: BearerToken): Option[AuthenticationUserInfo]

  protected[this] def insertAwsCredentials(username: UserName, awsCredential: AwsCredential, isNpa: Boolean): Future[Boolean]

  def userInAdminGroups(userGroups: Set[UserGroup]): Boolean =
    userGroups.exists(g => stsSettings.adminGroups.contains(g.value))

  def addNPA: Route = logRequestResult("debug") {
    post {
      path("npa") {
        formFields(('npaAccount, 'awsAccessKey, 'awsSecretKey)) { (npaAccount, awsAccessKey, awsSecretKey) =>
          authorizeToken(verifyAuthenticationToken) { keycloakUserInfo =>
            if (userInAdminGroups(keycloakUserInfo.userGroups)) {
              val awsCredentials = AwsCredential(AwsAccessKey(awsAccessKey), AwsSecretKey(awsSecretKey))
              onComplete(insertAwsCredentials(UserName(npaAccount), awsCredentials, isNpa = true)) {
                case Success(true) =>
                  logger.info(s"NPA: $npaAccount successfully created by ${keycloakUserInfo.userName}")
                  complete(ResponseMessage("NPA Created", s"NPA: $npaAccount successfully created by ${keycloakUserInfo.userName}", "NPA add"))
                case Success(false) =>
                  logger.warn(s"NPA: $npaAccount create failed, accessKey or NPA name must be unique")
                  complete(ResponseMessage("NPA Create Failed", "Error adding NPA account, accessKey or NPA name must be unique", "NPA add"))
                case Failure(ex) =>
                  logger.error(s"NPA: $npaAccount create failed, " + ex.getMessage)
                  complete(ResponseMessage("NPA Create Failed", ex.getMessage, "NPA add"))
              }
            } else {
              reject(AuthorizationFailedRejection)
            }
          }
        }
      }
    }
  }

}
