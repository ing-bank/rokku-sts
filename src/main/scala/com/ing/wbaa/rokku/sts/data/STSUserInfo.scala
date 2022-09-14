package com.ing.wbaa.rokku.sts.data

import com.ing.wbaa.rokku.sts.data.aws.{ AwsAccessKey, AwsSecretKey }

case class Username(value: String) extends AnyVal

case class UserAssumeRole(value: String) extends AnyVal

case class STSUserInfo(
    userName: Username,
    userGroup: Set[UserGroup],
    awsAccessKey: AwsAccessKey,
    awsSecretKey: AwsSecretKey,
    userRole: Option[UserAssumeRole])

sealed trait TokenStatus
case object TokenActive extends TokenStatus
case class TokenActiveForRole(role: UserAssumeRole) extends TokenStatus
case object TokenNotActive extends TokenStatus
