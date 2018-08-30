package com.ing.wbaa.gargoyle.sts.service.db

import com.ing.wbaa.gargoyle.sts.data.UserName
import com.ing.wbaa.gargoyle.sts.data.aws.{ AwsAccessKey, AwsCredential, AwsSecretKey }
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }

/**
 * Serves a table with a mapping from "username" -> "secretkey", "accesskey"
 * Some Remarks:
 *   - UID in S3 == username
 *   - usernames and accesskeys should be unique
 */
trait UserDb extends TokenGeneration with LazyLogging {

  implicit protected[this] def executionContext: ExecutionContext

  // TODO: Move this store to an actual DB
  private[this] val userStore = mutable.Map[UserName, AwsCredential]()

  protected[this] def getAwsCredential(userName: UserName): Future[Option[AwsCredential]] = synchronized {
    Future.successful(userStore.get(userName))
  }

  /**
   * Add a new or overwrite an existing user.
   * Ensures the accesskey provided is unique, otherwise returns None
   */
  protected[this] def addToUserStore(userName: UserName, awsCredential: AwsCredential): Future[Option[AwsCredential]] = synchronized {
    Future.successful(
      if (userStore.values.map(_.accessKey).toList.contains(awsCredential.accessKey)) None
      else {
        userStore.put(userName, awsCredential)
        Some(awsCredential)
      }
    )
  }

  protected[this] def getUserAndSecretKey(awsAccessKey: AwsAccessKey): Future[Option[(UserName, AwsSecretKey)]] = synchronized {
    val matches = userStore.filter(_._2.accessKey == awsAccessKey).map(e => (e._1, e._2.secretKey)).toList

    Future.successful {
      if (matches.length == 1) Some(matches.head)
      else None
    }
  }

  private[this] def getNewAwsCredential(userName: UserName): Future[AwsCredential] = {
    val newAwsCredential = generateAwsCredential
    addToUserStore(userName, newAwsCredential)
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
  protected[this] def getOrGenerateAwsCredential(userName: UserName): Future[AwsCredential] =
    getAwsCredential(userName)
      .flatMap {
        case Some(awsCredential) => Future.successful(awsCredential)
        case None                => getNewAwsCredential(userName)
      }
}
