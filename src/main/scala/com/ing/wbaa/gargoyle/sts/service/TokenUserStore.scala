package com.ing.wbaa.gargoyle.sts.service

import java.time.Instant

import com.ing.wbaa.gargoyle.sts.data.{ KeycloakUserInfo, UserGroup, UserInfo, UserName }
import com.ing.wbaa.gargoyle.sts.data.aws._
import com.ing.wbaa.gargoyle.sts.service.db.UserService
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.Duration

trait TokenUserStore extends LazyLogging {

  import com.ing.wbaa.gargoyle.sts.service.db.TokenService

  implicit def executionContext: ExecutionContext

  /**
   * Retrieve a new Aws Session, encoded with it are the groups assumed with this token
   */
  private[this] def getNewAwsSession(userName: UserName, duration: Option[Duration], assumedGroups: Option[UserGroup]): Future[AwsSession] = {
    val newAwsSession = TokenGeneration.generateAwsSession(duration)
    TokenService
      .addCredential(newAwsSession, userName, assumedGroups)
      .flatMap {
        case Some(awsSession) => Future.successful(awsSession)
        case None =>
          logger.debug("Generated token collided with existing token in DB, generating a new one ...")
          getNewAwsSession(userName, duration, assumedGroups)
      }
  }

  private[this] def getNewAwsCredential(userName: UserName): Future[AwsCredential] = {
    val newAwsCredential = TokenGeneration.generateAwsCredential
    UserService
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
    UserService
      .getAwsCredential(userName)
      .flatMap {
        case Some(awsCredential) => Future.successful(awsCredential)
        case None                => getNewAwsCredential(userName)
      }

  def getUserWithAssumedGroups(awsAccessKey: AwsAccessKey, awsSessionToken: AwsSessionToken): Future[Option[UserInfo]] =
    for {
      userName <- UserService.getUser(awsAccessKey)
      assumedGroup <- TokenService.getAssumedGroupsForToken(awsSessionToken)
    } yield userName.map(UserInfo(_, assumedGroup))

  def isTokenActive(awsAccessKey: AwsAccessKey, awsSessionToken: AwsSessionToken): Future[Boolean] = {
    TokenService.getUserNameAndTokenExpiration(awsSessionToken).flatMap {
      case Some((userName, tokenExpiration)) =>
        UserService.getAwsCredential(userName).map {
          case Some(awsCredential) =>
            awsCredential.accessKey == awsAccessKey && tokenExpiration.value.isBefore(Instant.now())

          case None => false
        }

      case None => Future.successful(false)
    }
  }

  def getAwsCredentialWithToken(userName: UserName, duration: Option[Duration], assumedGroups: Option[UserGroup]): Future[AwsCredentialWithToken] =
    for {
      awsCredential <- getOrGenerateAwsCredential(userName)
      awsSession <- getNewAwsSession(userName, duration, assumedGroups)
    } yield AwsCredentialWithToken(
      awsCredential,
      awsSession
    )

  // TODO: Implement
  /**
   * Parses the ARN to a group the user can assume.
   * Then verifies the user can indeed assume this role.
   */
  def canUserAssumeRole(keycloakUserInfo: KeycloakUserInfo, roleArn: String): Future[Option[UserGroup]] = Future {
    def parseArn(arn: String): Option[UserGroup] = Some(UserGroup("user"))

    parseArn(roleArn)
      .filter(keycloakUserInfo.userGroups.contains)
  }
}
