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

  /**
   * Retrieve user information with the group assumed
   */
  protected[this] def getUserWithAssumedGroups(awsAccessKey: AwsAccessKey, awsSessionToken: AwsSessionToken): Future[Option[STSUserInfo]] =
    for {
      userAndSecret <- getUserSecretKeyAndIsNPA(awsAccessKey)
      assumedGroup <- getAssumedGroupsForToken(awsSessionToken)
    } yield userAndSecret.map(uas => STSUserInfo(uas._1, assumedGroup, awsAccessKey, uas._2))

  /**
   * Check whether the token given is active for the accesskey
   */
  protected[this] def isTokenActive(awsAccessKey: AwsAccessKey, awsSessionToken: AwsSessionToken): Future[Boolean] = {
    getUserNameAndTokenExpiration(awsSessionToken).flatMap {
      case Some((userName, tokenExpiration)) =>
        getAwsCredential(userName).map {
          case Some(awsCredential) =>
            awsCredential.accessKey == awsAccessKey && tokenExpiration.value.isAfter(Instant.now())

          case None => false
        }

      case None => Future.successful(false)
    }
  }

  /**
   * Check whether this NPA account is active and indeed an NPA
   */
  protected[this] def isNPAActive(awsAccessKey: AwsAccessKey): Future[Boolean] = {
    getUserSecretKeyAndIsNPA(awsAccessKey).map {
      case Some((_, _, isNPA)) =>
        logger.debug(s"Check NPA active (accessKey/isNPA : $awsAccessKey/$isNPA")
        isNPA
      case None =>
        logger.info(s"NPA account does not exist for accesskey: $awsAccessKey")
        false
    }
  }
}
