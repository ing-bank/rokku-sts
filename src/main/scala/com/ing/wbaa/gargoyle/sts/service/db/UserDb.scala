package com.ing.wbaa.gargoyle.sts.service.db

import com.ing.wbaa.gargoyle.sts.data.UserName
import com.ing.wbaa.gargoyle.sts.data.aws.AwsCredential
import com.ing.wbaa.gargoyle.sts.service.db.dao.STSUserDAO
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContext, Future }

trait UserDb extends TokenGeneration with LazyLogging with STSUserDAO {

  implicit protected[this] def executionContext: ExecutionContext

  private[this] def getNewAwsCredential(userName: UserName): Future[AwsCredential] = {
    val newAwsCredential = generateAwsCredential
    insertAwsCredentials(userName, newAwsCredential, false)
      .flatMap {
        case true  => Future.successful(newAwsCredential)
        case false => getNewAwsCredential(userName)
        //TODO: Limit the infinite recursion
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
