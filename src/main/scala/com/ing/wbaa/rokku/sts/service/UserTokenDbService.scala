package com.ing.wbaa.rokku.sts.service

import java.time.Instant

import com.ing.wbaa.rokku.sts.data.aws._
import com.ing.wbaa.rokku.sts.data.{ AccountStatus, NPA, STSUserInfo, TokenActive, TokenActiveForRole, TokenNotActive, TokenStatus, UserAssumeRole, UserGroup, Username, UserAccount }
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future }

final case class ConflictException(message: String) extends Exception(message) {}

trait UserTokenDbService extends LazyLogging with TokenGeneration {

  implicit protected[this] def executionContext: ExecutionContext

  protected[this] def getUserAccountByName(username: Username): Future[Option[UserAccount]]

  protected[this] def getUserAccountByAccessKey(awsAccessKey: AwsAccessKey): Future[Option[UserAccount]]

  protected[this] def insertAwsCredentials(username: Username, awsCredential: AwsCredential, isNpa: Boolean): Future[Boolean]

  protected[this] def getToken(awsSessionToken: AwsSessionToken, userName: Username): Future[Option[(Username, UserAssumeRole, AwsSessionTokenExpiration)]]

  protected[this] def insertToken(awsSessionToken: AwsSessionToken, username: Username, expirationDate: AwsSessionTokenExpiration): Future[Boolean]

  protected[this] def insertToken(awsSessionToken: AwsSessionToken, username: Username, role: UserAssumeRole, expirationDate: AwsSessionTokenExpiration): Future[Boolean]

  protected[this] def doesUsernameNotExistAndAccessKeyExist(userName: Username, awsAccessKey: AwsAccessKey): Future[Boolean]

  protected[this] def setUserGroups(userName: Username, userGroups: Set[UserGroup]): Future[Boolean]

  protected[this] def doesUsernameExist(username: Username): Future[Boolean]

  protected[this] def doesAccessKeyExist(awsAccessKey: AwsAccessKey): Future[Boolean]

  /**
   * Retrieve or generate Credentials and generate a new Session
   *
   * @param userName   the username
   * @param userGroups the user groups
   * @param duration   optional: the duration of the session, if duration is not given then it defaults to the application application default
   * @return
   */
  def getAwsCredentialWithToken(userName: Username, userGroups: Set[UserGroup], duration: Option[Duration]): Future[AwsCredentialWithToken] =
    for {
      (awsCredential, AccountStatus(isEnabled)) <- getOrGenerateAwsCredentialWithStatus(userName)
      awsSession <- getNewAwsSession(userName, duration)
      _ <- setUserGroups(userName, userGroups)
      if isEnabled
    } yield AwsCredentialWithToken(
      awsCredential,
      awsSession
    )

  /**
   * Retrieve or generate Credentials and generate a new Session for specific role
   *
   * @param userName the username
   * @param userGroups the user groups
   * @param role     the role to get Credentials
   * @param duration optional: the duration of the session, if duration is not given then it defaults to the application application default
   * @return
   */
  def getAwsCredentialWithToken(userName: Username, userGroups: Set[UserGroup], role: UserAssumeRole, duration: Option[Duration]): Future[AwsCredentialWithToken] =
    for {
      (awsCredential, AccountStatus(isEnabled)) <- getOrGenerateAwsCredentialWithStatus(userName)
      awsSession <- getNewAwsSessionWithToken(userName, role, duration)
      _ <- setUserGroups(userName, userGroups)
      if isEnabled
    } yield AwsCredentialWithToken(
      awsCredential,
      awsSession
    )

  /**
   * Check whether the token given is active for the accesskey and potential sessiontoken
   *
   * When a session token is not provided; this user has to be an NPA to be allowed access
   */
  def isCredentialActive(awsAccessKey: AwsAccessKey, awsSessionToken: Option[AwsSessionToken]): Future[Option[STSUserInfo]] =
    getUserAccountByAccessKey(awsAccessKey) flatMap {
      case Some(UserAccount(userName, Some(AwsCredential(_, awsSecretKey)), AccountStatus(isEnabled), NPA(isNPA), groups)) =>
        awsSessionToken match {
          case Some(sessionToken) =>
            if (isEnabled) {
              isTokenActive(sessionToken, userName).flatMap {
                case TokenActive              => Future.successful(Some(STSUserInfo(userName, groups, awsAccessKey, awsSecretKey, None)))
                case TokenActiveForRole(role) => Future.successful(Some(STSUserInfo(userName, Set.empty, awsAccessKey, awsSecretKey, Some(role))))
                case TokenNotActive           => Future.successful(None)
              }
            } else {
              logger.warn(s"User validation failed. User account is not enabled in STS " +
                s"(username: $userName, accessKey: $awsAccessKey)")
              Future.successful(None)
            }

          case None =>
            if (isNPA && isEnabled) {
              Future.successful(Some(STSUserInfo(userName, Set.empty, awsAccessKey, awsSecretKey, None)))
            } else {
              logger.warn(s"User validation failed. No sessionToken provided while user is not an NPA " +
                s"(username: $userName, accessKey: $awsAccessKey) or account is not enabled")
              Future.successful(None)
            }

        }

      case None | Some(UserAccount(_, None, _, _, _)) =>
        logger.warn(s"User could not be retrieved with accesskey: $awsAccessKey")
        Future.successful(None)
    }

