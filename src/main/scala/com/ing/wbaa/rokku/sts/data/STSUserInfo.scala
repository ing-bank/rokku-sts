package com.ing.wbaa.rokku.sts.data

import com.ing.wbaa.rokku.sts.data.aws.{AwsAccessKey, AwsSecretKey}

case class UserName(value: String) extends AnyVal

case class STSUserInfo(
  userName: UserName,
  userGroup: Set[UserGroup],
  awsAccessKey: AwsAccessKey,
  awsSecretKey: AwsSecretKey
)
