package com.ing.wbaa.gargoyle.sts.service

import java.time.Instant

import com.ing.wbaa.gargoyle.sts.data.{ UserGroup, STSUserInfo, UserName }
import com.ing.wbaa.gargoyle.sts.data.aws._
import com.ing.wbaa.gargoyle.sts.service.db.{ TokenDb, UserDb }
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.Duration

trait UserTokenDbService extends LazyLogging with TokenDb with UserDb {

  implicit protected[this] def executionContext: ExecutionContext

  /**
   * Retrieve or generate Credentials and generate a new Session
   */
  protected[this] def getAwsCredentialWithToken(userName: UserName, duration: Option[Duration], assumedGroups: Option[UserGroup]): Future[AwsCredentialWithToken] =
    for {
      awsCredential <- getOrGenerateAwsCredential(userName)
      awsSession <- getNewAwsSession(userName, duration, assumedGroups)
    } yield AwsCredentialWithToken(
      awsCredential,
      awsSession
    )

  private[this] def isTokenActive(awsSessionToken: AwsSessionToken): Future[Boolean] =
    getTokenExpiration(awsSessionToken).map {
      case Some(tokenExpiration) =>
        val isExpired = tokenExpiration.value.isAfter(Instant.now())
        if (isExpired) logger.warn(s"Sessiontoken provided has expired at: ${tokenExpiration.value} for token: $awsSessionToken")
        isExpired

      case None =>
        logger.error("Token doesn't have any expiration time associated with it.")
        false
    }

  /**
   * Check whether the token given is active for the accesskey and potential sessiontoken
   *
   * When a session token is not provided; this user has to be an NPA to be allowed access
   */
  protected[this] def isCredentialActive(awsAccessKey: AwsAccessKey, awsSessionToken: Option[AwsSessionToken]): Future[Option[STSUserInfo]] =
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
}
