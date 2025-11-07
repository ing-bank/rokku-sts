package com.ing.wbaa.rokku.sts.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.AuthorizationFailedRejection
import akka.http.scaladsl.server.Directive
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.ing.wbaa.rokku.sts.api.directive.STSDirectives.authorizeNpa
import com.ing.wbaa.rokku.sts.api.directive.STSDirectives.authorizeToken
import com.ing.wbaa.rokku.sts.config.KeycloakSettings
import com.ing.wbaa.rokku.sts.config.StsSettings
import com.ing.wbaa.rokku.sts.data._
import com.ing.wbaa.rokku.sts.data.aws.AwsAccessKey
import com.ing.wbaa.rokku.sts.data.aws.AwsCredential
import com.ing.wbaa.rokku.sts.data.aws.AwsSecretKey
import com.ing.wbaa.rokku.sts.data.UserAccount
import com.ing.wbaa.rokku.sts.keycloak.KeycloakUserId
import com.ing.wbaa.rokku.sts.service.ConflictException
import com.ing.wbaa.rokku.sts.service.TokenGeneration
import com.ing.wbaa.rokku.sts.service.db.security.Encryption
import com.ing.wbaa.rokku.sts.util.JwtToken
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig.UserManagedAccessConfig
import spray.json.RootJsonFormat

trait NpaApi extends LazyLogging with Encryption with JwtToken with TokenGeneration {

  protected[this] def keycloakSettings: KeycloakSettings

  val npaRoutes: Route = pathPrefix("npa") {
    registerNpa ~ getNpaCredentials
  }

  case class NpaAwsCredentialResponse(accessKey: String, secretKey: String)

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import spray.json.DefaultJsonProtocol._

  implicit val npaAwsCredentialFormat: RootJsonFormat[NpaAwsCredentialResponse] = jsonFormat2(NpaAwsCredentialResponse)

  protected[this] def verifyAuthenticationToken(token: BearerToken): Option[AuthenticationUserInfo]

  protected[this] def getUserAccountByName(userName: Username): Future[Option[UserAccount]]

  protected[this] def registerNpaUser(userName: Username): Future[AwsCredential]

  def registerNpa: Route = logRequestResult("debug") {
    post {
      path("registry") {
        authorizeToken(verifyAuthenticationToken) { keycloakUserInfo =>
          authorizeNpa(keycloakUserInfo, keycloakSettings.npaRole) {
            val npaAccount = keycloakUserInfo.userName
            onComplete(registerNpaUser(npaAccount)) {
              case Success(creds: AwsCredential) =>
                logger.info(s"NPA '${npaAccount.value}' successfully registered as an NPA")
                complete(StatusCodes.Created -> NpaAwsCredentialResponse(creds.accessKey.value, creds.secretKey.value))
              case Failure(ex: ConflictException) =>
                val errMsg = s"NPA registration failed for user '${npaAccount.value}'. ${ex.getMessage}"
                logger.error(errMsg)
                complete(StatusCodes.Conflict -> errMsg)
              case Failure(ex) =>
                val errMsg = s"NPA registration failed for user '${npaAccount.value}'. ${ex.getMessage}"
                logger.error(errMsg)
                complete(StatusCodes.InternalServerError -> errMsg)
            }
          }
        }
      }
    }
  }

  def getNpaCredentials: Route = logRequestResult("debug") {
    get {
      path("credentials") {
        authorizeToken(verifyAuthenticationToken) { keycloakUserInfo =>
          val npaAccount = keycloakUserInfo.userName
          authorizeNpa(keycloakUserInfo, keycloakSettings.npaRole) {
            onComplete(getUserAccountByName(npaAccount)) {
              // case Success(UserAccount(_, None, _, _, _)) =>
              case Success(None | Some(UserAccount(_, None, _, _, _))) =>
                val errMsg = s"No credentials were found for user '${npaAccount.value}'"
                logger.info(errMsg)
                complete(StatusCodes.NotFound -> errMsg)
              case Success(Some(UserAccount(_, Some(awsCredential), AccountStatus(isEnabled), NPA(isNpa), _))) =>
                if (isEnabled && isNpa) {
                  complete(NpaAwsCredentialResponse(awsCredential.accessKey.value, awsCredential.secretKey.value))
                } else {
                  val errMsg = s"User account '${npaAccount.value}' is not enabled or it's not an NPA. Enabled: ${isEnabled}, NPA: ${isNpa}"
                  logger.error(errMsg)
                  complete(StatusCodes.NotFound -> errMsg)
                }
              case Failure(ex) =>
                val errMsg = s"Cannot retrieve NPA credentials for user '${npaAccount.value}'"
                logger.error(errMsg)
                complete(StatusCodes.InternalServerError -> errMsg)
            }
          }
        }
      }
    }
  }

}
