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

//trait STSUserTokenStore {
//
//  import STSUserTokenStore._
//
//  def getAwsCredentialWithToken(userInfo: UserInfo, durationSeconds: Option[Duration]): Future[Option[AwsCredentialWithToken]] = ???
//
//  def isCredentialActive(accessKey: AwsAccessKey, sessionToken: AwsSessionToken): Future[Boolean] = synchronized {
//    Future.successful(
//      getUser(accessKey).isDefined &&
//        AwsSessionToken("okSessionToken").equals(sessionToken)
//    )
//  }
//
//  def getUserInfo(accessKey: AwsAccessKey): Future[Option[UserInfo]] = synchronized {
//    Future.successful(getUser(accessKey))
//  }
//
//}
//
//object STSUserTokenStore {
//  // TODO: Move these stores to an actual DB
//  private[this] var userStore = mutable.Map[AwsAccessKey, UserInfo](
//    AwsAccessKey("accesskey") -> UserInfo("testuser", Set("testgroup", "groupTwo"))
//  )
//
////  private[this] val awsCredentialStore = mutable.Map[AwsAccessKey, AwsCredentialWithToken]()
//
//  def getUser(awsAccessKey: AwsAccessKey): Option[UserInfo] = userStore.get(awsAccessKey)
//
////  /**
////    * Retrieve the AwsCredentials for this user.
////    * If this user is still unknown to our system, create AwsCredentials for it.
////    */
////  def getCredentialsForUser(userInfo: UserInfo): Future[AwsCredentialWithToken] = {
////    userStore = userStore ++ Map(accessKey -> userInfo)
////    awsCredentialStore = awsCredentialStore ++ Map(
////      accessKey -> AwsCredentialWithToken(
////
////      ))
////  }
//
//  def generateAwsCredentials(userInfo: UserInfo): AwsCredential =
//    AwsCredential(
//      accessKey = AwsAccessKey(userInfo.userName),
//      secretKey = AwsSecretKey(Random.alphanumeric.take(32).mkString(""))
//    )
//
//  val defaultSessionDuration = Duration(8, TimeUnit.HOURS)
//  val maxSessionDuration = Duration(8, TimeUnit.HOURS)
//
//  def generateAwsSession(duration: Option[Duration]): AwsSession = {
//    val tokenDuration = duration.filter(_ < maxSessionDuration).getOrElse(defaultSessionDuration)
//    AwsSession(
//      sessionToken = AwsSessionToken(Random.alphanumeric.take(32).mkString("")),
//      expiration = AwsSessionTokenExpiration(Instant.now().plusMillis(tokenDuration.toMillis))
//    )
//  }
//}
