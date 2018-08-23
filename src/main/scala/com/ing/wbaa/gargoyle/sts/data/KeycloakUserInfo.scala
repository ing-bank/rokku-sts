package com.ing.wbaa.gargoyle.sts.data

case class KeycloakTokenId(value: String) extends AnyVal

case class KeycloakUserInfo(
    userName: UserName,
    userGroups: Set[UserGroup],
    keycloakTokenId: KeycloakTokenId)
