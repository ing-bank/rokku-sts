package com.ing.wbaa.gargoyle.sts.data.aws

import scala.concurrent.duration.Duration

case class AwsAccessKey(value: String) extends AnyVal
case class AwsSecretKey(value: String) extends AnyVal
case class AwsSessionToken(value: String) extends AnyVal
case class AwsSessionTokenExpiration(value: Duration) extends AnyVal
case class AwsSession(sessionToken: AwsSessionToken, expiration: AwsSessionTokenExpiration)

case class AwsCredentialWithToken(
    accessKey: AwsAccessKey,
    secretKey: AwsSecretKey,
    session: AwsSession
)
