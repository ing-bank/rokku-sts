package com.ing.wbaa.gargoyle.sts.data

case class CredentialsResponse(
    sessionToken: String,
    secretAccessKey: String,
    expiration: Long,
    accessKeyId: String,
    requestId: String)
