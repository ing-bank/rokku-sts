package com.ing.wbaa.airlock.sts.data

import com.ing.wbaa.airlock.sts.data.aws.{ AwsAccessKey, AwsSecretKey }

case class UserName(value: String) extends AnyVal

case class UserAssumedGroup(value: String) extends AnyVal

case class STSUserInfo(
    userName: UserName,
    assumedGroup: Option[UserAssumedGroup],
    awsAccessKey: AwsAccessKey,
    awsSecretKey: AwsSecretKey)
