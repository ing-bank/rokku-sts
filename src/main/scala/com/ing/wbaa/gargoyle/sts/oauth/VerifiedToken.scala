package com.ing.wbaa.gargoyle.sts.oauth

case class VerifiedToken(
    token: String,
    id: String,
    name: String,
    username: String,
    email: String,
    roles: Seq[String],
    expirationDate: Long)
