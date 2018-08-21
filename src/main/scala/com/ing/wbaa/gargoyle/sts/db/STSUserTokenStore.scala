package com.ing.wbaa.gargoyle.sts.db

import java.time.Instant
import java.util.concurrent.TimeUnit

import com.ing.wbaa.gargoyle.sts.data.UserInfo
import com.ing.wbaa.gargoyle.sts.data.aws._

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future }

trait STSUserTokenStore {

  implicit def executionContext: ExecutionContext

  private[this] val storage = mutable.Map[AwsAccessKey, UserInfo]()

  //TODO test mock user
  storage.put(AwsAccessKey("accesskey"), UserInfo("testuser", Set("testgroup", "groupTwo")))

  def isCredentialActive(accessKey: AwsAccessKey, sessionToken: AwsSessionToken): Future[Boolean] = synchronized {
    Future.successful(storage.get(accessKey).isDefined && AwsSessionToken("okSessionToken").equals(sessionToken))
  }

  def getUserInfo(accessKey: AwsAccessKey): Future[Option[UserInfo]] = synchronized {
    Future.successful(storage.get(accessKey))
  }

  def addUserInfo(accessKey: AwsAccessKey, sessionToken: AwsSessionToken, userInfo: UserInfo): Unit = synchronized {
    storage.put(accessKey, userInfo)
  }

  def getAwsCredentialWithToken(userInfo: UserInfo, durationSeconds: Option[Duration]): Future[AwsCredentialWithToken] = {
    Future {
      AwsCredentialWithToken(
        AwsAccessKey("accesskey"),
        AwsSecretKey("secretkey"),
        AwsSession(
          AwsSessionToken("okSessionToken"),
          AwsSessionTokenExpiration(Instant.now().plusMillis(Duration(1, TimeUnit.HOURS).toMillis))
        )
      )
    }
  }

  // TODO: Implement and move probably to own function
  protected[this] def canUserAssumeRole(userInfo: UserInfo, roleArn: String): Future[Boolean] = Future {
    true
  }
}
