package com.ing.wbaa.gargoyle.sts.service.db

import com.ing.wbaa.gargoyle.sts.data.{ UserGroup, UserName }
import com.ing.wbaa.gargoyle.sts.data.aws.{ AwsSession, AwsSessionToken, AwsSessionTokenExpiration }

import scala.collection.mutable
import scala.concurrent.Future

/**
 * Serves a table with a mapping from "sessiontoken" -> "expirationdate", "username", Option("group")
 */
object TokenDb {

  // TODO: Move this store to an actual DB
  private[this] val awsCredentialStore = mutable.Map[AwsSessionToken, (AwsSessionTokenExpiration, UserName, Option[UserGroup])]()

  private[this] def credentialExists(awsSessionToken: AwsSessionToken): Boolean = synchronized {
    awsCredentialStore.get(awsSessionToken).isDefined
  }

  def getAssumedGroupsForToken(awsSessionToken: AwsSessionToken): Future[Option[UserGroup]] = synchronized {
    Future.successful(
      awsCredentialStore.get(awsSessionToken).flatMap(_._3)
    )
  }

  def getUserNameAndTokenExpiration(awsSessionToken: AwsSessionToken): Future[Option[(UserName, AwsSessionTokenExpiration)]] = synchronized {
    Future.successful(awsCredentialStore.get(awsSessionToken).map(e => (e._2, e._1)))
  }

  /**
   * Add credential to the credential store.
   * Return None if the credential is a duplicate and thus not added.
   *
   * @param awsSession Created aws session
   * @param userName UserName this session is valid for
   * @param assumedUserGroup Group this sessiontoken gives you access to.
   * @return awsCredential if credential is not a duplicated and added successfully
   */
  def addCredential(awsSession: AwsSession, userName: UserName, assumedUserGroup: Option[UserGroup]): Future[Option[AwsSession]] =
    Future.successful(
      if (credentialExists(awsSession.sessionToken)) None
      else synchronized {
        awsCredentialStore.put(awsSession.sessionToken, (awsSession.expiration, userName, assumedUserGroup))
        Some(awsSession)
      }
    )
}
