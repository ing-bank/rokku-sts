package com.ing.wbaa

import com.ing.wbaa.gargoyle.sts.service.{ AssumeRoleWithWebIdentityResponse, AssumedRoleUser, CredentialsResponse, UserInfo }

package object gargoyle {
  val okAccessKey = "okAccessKey"
  val okSessionToken = "okSessionToken"
  val badAccessKey = "BadAccessKey"
  val badSessionToken = "BadSessionToken"
  val okUserId = "userOk"
  val okSecretKey = "okSecretKey"
  val groups = List("group1", "group2")
  val arn = "arn:ing-wbaa:iam:::role/TheRole"
  val okUserInfo = UserInfo(okUserId, okSecretKey, groups, arn)
  val assumeRoleWithWebIdentityResponse = AssumeRoleWithWebIdentityResponse(
    "amzn1.account.AF6RHO7KZU5XRVQJGXK6HB56KR2A",
    "client.5498841531868486423.1548@apps.example.com",
    AssumedRoleUser(
      "arn:aws:sts::123456789012:assumed-role/FederatedWebIdentityRole/app1",
      "AROACLKWSDQRAOEXAMPLE:app1"),
    CredentialsResponse(
      "okSessionToken",
      "secretKey",
      3601,
      "okAccessKey",
      "ad4156e9-bce1-11e2-82e6-6b6efEXAMPLE"),
    "ing.wbaa")
  val credentialsResponse = CredentialsResponse(
    "okSessionToken",
    "secretKey",
    3601,
    "okAccessKey",
    "58c5dbae-abef-11e0-8cfe-09039844ac7d")
}
