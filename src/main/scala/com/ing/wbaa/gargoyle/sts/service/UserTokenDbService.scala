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
      userName <- getUser(awsAccessKey)
      assumedGroup <- getAssumedGroupsForToken(awsSessionToken)
    } yield userName.map(STSUserInfo(_, assumedGroup))

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
}
