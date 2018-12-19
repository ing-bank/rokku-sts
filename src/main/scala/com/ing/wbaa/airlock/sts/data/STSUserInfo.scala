package com.ing.wbaa.airlock.sts.data

import com.ing.wbaa.airlock.sts.data.aws.{ AwsAccessKey, AwsSecretKey }

case class UserName(value: String) extends AnyVal

// TODO: replace type of userGroup to Set[UserGroup]
case class STSUserInfo(
    userName: UserName,
    userGroup: Option[UserGroup],
    awsAccessKey: AwsAccessKey,
    awsSecretKey: AwsSecretKey)
