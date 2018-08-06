package com.ing.wbaa.gargoyle.sts.service

import scala.collection.mutable
import scala.concurrent.Future

case class UserInfo(userId: String, secretKey: String, groups: Seq[String], arn: String)

/**
 * User service providing information about users
 */
trait UserService {

  /**
   * Add the user information to storage
   *
   * @param accessKey    - the user access key
   * @param sessionToken - the user session token
   * @param userInfo     - the user info
   */
  def addUserInfo(accessKey: String, sessionToken: String, userInfo: UserInfo)

  /**
   * Verify the user credential based on the user access key and session token
   *
   * @param accessKey    - the user access key
   * @param sessionToken the user token
   * @return true if the credential are active
   */
  def isCredentialActive(accessKey: String, sessionToken: String): Future[Boolean]

  /**
   * Get the user info
   *
   * @param accessKey    - the user access key
   * @return the user info or None if the user credential are not active
   */
  def getUserInfo(accessKey: String): Future[Option[UserInfo]]
}

/**
 * Simple user service implementation for test
 */
trait UserServiceImpl extends UserService {

  private[this] val storage = mutable.Map[String, UserInfo]()

  //TODO test mock user
  storage.put("accesskey", UserInfo("testuser", "secretkey", Seq("testgroup", "groupTwo"), "arn"))

  override def isCredentialActive(accessKey: String, sessionToken: String): Future[Boolean] = synchronized {
    Future.successful(storage.get(accessKey).isDefined && "okSessionToken".equals(sessionToken))
  }

  override def getUserInfo(accessKey: String): Future[Option[UserInfo]] = synchronized {
    Future.successful(storage.get(accessKey))
  }

  override def addUserInfo(accessKey: String, sessionToken: String, userInfo: UserInfo): Unit = synchronized {
    storage.put(accessKey, userInfo)
  }
}
