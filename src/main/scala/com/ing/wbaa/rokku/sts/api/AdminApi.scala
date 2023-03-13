package com.ing.wbaa.rokku.sts.api

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ AuthorizationFailedRejection, Route }
import com.ing.wbaa.rokku.sts.api.directive.STSDirectives.authorizeToken
import com.ing.wbaa.rokku.sts.config.StsSettings
import com.ing.wbaa.rokku.sts.data.aws.{ AwsAccessKey, AwsCredential, AwsSecretKey }
import com.ing.wbaa.rokku.sts.data._
import com.ing.wbaa.rokku.sts.keycloak.KeycloakUserId
import com.ing.wbaa.rokku.sts.service.db.security.Encryption
import com.ing.wbaa.rokku.sts.util.JwtToken
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.util.{ Failure, Success }

trait AdminApi extends LazyLogging with Encryption with JwtToken {

  protected[this] def stsSettings: StsSettings

  val adminRoutes: Route = pathPrefix("admin") {
    listAllNPAs ~ addNPA ~ addServiceNPA ~ setAccountStatus ~ insertServiceUserToKeycloak ~ insertUserToKeycloak
  }

  case class ResponseMessage(code: String, message: String, target: String)

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import spray.json.DefaultJsonProtocol._

  implicit val responseMessageFormat = jsonFormat3(ResponseMessage)
  implicit val npaAccountFormat = jsonFormat2(NPAAccount)
  implicit val npaAccountListFormat = jsonFormat1(NPAAccountList)

  // Keycloak
  protected[this] def verifyAuthenticationToken(token: BearerToken): Option[AuthenticationUserInfo]

  protected[this] def insertAwsCredentials(username: Username, awsCredential: AwsCredential, isNpa: Boolean): Future[Boolean]

  protected[this] def insertNpaCredentialsToVault(username: Username, safeName: String, awsCredential: AwsCredential): Future[Boolean]

  protected[this] def setAccountStatus(username: Username, enabled: Boolean): Future[Boolean]

  protected[this] def getAllNPAAccounts: Future[NPAAccountList]

  protected[this] def insertUserToKeycloak(username: Username): Future[KeycloakUserId]

  implicit val requestId = RequestId("")

  def userInAdminGroups(userGroups: Set[UserGroup]): Boolean =
    userGroups.exists(g => stsSettings.adminGroups.contains(g.value))

  //todo: Personal login from keycloak should be removed or changed to service keycloak token
  def addNPA: Route = logRequestResult("debug") {
    post {
      path("npa") {
        formFields("npaAccount", "safeName", "awsAccessKey", "awsSecretKey") { (npaAccount, safeName, awsAccessKey, awsSecretKey) =>
          authorizeToken(verifyAuthenticationToken) { keycloakUserInfo =>
            if (userInAdminGroups(keycloakUserInfo.userGroups)) {
              val awsCredentials = AwsCredential(AwsAccessKey(awsAccessKey), AwsSecretKey(awsSecretKey))
              onComplete(insertAwsCredentials(Username(npaAccount), awsCredentials, isNpa = true)) {
                case Success(true) =>
                  insertNpaCredentialsToVault(Username(npaAccount), safeName, awsCredentials)
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
        formFields("npaAccount", "safeName", "awsAccessKey", "awsSecretKey") { (npaAccount, safeName, awsAccessKey, awsSecretKey) =>
          headerValueByName("Authorization") { bearerToken =>
            verifyInternalToken(bearerToken) {
              val awsCredentials = AwsCredential(AwsAccessKey(awsAccessKey), AwsSecretKey(awsSecretKey))
              onComplete(insertAwsCredentials(Username(npaAccount), awsCredentials, isNpa = true)) {
                case Success(true) =>
                  insertNpaCredentialsToVault(Username(npaAccount), safeName, awsCredentials)
                  logger.info(s"NPA: $npaAccount successfully created")
                  complete(ResponseMessage("NPA Created", s"NPA: $npaAccount successfully created", "NPA add"))
                case Success(false) =>
                  logger.warn(s"NPA: $npaAccount create failed, accessKey or NPA name must be unique")
                  complete(ResponseMessage("NPA Create Failed", "Error adding NPA account, accessKey or NPA name must be unique", "NPA add"))
                case Failure(ex) =>
                  logger.error(s"NPA: $npaAccount create failed, " + ex.getMessage)
                  complete(ResponseMessage("NPA Create Failed", ex.getMessage, "NPA add"))
              }
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
              onComplete(setAccountStatus(Username(uid), action)) {
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

  def insertUserToKeycloak: Route = logRequestResult("debug") {
    post {
      path("keycloak" / "user") {
        formFields((Symbol("username"))) { username =>
          authorizeToken(verifyAuthenticationToken) { keycloakUserInfo =>
            extractUri { _ =>
              if (userInAdminGroups(keycloakUserInfo.userGroups)) {
                onComplete(insertUserToKeycloak(Username(username))) {
                  case Success(_)  => complete(ResponseMessage(s"Add user ok", s"$username added", "keycloak"))
                  case Failure(ex) => complete(ResponseMessage(s"Add user error", ex.getMessage, "keycloak"))
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

  def insertServiceUserToKeycloak: Route = logRequestResult("debug") {
    post {
      path("service" / "keycloak" / "user") {
        formFields((Symbol("username"))) { username =>
          headerValueByName("Authorization") { bearerToken =>
            verifyInternalToken(bearerToken) {
              onComplete(insertUserToKeycloak(Username(username))) {
                case Success(_)  => complete(ResponseMessage(s"Add user ok", s"$username added", "keycloak"))
                case Failure(ex) => complete(ResponseMessage(s"Add user error", ex.getMessage, "keycloak"))
              }
            }
          }
        }
      }
    }
  }
}
