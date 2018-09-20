package com.ing.wbaa.gargoyle.sts.service.db

import com.ing.wbaa.gargoyle.sts.config.GargoyleNPASettings
import com.ing.wbaa.gargoyle.sts.data.UserName
import com.ing.wbaa.gargoyle.sts.data.aws.{ AwsAccessKey, AwsCredential, AwsSecretKey }
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }

/**
 * Serves a table with a mapping from "username" -> "secretkey", "accesskey", "isNPA"
 * Some Remarks:
 *   - UID in S3 == username
 *   - usernames and accesskeys should be unique
 */
trait UserDb extends TokenGeneration with LazyLogging {

  implicit protected[this] def executionContext: ExecutionContext

  protected[this] def gargoyleNPASettings: GargoyleNPASettings

  // TODO: Move this store to an actual DB
  private[this] case class DbValue(awsCredential: AwsCredential, isNpa: Boolean)

  private[this] lazy val userStore: mutable.Map[UserName, DbValue] =
    mutable.Map() ++
      gargoyleNPASettings.gargoyleNPAList.flatMap { listItem =>
        for {
          u <- listItem.get("username")
          a <- listItem.get("accesskey")
          s <- listItem.get("secretkey")
        } yield (UserName(u), DbValue(AwsCredential(AwsAccessKey(a), AwsSecretKey(s)), true))
      }.toMap

  private[this] def getAwsCredential(userName: UserName): Future[Option[AwsCredential]] = synchronized {
    Future.successful(userStore.get(userName).map(_.awsCredential))
  }

  /**
   * Add a new or overwrite an existing user.
   * Ensures the accesskey provided is unique, otherwise returns None
   */
  protected[this] def addToUserStore(userName: UserName, awsCredential: AwsCredential): Future[Option[AwsCredential]] = synchronized {
    Future.successful(
      if (userStore.values.map(_.awsCredential.accessKey).toList.contains(awsCredential.accessKey)) None
      else {
        userStore.put(userName, DbValue(awsCredential, false))
        Some(awsCredential)
      }
    )
  }

  /**
   * @param awsAccessKey Aws AccessKey
   * @return username/secretKey/isNPA
   */
  protected[this] def getUserSecretKeyAndIsNPA(awsAccessKey: AwsAccessKey): Future[Option[(UserName, AwsSecretKey, Boolean)]] = synchronized {
    val matches = userStore
      .filter(_._2.awsCredential.accessKey == awsAccessKey)
      .map(e => (e._1, e._2.awsCredential.secretKey, e._2.isNpa)).toList

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
