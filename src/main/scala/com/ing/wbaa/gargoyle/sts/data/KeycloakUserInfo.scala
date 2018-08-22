package com.ing.wbaa.gargoyle.sts.data

case class KeycloakUserInfo(
    userName: UserName,
    userGroups: Set[UserGroup])
