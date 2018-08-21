package com.ing.wbaa

import com.ing.wbaa.gargoyle.sts.data._
import com.ing.wbaa.gargoyle.sts.data.aws.{ AwsAccessKey, AwsSecretKey, AwsSessionToken }

package object gargoyle {
  val okAccessKey = AwsAccessKey("okAccessKey")
  val okSessionToken = AwsSessionToken("okSessionToken")
  val badAccessKey = AwsAccessKey("BadAccessKey")
  val badSessionToken = AwsSessionToken("BadSessionToken")
  val okUserName = "userOk"
  val okSecretKey = AwsSecretKey("okSecretKey")
  val groups = Set("group1", "group2")
  val arn = "arn:ing-wbaa:iam:::role/TheRole"
  val okUserInfo = UserInfo(okUserName, groups)
}
