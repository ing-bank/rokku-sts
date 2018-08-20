package com.ing.wbaa

import com.ing.wbaa.gargoyle.sts.data._

package object gargoyle {
  val okAccessKey = "okAccessKey"
  val okSessionToken = "okSessionToken"
  val badAccessKey = "BadAccessKey"
  val badSessionToken = "BadSessionToken"
  val okUserName = "userOk"
  val okSecretKey = "okSecretKey"
  val groups = Set("group1", "group2")
  val arn = "arn:ing-wbaa:iam:::role/TheRole"
  val okUserInfo = UserInfo(okUserName, groups)
}
