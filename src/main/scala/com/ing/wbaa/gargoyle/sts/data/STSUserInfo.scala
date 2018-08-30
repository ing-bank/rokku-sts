package com.ing.wbaa.gargoyle.sts.data

import com.ing.wbaa.gargoyle.sts.data.aws.{ AwsAccessKey, AwsSecretKey }

case class UserName(value: String) extends AnyVal

case class UserGroup(value: String) extends AnyVal

case class STSUserInfo(
    userName: UserName,
    assumedGroup: Option[UserGroup],
    awsAccessKey: AwsAccessKey,
    awsSecretKey: AwsSecretKey)
