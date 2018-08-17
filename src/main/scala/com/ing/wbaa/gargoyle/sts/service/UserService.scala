package com.ing.wbaa.gargoyle.sts.service

import com.ing.wbaa.gargoyle.sts.data.UserInfo

import scala.collection.mutable
import scala.concurrent.Future

/**
 * User service providing information about users
 */
trait UserService {

  private[this] val storage = mutable.Map[String, UserInfo]()

  //TODO test mock user
  //  storage.put("accesskey", UserInfo("testuser", "secretkey", Seq("testgroup", "groupTwo")))

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
