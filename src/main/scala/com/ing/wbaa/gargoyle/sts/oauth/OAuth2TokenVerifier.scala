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
  def verifyToken(token: String): Future[VerifiedToken]
}

/**
 * Test implementation of OAuth2 token verifier
 */
class OAuth2TokenVerifierImpl extends OAuth2TokenVerifier {

  import scala.concurrent.ExecutionContext.Implicits.global

  override def verifyToken(token: String): Future[VerifiedToken] = {
    if ("validToken".equals(token)) Future[VerifiedToken](VerifiedToken(token, "id", "name", "username", "email", Seq.empty, 0))
    else Future.failed(new Exception("invalid token"))
  }
}
