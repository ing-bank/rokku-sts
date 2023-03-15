package com.ing.wbaa.rokku.sts.util

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.ing.wbaa.rokku.sts.config.StsSettings
import com.ing.wbaa.rokku.sts.data.RequestId
import com.ing.wbaa.rokku.sts.handler.LoggerHandlerWithId

import scala.util.{ Failure, Success, Try }
import akka.http.scaladsl.server.{ Route, AuthorizationFailedRejection, MalformedHeaderRejection, Directives }

trait JwtToken {
  protected[this] def stsSettings: StsSettings
  private val logger = new LoggerHandlerWithId

  def verifyInternalToken(bearerToken: String)(inner: Route)(implicit id: RequestId): Route =
    Try {
      val algorithm = Algorithm.HMAC256(stsSettings.decodeSecret)
      val verifier = JWT.require(algorithm)
        .withIssuer("rokku")
        .build()
      verifier.verify(bearerToken)
    } match {
      case Success(t) =>
        val serviceName = t.getClaim("service").asString()
        if (serviceName == "rokku") {
          logger.debug("Successfully verified internal token for {}", serviceName)
          inner
        } else {
          logger.warn("Failed to verify internal token={}", bearerToken)
          Directives.reject(AuthorizationFailedRejection)
        }
      case Failure(exception) =>
        logger.warn("jwt token exception - {}", exception.getMessage)
        Directives.reject(MalformedHeaderRejection("bearer token", s"malformed token=$bearerToken"))
    }

}
