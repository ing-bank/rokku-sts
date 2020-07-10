package com.ing.wbaa.rokku.sts.api

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ AuthorizationFailedRejection, Route }
import com.ing.wbaa.rokku.sts.api.directive.STSDirectives.authorizeToken
import com.ing.wbaa.rokku.sts.config.StsSettings
import com.ing.wbaa.rokku.sts.data.aws.{ AwsAccessKey, AwsCredential, AwsSecretKey }
import com.ing.wbaa.rokku.sts.data.{ AuthenticationUserInfo, BearerToken, NPAAccount, NPAAccountList, RequestId, UserGroup, UserName }
import com.ing.wbaa.rokku.sts.service.db.security.Encryption
import com.typesafe.scalalogging.LazyLogging
import com.ing.wbaa.rokku.sts.util.JwtToken

import scala.concurrent.Future
import scala.util.{ Failure, Success }

trait AdminApi extends LazyLogging with Encryption with JwtToken {

  protected[this] def stsSettings: StsSettings

  val adminRoutes: Route = pathPrefix("admin") {
    listAllNPAs ~ addNPA ~ addServiceNPA ~ setAccountStatus
  }

  case class ResponseMessage(code: String, message: String, target: String)

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import spray.json.DefaultJsonProtocol._

  implicit val responseMessageFormat = jsonFormat3(ResponseMessage)
  implicit val npaAccountFormat = jsonFormat2(NPAAccount)
  implicit val npaAccountListFormat = jsonFormat1(NPAAccountList)

  // Keycloak
  protected[this] def verifyAuthenticationToken(token: BearerToken): Option[AuthenticationUserInfo]

  protected[this] def insertAwsCredentials(username: UserName, awsCredential: AwsCredential, isNpa: Boolean): Future[Boolean]

  protected[this] def insertNpaCredentialsToVault(username: UserName, awsCredential: AwsCredential): Future[Boolean]

  protected[this] def setAccountStatus(username: UserName, enabled: Boolean): Future[Boolean]

  protected[this] def getAllNPAAccounts: Future[NPAAccountList]

  implicit val requestId = RequestId("")

  def userInAdminGroups(userGroups: Set[UserGroup]): Boolean =
    userGroups.exists(g => stsSettings.adminGroups.contains(g.value))

  //todo: Personal login from keycloak should be removed or changed to service keycloak token
  def addNPA: Route = logRequestResult("debug") {
    post {
      path("npa") {
        formFields((Symbol("npaAccount"), Symbol("awsAccessKey"), Symbol("awsSecretKey"))) { (npaAccount, awsAccessKey, awsSecretKey) =>
          authorizeToken(verifyAuthenticationToken) { keycloakUserInfo =>
            if (userInAdminGroups(keycloakUserInfo.userGroups)) {
              val awsCredentials = AwsCredential(AwsAccessKey(awsAccessKey), AwsSecretKey(awsSecretKey))
              onComplete(insertAwsCredentials(UserName(npaAccount), awsCredentials, isNpa = true)) {
                case Success(true) =>
                  insertNpaCredentialsToVault(UserName(npaAccount), awsCredentials)
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

  def addServiceNPA: Route = logRequestResult("debug") {
    post {
      path("service" / "npa") {
        formFields((Symbol("npaAccount"), Symbol("awsAccessKey"), Symbol("awsSecretKey"))) { (npaAccount, awsAccessKey, awsSecretKey) =>
          headerValueByName("Authorization") { bearerToken =>
            if (verifyInternalToken(bearerToken)) {
              val awsCredentials = AwsCredential(AwsAccessKey(awsAccessKey), AwsSecretKey(awsSecretKey))
              onComplete(insertAwsCredentials(UserName(npaAccount), awsCredentials, isNpa = true)) {
                case Success(true) =>
                  insertNpaCredentialsToVault(UserName(npaAccount), awsCredentials)
                  logger.info(s"NPA: $npaAccount successfully created")
                  complete(ResponseMessage("NPA Created", s"NPA: $npaAccount successfully created", "NPA add"))
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

  def listAllNPAs: Route =
    path("npa" / "list") {
      get {
        authorizeToken(verifyAuthenticationToken) { keycloakUserInfo =>
          if (userInAdminGroups(keycloakUserInfo.userGroups)) {
            onComplete(getAllNPAAccounts) {
              case Success(npaData) => complete(npaData)
              case Failure(ex)      => complete(ResponseMessage("Failed to get NPA list", ex.getMessage, "npa account"))
            }
          } else {
            reject(AuthorizationFailedRejection)
          }
        }
      }
    }

  def setAccountStatus: Route =
    put {
      path("account" / Segment / ("enable" | "disable")) { uid =>
        authorizeToken(verifyAuthenticationToken) { keycloakUserInfo =>
          extractUri { uri =>
            if (userInAdminGroups(keycloakUserInfo.userGroups)) {
              val action = uri.path.toString.split("/").last match {
                case "enable"  => true
                case "disable" => false
              }
              onComplete(setAccountStatus(UserName(uid), action)) {
                case Success(_)  => complete(ResponseMessage(s"Account action", s"User account $uid enabled: $action", "user account"))
                case Failure(ex) => complete(ResponseMessage("Account disable failed", ex.getMessage, "user account"))
              }
            } else {
              reject(AuthorizationFailedRejection)
            }
          }
        }
      }
    }

}
