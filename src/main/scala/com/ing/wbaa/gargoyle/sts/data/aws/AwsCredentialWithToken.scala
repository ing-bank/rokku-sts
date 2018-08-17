package com.ing.wbaa.gargoyle.sts.data.aws

import scala.concurrent.duration.Duration

case class AwsSessionToken(sessionToken: String, expiration: Duration)

case class AwsCredentialWithToken(
    accessKey: AwsAccessKey,
    secretKey: AwsSecretKey,
    sessionToken: AwsSessionToken
)
