package com.ing.wbaa.rokku.sts.data.aws

case class AwsAccessKey(value: String) extends AnyVal

case class AwsSecretKey(value: String) extends AnyVal

case class AwsCredential(
  accessKey: AwsAccessKey,
  secretKey: AwsSecretKey
)

case class AwsCredentialWithToken(
  awsCredential: AwsCredential,
  session: AwsSession
)
