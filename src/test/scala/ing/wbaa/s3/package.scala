package ing.wbaa

import ing.wbaa.s3.sts.service.UserInfo

package object s3 {
  val okAccessKey = "okAccessKey"
  val okSessionToken = "okSessionToken"
  val badAccessKey = "BadAccessKey"
  val badSessionToken = "BadSessionToken"
  val okUserId = "userOk"
  val okSecretKey = "okSecretKey"
  val groups = List("group1", "group2")
  val arn = "arn:ing-wbaa:iam:::role/TheRole"
  val okUserInfo = UserInfo(okUserId, okSecretKey, groups, arn)
}
