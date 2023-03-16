package com.ing.wbaa.rokku.sts.data

import com.ing.wbaa.rokku.sts.data.aws.AwsCredential

case class UserAccount(
    username: Username,
    credentials: Option[AwsCredential],
    status: AccountStatus,
    isNpa: NPA,
    groups: Set[UserGroup]
)
