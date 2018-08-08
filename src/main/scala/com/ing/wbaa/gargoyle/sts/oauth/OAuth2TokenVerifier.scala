package com.ing.wbaa.gargoyle.sts.oauth

import scala.concurrent.Future

case class VerifiedToken(
    token: String,
    id: String,
    name: String,
    username: String,
    email: String,
    roles: Seq[String],
    expirationDate: Long)

trait OAuth2TokenVerifier {
  def verifyToken(token: BearerToken): Future[VerifiedToken]
}

/**
 * Test implementation of OAuth2 token verifier
 */
trait OAuth2TokenVerifierImpl extends OAuth2TokenVerifier {

  import scala.concurrent.ExecutionContext.Implicits.global

  override def verifyToken(token: BearerToken): Future[VerifiedToken] = {
    if ("validToken".equals(token.value)) Future[VerifiedToken](VerifiedToken(token.value, "id", "name", "username", "email", Seq.empty, 0))
    else Future.failed(new Exception("invalid token"))
  }
}
