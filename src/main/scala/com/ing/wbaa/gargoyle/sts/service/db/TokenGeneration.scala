package com.ing.wbaa.gargoyle.sts.service.db

import java.time.Instant

import com.ing.wbaa.gargoyle.sts.config.GargoyleStsSettings
import com.ing.wbaa.gargoyle.sts.data.aws
import com.ing.wbaa.gargoyle.sts.data.aws._

import scala.concurrent.duration.Duration
import scala.util.Random

trait TokenGeneration {

  protected[this] def stsSettings: GargoyleStsSettings

  protected[this] def generateAwsCredential: AwsCredential = aws.AwsCredential(
    AwsAccessKey(Random.alphanumeric.take(32).mkString),
    AwsSecretKey(Random.alphanumeric.take(32).mkString)
  )

  protected[this] def generateAwsSession(duration: Option[Duration]): AwsSession = {
    val tokenDuration = duration match {
      case None => stsSettings.defaultTokenSessionDuration
      case Some(durationRequested) =>
        if (durationRequested > stsSettings.maxTokenSessionDuration) stsSettings.maxTokenSessionDuration
        else durationRequested
    }

    AwsSession(
      sessionToken = AwsSessionToken(Random.alphanumeric.take(32).mkString),
      expiration = AwsSessionTokenExpiration(Instant.now().plusMillis(tokenDuration.toMillis))
    )
  }
}
