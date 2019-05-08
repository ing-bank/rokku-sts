package com.ing.wbaa.rokku.sts.data.aws

import java.time.Instant

case class AwsSessionToken(value: String) extends AnyVal

case class AwsSessionTokenExpiration(value: Instant) extends AnyVal

case class AwsSession(sessionToken: AwsSessionToken, expiration: AwsSessionTokenExpiration)
