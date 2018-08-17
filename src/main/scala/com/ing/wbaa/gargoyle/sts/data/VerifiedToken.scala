package com.ing.wbaa.gargoyle.sts.data

case class VerifiedToken(
    token: String,
    id: String,
    name: String,
    username: String,
    email: String,
    roles: Seq[String],
    expirationDate: Long)
