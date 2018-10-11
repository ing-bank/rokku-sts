package com.ing.wbaa.airlock.sts.data

case class AuthenticationTokenId(value: String) extends AnyVal

case class UserGroup(value: String) extends AnyVal

case class AuthenticationUserInfo(
    userName: UserName,
    userGroups: Set[UserGroup],
    keycloakTokenId: AuthenticationTokenId)
