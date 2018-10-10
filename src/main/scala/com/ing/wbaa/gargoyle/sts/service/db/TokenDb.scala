package com.ing.wbaa.gargoyle.sts.service.db

import com.ing.wbaa.gargoyle.sts.data.{ UserAssumedGroup, UserName }
import com.ing.wbaa.gargoyle.sts.data.aws.{ AwsSession, AwsSessionToken, AwsSessionTokenExpiration }
import com.ing.wbaa.gargoyle.sts.service.TokenGeneration
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.Duration

trait TokenDb extends TokenGeneration with LazyLogging {

  implicit protected[this] def executionContext: ExecutionContext

  protected[this] def insertToken(awsSessionToken: AwsSessionToken, username: UserName, expirationDate: AwsSessionTokenExpiration, assumedGroup: Option[UserAssumedGroup]): Future[Boolean]

  protected[this] def getToken(awsSessionToken: AwsSessionToken): Future[Option[(UserName, AwsSessionTokenExpiration, UserAssumedGroup)]]

  /**
   * Retrieve a new Aws Session, encoded with it are the groups assumed with this token
   */
  def getNewAwsSession(userName: UserName, duration: Option[Duration], assumedGroups: Option[UserAssumedGroup]): Future[AwsSession] = {
    val newAwsSession = generateAwsSession(duration)
    addCredential(newAwsSession, userName, assumedGroups)
      .flatMap {
        case true => Future.successful(newAwsSession)
        case false =>
          logger.debug("Generated token collided with existing token in DB, generating a new one ...")
          getNewAwsSession(userName, duration, assumedGroups)
      }
  }

  /**
   * Gets User Assumed Group against the session token
   *
   * @param awsSessionToken
   * @return
   */
  def getAssumedGroupsForToken(awsSessionToken: AwsSessionToken): Future[Option[UserAssumedGroup]] = synchronized {
    getToken(awsSessionToken)
      .map {
        case Some((username, expirationDate, assumedGroup)) => Some(assumedGroup)
        case None => None
      }
  }

  /**
   * Gets token expiration against the session token
   *
   * @param awsSessionToken
   * @return
   */
  def getTokenExpiration(awsSessionToken: AwsSessionToken): Future[Option[AwsSessionTokenExpiration]] = synchronized {
    getToken(awsSessionToken)
      .map {
        case Some((username, expirationDate, assumedGroup)) => Some(expirationDate)
        case None => None
      }
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
    credentialExists(awsSession.sessionToken)
      .flatMap {
        case true  => Future.successful(false)
        case false => insertToken(awsSession.sessionToken, userName, awsSession.expiration, assumedUserGroup)

      }
  }

  private[this] def credentialExists(awsSessionToken: AwsSessionToken): Future[Boolean] = {
    getToken(awsSessionToken)
      .map {
        case Some((username, expirationDate, assumedGroup)) => true
        case None => false
      }
  }

}
