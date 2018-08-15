package com.ing.wbaa.gargoyle.sts.data

case class AssumeRoleWithWebIdentityResponse(
    subjectFromWebIdentityToken: String,
    audience: String,
    assumedRoleUser: AssumedRoleUser,
    credentialsResponse: CredentialsResponse,
    provider: String)
