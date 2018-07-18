package ing.wbaa.gargoyle.sts.service

import scala.collection.mutable

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
  def isCredentialActive(accessKey: String, sessionToken: String): Boolean

  /**
   * Get the user info
   *
   * @param accessKey    - the user access key
   * @param sessionToken the user token
   * @return the user info or None if the user credential are not active
   */
  def getUserInfo(accessKey: String, sessionToken: String): Option[UserInfo]
}

/**
 * Simple user service implementation for test
 */
class UserServiceImpl extends UserService {

  private val storage = mutable.Map[String, UserInfo]()

  //TODO test mock user
  storage.put(userKey("okAccessKey","okSessionToken"), UserInfo("testUser", "secretKey", Seq("groupOne", "groupTwo"), "arn"))

  private def userKey(accessKey: String, sessionKey: String): String = s"$accessKey-$sessionKey"

  override def isCredentialActive(accessKey: String, sessionToken: String): Boolean = {
    storage.get(userKey(accessKey, sessionToken)).isDefined
  }

  override def getUserInfo(accessKey: String, sessionToken: String): Option[UserInfo] = {
    storage.get(userKey(accessKey, sessionToken))
  }

  override def addUserInfo(accessKey: String, sessionToken: String, userInfo: UserInfo): Unit = {
    storage.put(userKey(accessKey, sessionToken), userInfo)
  }
}