  private[this] def generateUniqueAwsCredential(): Future[AwsCredential] = {
    val newAwsCredential = generateAwsCredential
    return doesAccessKeyExist(newAwsCredential.accessKey).flatMap {
      case true  => generateUniqueAwsCredential()
      case false => Future.successful(newAwsCredential)
    }
  }

  /**
   * Registers a user as an NPA
   * @param userName
   * @return A future with the aws credentials of the NPA user
   */
  def registerNpaUser(userName: Username): Future[AwsCredential] = {
    doesUsernameExist(userName).flatMap {
      case true => {
        Future.failed(new ConflictException(s"User account '${userName.value}' is already in NPA registry or it is a regular account that cannot be converted to an NPA."))
      }
      case false => {
        generateUniqueAwsCredential().flatMap(newAwsCredential =>
          insertAwsCredentials(userName, newAwsCredential, true)
            .flatMap {
              case true  => Future.successful(newAwsCredential)
              case false => Future.failed(new Exception(s"Unable to insert user account '${userName.value}' as NPA"))
            })
      }
    }
  }

  /**
   * Retrieve a new Aws Session
   *
   * @param userName
   * @param duration
   * @param generationTriesLeft Number of times to retry token generation, in case it collides
   * @return
   */
  private[this] def getNewAwsSession(userName: Username, duration: Option[Duration], generationTriesLeft: Int = 3): Future[AwsSession] = {
    val newAwsSession = generateAwsSession(duration)
    insertToken(newAwsSession.sessionToken, userName, newAwsSession.expiration)
      .flatMap {
        case true => Future.successful(newAwsSession)
        case false =>
          if (generationTriesLeft <= 0) Future.failed(new Exception("Token generation failed, keys collided"))
          else {
            logger.debug(s"Generated token collided with existing token in DB, generating a new one ... (tries left: $generationTriesLeft)")
            getNewAwsSession(userName, duration, generationTriesLeft - 1)
          }
      }
  }

  /**
   * Retrieve a new Aws Session for a role
   *
   * @param userName
   * @param role the role to get credentials
   * @param duration
   * @param generationTriesLeft Number of times to retry token generation, in case it collides
   * @return
   */
  private[this] def getNewAwsSessionWithToken(userName: Username, role: UserAssumeRole, duration: Option[Duration], generationTriesLeft: Int = 3): Future[AwsSession] = {
    val newAwsSession = generateAwsSession(duration)
    insertToken(newAwsSession.sessionToken, userName, role, newAwsSession.expiration)
      .flatMap {
        case true => Future.successful(newAwsSession)
        case false =>
          if (generationTriesLeft <= 0) Future.failed(new Exception("Token generation failed, keys collided"))
          else {
            logger.debug(s"Generated token collided with existing token in DB, generating a new one ... (tries left: $generationTriesLeft)")
            getNewAwsSessionWithToken(userName, role, duration, generationTriesLeft - 1)
          }
      }
  }

  /**
   * Adds a user to the DB with aws credentials generated for it.
   * In case the user already exists, it returns the already existing credentials.
   */
  private[this] def getOrGenerateAwsCredentialWithStatus(userName: Username): Future[(AwsCredential, AccountStatus)] =
    getUserAccountByName(userName)
      .flatMap {
        case (Some(UserAccount(_, Some(awsCredential), AccountStatus(isEnabled), _, _))) =>
          if (isEnabled) {
            Future.successful((awsCredential, AccountStatus(isEnabled)))
          } else {
            logger.info(s"User account disabled for ${awsCredential.accessKey}")
            Future.successful((awsCredential, AccountStatus(isEnabled)))
          }
        case (None | Some(UserAccount(_, None, _, _, _))) => getNewAwsCredential(userName).map(c => (c, AccountStatus(true)))
      }

  private[this] def getNewAwsCredential(userName: Username): Future[AwsCredential] = {
    val newAwsCredential = generateAwsCredential
    insertAwsCredentials(userName, newAwsCredential, isNpa = false)
      .flatMap {
        case true => Future.successful(newAwsCredential)
        case false =>
          //If this failed it can be due to the access key or the username being duplicate.
          // A check is done to see if it was due to the access key, if so generate another one else fail as user already exists.
          doesUsernameNotExistAndAccessKeyExist(userName, newAwsCredential.accessKey)
            .flatMap {
              case true  => getNewAwsCredential(userName)
              case false => Future.failed(new Exception(s"Username: $userName already exists "))
            }

      }
  }

  private[this] def isTokenActive(awsSessionToken: AwsSessionToken, userName: Username): Future[TokenStatus] = {
    getToken(awsSessionToken, userName).map {
      case Some((_, _, tokenExpiration)) if isTokenExpired(tokenExpiration) =>
        logger.warn(s"Provided sessionToken has expired at: {} for token: {}", tokenExpiration.value, awsSessionToken.value)
        TokenNotActive
      case Some((_, assumeRole, _)) if assumeRole.value.isEmpty => TokenActive
      case Some((_, assumeRole, _))                             => TokenActiveForRole(assumeRole)
      case None =>
        logger.error("Token doesn't have any expiration time associated with it.")
        TokenNotActive
    }
  }

  private[this] def isTokenExpired(tokenExpiration: AwsSessionTokenExpiration) = {
    val isExpired = Instant.now().isAfter(tokenExpiration.value)
    if (isExpired)
      logger.warn(s"Provided sessionToken has expired at: {}", tokenExpiration.value)
    isExpired
  }
}
