package com.ing.wbaa.gargoyle.sts.service

import java.time.Instant

import com.ing.wbaa.gargoyle.sts.data.{ UserGroup, UserInfo, UserName }
import com.ing.wbaa.gargoyle.sts.data.aws._
import com.ing.wbaa.gargoyle.sts.service.db.UserDb
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.Duration

trait UserTokenService extends LazyLogging with TokenGeneration {

  import com.ing.wbaa.gargoyle.sts.service.db.TokenDb

  implicit protected[this] def executionContext: ExecutionContext

  /**
   * Retrieve a new Aws Session, encoded with it are the groups assumed with this token
   */
  private[this] def getNewAwsSession(userName: UserName, duration: Option[Duration], assumedGroups: Option[UserGroup]): Future[AwsSession] = {
    val newAwsSession = generateAwsSession(duration)
    TokenDb
      .addCredential(newAwsSession, userName, assumedGroups)
      .flatMap {
        case Some(awsSession) => Future.successful(awsSession)
        case None =>
          logger.debug("Generated token collided with existing token in DB, generating a new one ...")
          getNewAwsSession(userName, duration, assumedGroups)
      }
  }

  private[this] def getNewAwsCredential(userName: UserName): Future[AwsCredential] = {
    val newAwsCredential = generateAwsCredential
    UserDb
      .addToUserStore(userName, newAwsCredential)
      .flatMap {
        case Some(awsCredential) => Future.successful(awsCredential)
        case None =>
          logger.debug("Generated credentials collided with existing token in DB, generating new ones ...")
          getNewAwsCredential(userName)
      }
  }

  /**
   * Adds a user to the DB with aws credentials generated for it.
   * In case the user already exists, it returns the already existing credentials.
   */
  private[this] def getOrGenerateAwsCredential(userName: UserName): Future[AwsCredential] =
    UserDb
      .getAwsCredential(userName)
      .flatMap {
        case Some(awsCredential) => Future.successful(awsCredential)
        case None                => getNewAwsCredential(userName)
      }

  /**
   * Retrieve or generate Credentials and generate a new Session
   */
  def getAwsCredentialWithToken(userName: UserName, duration: Option[Duration], assumedGroups: Option[UserGroup]): Future[AwsCredentialWithToken] =
    for {
      awsCredential <- getOrGenerateAwsCredential(userName)
      awsSession <- getNewAwsSession(userName, duration, assumedGroups)
    } yield AwsCredentialWithToken(
      awsCredential,
      awsSession
    )

  def getUserWithAssumedGroups(awsAccessKey: AwsAccessKey, awsSessionToken: AwsSessionToken): Future[Option[UserInfo]] =
    for {
      userName <- UserDb.getUser(awsAccessKey)
      assumedGroup <- TokenDb.getAssumedGroupsForToken(awsSessionToken)
    } yield userName.map(UserInfo(_, assumedGroup))

  def isTokenActive(awsAccessKey: AwsAccessKey, awsSessionToken: AwsSessionToken): Future[Boolean] = {
    TokenDb.getUserNameAndTokenExpiration(awsSessionToken).flatMap {
      case Some((userName, tokenExpiration)) =>
        UserDb.getAwsCredential(userName).map {
          case Some(awsCredential) =>
            awsCredential.accessKey == awsAccessKey && tokenExpiration.value.isAfter(Instant.now())

          case None => false
        }

      case None => Future.successful(false)
    }
  }
}
