package com.ing.wbaa.rokku.sts.data

case class AuthenticationTokenId(value: String) extends AnyVal

case class UserGroup(value: String) extends AnyVal

case class AuthenticationUserInfo(
    userName: Username,
    userGroups: Set[UserGroup],
    keycloakTokenId: AuthenticationTokenId,
    userRoles: Set[UserAssumeRole],
    isNPA: Boolean)
