package com.ing.wbaa.gargoyle.sts.service

import scala.collection.mutable
import scala.concurrent.Future

case class UserInfo(userId: String, secretKey: String, groups: Seq[String], arn: String)

/**
 * User service providing information about users
 */
trait UserService {

  private[this] val storage = mutable.Map[String, UserInfo]()

  //TODO test mock user
  storage.put("accesskey", UserInfo("testuser", "secretkey", Seq("testgroup", "groupTwo"), "arn"))

  def isCredentialActive(accessKey: String, sessionToken: String): Future[Boolean] = synchronized {
    Future.successful(storage.get(accessKey).isDefined && "okSessionToken".equals(sessionToken))
  }

  def getUserInfo(accessKey: String): Future[Option[UserInfo]] = synchronized {
    Future.successful(storage.get(accessKey))
  }

  def addUserInfo(accessKey: String, sessionToken: String, userInfo: UserInfo): Unit = synchronized {
    storage.put(accessKey, userInfo)
  }
}
