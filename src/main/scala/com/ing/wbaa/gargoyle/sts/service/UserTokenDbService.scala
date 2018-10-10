package com.ing.wbaa.gargoyle.sts.service

import java.time.Instant

import com.ing.wbaa.gargoyle.sts.data.{ STSUserInfo, UserAssumedGroup, UserName }
import com.ing.wbaa.gargoyle.sts.data.aws._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.Duration

trait UserTokenDbService extends LazyLogging with TokenGeneration {

  implicit protected[this] def executionContext: ExecutionContext

  protected[this] def getAwsCredential(userName: UserName): Future[Option[AwsCredential]]

  protected[this] def getUserSecretKeyAndIsNPA(awsAccessKey: AwsAccessKey): Future[Option[(UserName, AwsSecretKey, Boolean)]]

  protected[this] def insertAwsCredentials(username: UserName, awsCredential: AwsCredential, isNpa: Boolean): Future[Boolean]

  protected[this] def getToken(awsSessionToken: AwsSessionToken): Future[Option[(UserName, AwsSessionTokenExpiration, Option[UserAssumedGroup])]]

  protected[this] def insertToken(awsSessionToken: AwsSessionToken, username: UserName, expirationDate: AwsSessionTokenExpiration, assumedGroup: Option[UserAssumedGroup]): Future[Boolean]

  /**
   * Retrieve or generate Credentials and generate a new Session
   *
   * @param userName the username
   * @param duration optional: the duration of the session, if duration is not given then it defaults to the application application default
   * @param assumedGroups the group which the session belongs to
   * @return
   */
  def getAwsCredentialWithToken(userName: UserName, duration: Option[Duration], assumedGroups: Option[UserAssumedGroup]): Future[AwsCredentialWithToken] =
    for {
      awsCredential <- getOrGenerateAwsCredential(userName)
      awsSession <- getNewAwsSession(userName, duration, assumedGroups)
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
    getUserSecretKeyAndIsNPA(awsAccessKey) flatMap {
      case Some((userName, awsSecretKey, isNPA)) =>
        awsSessionToken match {
          case Some(sessionToken) =>
            isTokenActive(sessionToken).flatMap {
              case true =>
                getToken(sessionToken)
                  .map(_.flatMap(_._3))
                  .map(userGroup => Some(STSUserInfo(userName, userGroup, awsAccessKey, awsSecretKey)))

              case false => Future.successful(None)
            }

          case None if isNPA =>
            Future.successful(Some(STSUserInfo(userName, None, awsAccessKey, awsSecretKey)))

          case None if !isNPA =>
            logger.warn(s"User validation failed. No sessionToken provided while user is not an NPA " +
              s"(username: $userName, accessKey: $awsAccessKey)")
            Future.successful(None)
        }

      case None =>
        logger.warn(s"User could not be retrieved with accesskey: $awsAccessKey")
        Future.successful(None)
    }

  /**
    * Retrieve a new Aws Session, encoded with it are the groups assumed with this token
    * @param userName
    * @param duration
    * @param assumedGroups
    * @param generationTriesLeft Number of times to retry token generation, in case it collides
    * @return
    */
  private[this] def getNewAwsSession(userName: UserName, duration: Option[Duration], assumedGroups: Option[UserAssumedGroup], generationTriesLeft: Int = 3): Future[AwsSession] = {
    val newAwsSession = generateAwsSession(duration)
    addCredential(newAwsSession, userName, assumedGroups)
      .flatMap {
        case true => Future.successful(newAwsSession)
        case false =>
          if (generationTriesLeft <= 0) Future.failed(new Exception("Token generation failed, keys collided"))
          else {
            logger.debug(s"Generated token collided with existing token in DB, generating a new one ... (tries left: $generationTriesLeft)")
            getNewAwsSession(userName, duration, assumedGroups, generationTriesLeft - 1)
          }
      }
  }

  /**
   * Adds a user to the DB with aws credentials generated for it.
   * In case the user already exists, it returns the already existing credentials.
   */
  private[this] def getOrGenerateAwsCredential(userName: UserName): Future[AwsCredential] =
    getAwsCredential(userName)
      .flatMap {
        case Some(awsCredential) => Future.successful(awsCredential)
        case None                => getNewAwsCredential(userName)
      }

  private[this] def getNewAwsCredential(userName: UserName): Future[AwsCredential] = {
    val newAwsCredential = generateAwsCredential
    insertAwsCredentials(userName, newAwsCredential, false)
      .flatMap {
        case true  => Future.successful(newAwsCredential)
        case false => getNewAwsCredential(userName)
        //TODO: Limit the infinite recursion
      }
  }

  private[this] def isTokenActive(awsSessionToken: AwsSessionToken): Future[Boolean] =
    getToken(awsSessionToken)
      .map(_.map(_._2))
      .map {
        case Some(tokenExpiration) =>
          val isExpired = tokenExpiration.value.isAfter(Instant.now())
          if (isExpired) logger.warn(s"Sessiontoken provided has expired at: ${tokenExpiration.value} for token: $awsSessionToken")
          !isExpired

        case None =>
          logger.error("Token doesn't have any expiration time associated with it.")
          false
      }

  /**
   * Add credential to the credential store.
   * Return None if the credential is a duplicate and thus not added.
   *
   * @param awsSession       Created aws session
   * @param userName         UserName this session is valid for
   * @param assumedUserGroup Group this sessiontoken gives you access to.
   * @return awsCredential if credential is not a duplicated and added successfully
   */
  private[this] def addCredential(awsSession: AwsSession, userName: UserName, assumedUserGroup: Option[UserAssumedGroup]): Future[Boolean] = {
    getToken(awsSession.sessionToken)
      .flatMap {
        case Some(_) => Future.successful(false)
        case None    => insertToken(awsSession.sessionToken, userName, awsSession.expiration, assumedUserGroup)

      }
  }
}
