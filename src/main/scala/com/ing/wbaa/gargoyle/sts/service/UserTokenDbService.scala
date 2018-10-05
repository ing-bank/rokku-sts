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

  protected[this] def getNewAwsSession(userName: UserName, duration: Option[Duration], assumedGroups: Option[UserAssumedGroup]): Future[AwsSession]

  protected[this] def getAssumedGroupsForToken(awsSessionToken: AwsSessionToken): Future[Option[UserAssumedGroup]]

  protected[this] def getTokenExpiration(awsSessionToken: AwsSessionToken): Future[Option[AwsSessionTokenExpiration]]

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
                getAssumedGroupsForToken(sessionToken)
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
    getTokenExpiration(awsSessionToken).map {
      case Some(tokenExpiration) =>
        val isExpired = tokenExpiration.value.isAfter(Instant.now())
        if (isExpired) logger.warn(s"Sessiontoken provided has expired at: ${tokenExpiration.value} for token: $awsSessionToken")
        !isExpired

      case None =>
        logger.error("Token doesn't have any expiration time associated with it.")
        false
    }
}
