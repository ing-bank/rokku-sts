package com.ing.wbaa.gargoyle.sts.service.db

import com.ing.wbaa.gargoyle.sts.data.UserName
import com.ing.wbaa.gargoyle.sts.data.aws.{ AwsAccessKey, AwsCredential, AwsSecretKey }

import scala.collection.mutable
import scala.concurrent.Future

/**
 * Serves a table with a mapping from "username" -> "secretkey", "accesskey"
 * Some Remarks:
 *   - UID in S3 == username
 *   - usernames and accesskeys should be unique
 */
object UserService {

  // TODO: Move this store to an actual DB
  private[this] val userStore = mutable.Map[UserName, AwsCredential]()

  def getAwsCredential(userName: UserName): Future[Option[AwsCredential]] = synchronized {
    Future.successful(userStore.get(userName))
  }

  /**
   * Add a new or overwrite an existing user.
   * Ensures the accesskey provided is unique, otherwise returns None
   */
  def addToUserStore(userName: UserName, awsCredential: AwsCredential): Future[Option[AwsCredential]] = synchronized {
    Future.successful(
      if (userStore.values.map(_.accessKey).toList.contains(awsCredential.accessKey)) None
      else {
        userStore.put(userName, awsCredential)
        Some(awsCredential)
      }
    )
  }

  private[this] def getUserNameAndSecretKey(awsAccessKey: AwsAccessKey): Option[(UserName, AwsSecretKey)] = synchronized {
    val matches = userStore.filter(_._2.accessKey == awsAccessKey).map(e => (e._1, e._2.secretKey)).toList
    if (matches.length == 1) Some(matches.head)
    else None
  }

  def getUser(awsAccessKey: AwsAccessKey): Future[Option[UserName]] =
    Future.successful(getUserNameAndSecretKey(awsAccessKey).map(_._1))
}
