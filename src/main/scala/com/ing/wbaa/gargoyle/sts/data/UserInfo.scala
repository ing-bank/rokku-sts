package com.ing.wbaa.gargoyle.sts.data

case class UserName(value: String) extends AnyVal

case class UserGroup(value: String) extends AnyVal

case class UserInfo(
    userName: UserName,
    assumedGroups: Option[UserGroup])
