package com.ing.wbaa.gargoyle.sts.data

case class UserName(value: String) extends AnyVal

case class UserGroup(value: String) extends AnyVal

case class UserInfo(
    keycloakTokenId: String,
    userName: UserName,
    userGroups: Set[UserGroup])
