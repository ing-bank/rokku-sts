package com.ing.wbaa.gargoyle.sts.db

import com.ing.wbaa.gargoyle.sts.data.UserInfo
import com.ing.wbaa.gargoyle.sts.data.aws.{AwsAccessKey, AwsCredentialWithToken, AwsSessionToken}

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.Duration

trait STSUserTokenStore {

  // TODO: Move these stores to an actual DB
  private[this] val userStore = mutable.Map[AwsAccessKey, UserInfo](
    AwsAccessKey("accesskey") -> UserInfo("testuser", Set("testgroup", "groupTwo"))
  )

//  private[this] val awsCredentialStore = mutable.Map[String, AwsCredentialWithToken]()

  def getAwsCredentialWithToken(userInfo: UserInfo, durationSeconds: Option[Duration]): Future[Option[AwsCredentialWithToken]] = ???

  def isCredentialActive(accessKey: AwsAccessKey, sessionToken: AwsSessionToken): Future[Boolean] = synchronized {
    Future.successful(userStore.get(accessKey).isDefined && AwsSessionToken("okSessionToken").equals(sessionToken))
  }

  def getUserInfo(accessKey: AwsAccessKey): Future[Option[UserInfo]] = synchronized {
    Future.successful(userStore.get(accessKey))
  }

  def addUserInfo(accessKey: AwsAccessKey, sessionToken: AwsSessionToken, userInfo: UserInfo): Unit = synchronized {
    userStore.put(accessKey, userInfo)
  }

}
