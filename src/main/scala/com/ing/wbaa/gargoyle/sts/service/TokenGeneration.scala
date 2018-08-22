package com.ing.wbaa.gargoyle.sts.service

import java.time.Instant
import java.util.concurrent.TimeUnit

import com.ing.wbaa.gargoyle.sts.data.aws
import com.ing.wbaa.gargoyle.sts.data.aws._

import scala.concurrent.duration.Duration
import scala.util.Random

object TokenGeneration {
  def generateAwsCredential: AwsCredential = aws.AwsCredential(
    AwsAccessKey(Random.alphanumeric.take(32).mkString("")),
    AwsSecretKey(Random.alphanumeric.take(32).mkString(""))
  )

  val defaultSessionDuration = Duration(8, TimeUnit.HOURS)
  val maxSessionDuration = Duration(8, TimeUnit.HOURS)

  def generateAwsSession(duration: Option[Duration]): AwsSession = {
    val tokenDuration = duration.filter(_ < maxSessionDuration).getOrElse(defaultSessionDuration)
    AwsSession(
      sessionToken = AwsSessionToken(Random.alphanumeric.take(32).mkString("")),
      expiration = AwsSessionTokenExpiration(Instant.now().plusMillis(tokenDuration.toMillis))
    )
  }
}
