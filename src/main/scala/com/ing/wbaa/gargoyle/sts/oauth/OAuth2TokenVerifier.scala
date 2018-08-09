package com.ing.wbaa.gargoyle.sts.oauth

import scala.concurrent.{ ExecutionContext, Future }

case class VerifiedToken(
    token: String,
    id: String,
    name: String,
    username: String,
    email: String,
    roles: Seq[String],
    expirationDate: Long)

/**
 * Test implementation of OAuth2 token verifier
 */
trait OAuth2TokenVerifier {

  implicit def executionContext: ExecutionContext

  def verifyToken(token: BearerToken): Future[VerifiedToken] = {
    if ("validToken".equals(token.value)) Future[VerifiedToken](VerifiedToken(token.value, "id", "name", "username", "email", Seq.empty, 0))
    else Future.failed(new Exception("invalid token"))
  }
}
