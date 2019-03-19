package com.ing.wbaa.airlock.sts.util

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.ing.wbaa.airlock.sts.config.StsSettings

import scala.util.{Failure, Success, Try}

trait JwtToken {
  protected[this] def stsSettings: StsSettings

  def verifyInternalToken(bearerToken: String): Boolean =
    Try {
      val algorithm = Algorithm.HMAC256(stsSettings.decodeSecret)
      val verifier = JWT.require(algorithm)
        .withIssuer("airlock")
        .build()
      verifier.verify(bearerToken)
    } match {
      case Success(t) =>
        if (t.getClaim("service").asString() == "airlock") true
        else false
      case Failure(exception) => throw exception
    }

}
