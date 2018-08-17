package com.ing.wbaa.gargoyle.sts.data

case class UserInfo(
    userId: String,
    secretKey: String,
    groups: Seq[String],
    arn: String)
