package com.ing.wbaa.gargoyle.sts.service

case class CredentialsResponse(
    sessionToken: String,
    secretAccessKey: String,
    expiration: Long,
    accessKeyId: String,
    requestId: String)

case class AssumeRoleWithWebIdentityResponse(
    subjectFromWebIdentityToken: String,
    audience: String,
    assumedRoleUser: AssumedRoleUser,
    credentialsResponse: CredentialsResponse,
    provider: String)

case class AssumedRoleUser(arn: String, assumedRoleId: String)

trait TokenService {
  def getAssumeRoleWithWebIdentity(
      roleArn: String,
      roleSessionName: String,
      webIdentityToken: String,
      durationSeconds: Int): Option[AssumeRoleWithWebIdentityResponse]

  def getSessionToken(durationSeconds: Int): Option[CredentialsResponse]
}

/**
 * Simple s3 token service implementation for test
 */
class TokenServiceImpl extends TokenService {

  override def getAssumeRoleWithWebIdentity(
      roleArn: String,
      roleSessionName: String,
      webIdentityToken: String,
      durationSeconds: Int): Option[AssumeRoleWithWebIdentityResponse] =
    Some(AssumeRoleWithWebIdentityResponse(
      "amzn1.account.AF6RHO7KZU5XRVQJGXK6HB56KR2A",
      "client.5498841531868486423.1548@apps.example.com",
      AssumedRoleUser(
        "arn:aws:sts::123456789012:assumed-role/FederatedWebIdentityRole/app1",
        "AROACLKWSDQRAOEXAMPLE:app1"),
      CredentialsResponse(
        "okSessionToken",
        "secretkey",
        System.currentTimeMillis() + durationSeconds * 1000,
        "accesskey",
        "ad4156e9-bce1-11e2-82e6-6b6efEXAMPLE"),
      "ing.wbaa"))

  override def getSessionToken(durationSeconds: Int): Option[CredentialsResponse] =
    Some(CredentialsResponse(
      "okSessionToken",
      "secretkey",
      System.currentTimeMillis() + durationSeconds * 1000,
      "accesskey",
      "58c5dbae-abef-11e0-8cfe-09039844ac7d"))
}

