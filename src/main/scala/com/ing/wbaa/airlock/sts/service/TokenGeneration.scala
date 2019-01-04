package com.ing.wbaa.airlock.sts.service

import java.security.SecureRandom
import java.time.Instant

import com.ing.wbaa.airlock.sts.config.StsSettings
import com.ing.wbaa.airlock.sts.data.aws
import com.ing.wbaa.airlock.sts.data.aws._

import scala.concurrent.duration.Duration
import scala.util.Random

trait TokenGeneration {

  private[this] lazy val rand = new Random(new SecureRandom())

  protected[this] def stsSettings: StsSettings

  protected[this] def generateAwsCredential: AwsCredential = aws.AwsCredential(
    AwsAccessKey(rand.alphanumeric.take(32).mkString),
    AwsSecretKey(rand.alphanumeric.take(32).mkString)
  )

  protected[this] def generateAwsSession(duration: Option[Duration]): AwsSession = {
    val tokenDuration = duration match {
      case None => stsSettings.defaultTokenSessionDuration
      case Some(durationRequested) =>
        if (durationRequested > stsSettings.maxTokenSessionDuration) stsSettings.maxTokenSessionDuration
        else durationRequested
    }

    AwsSession(
      sessionToken = AwsSessionToken(rand.alphanumeric.take(32).mkString),
      expiration = AwsSessionTokenExpiration(Instant.now().plusMillis(tokenDuration.toMillis))
    )
  }
}
