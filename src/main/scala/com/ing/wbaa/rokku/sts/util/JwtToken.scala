package com.ing.wbaa.rokku.sts.util

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.ing.wbaa.rokku.sts.config.StsSettings
import com.typesafe.scalalogging.LazyLogging

import scala.util.{Failure, Success, Try}

trait JwtToken extends LazyLogging {
  protected[this] def stsSettings: StsSettings

  def verifyInternalToken(bearerToken: String): Boolean =
    Try {
      val algorithm = Algorithm.HMAC256(stsSettings.decodeSecret)
      val verifier = JWT
        .require(algorithm)
        .withIssuer("rokku")
        .build()
      verifier.verify(bearerToken)
    } match {
      case Success(t) =>
        val serviceName = t.getClaim("service").asString()
        if (serviceName == "rokku") {
          logger.debug(s"Successfully verified internal token for $serviceName")
          true
        } else {
          logger.debug(s"Failed to verify internal token")
          false
        }
      case Failure(exception) => throw exception
    }

}
