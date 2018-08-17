package com.ing.wbaa.gargoyle.sts.service

import java.util.UUID

import com.ing.wbaa.gargoyle.sts.data.{ AssumeRoleWithWebIdentityResponse, AssumedRoleUser, CredentialsResponse, VerifiedToken }

import scala.concurrent.{ ExecutionContext, Future }

trait TokenService {

  implicit def executionContext: ExecutionContext

  def getAssumeRoleWithWebIdentity(
      roleArn: String,
      roleSessionName: String,
      token: VerifiedToken,
      durationSeconds: Int): Future[Option[AssumeRoleWithWebIdentityResponse]] = {
    Future(getCredentials(token).map(credentials =>
      AssumeRoleWithWebIdentityResponse(
        subjectFromWebIdentityToken(token),
        audience(token),
        assumedRoleUser(roleArn, roleSessionName),
        CredentialsResponse(
          credentials.sessionToken,
          credentials.secretKey,
          expirationTime(durationSeconds),
          credentials.accessKey,
          requestId),
        providerID)))
  }

  def getSessionToken(token: VerifiedToken, durationSeconds: Int): Future[Option[CredentialsResponse]] = {
    Future(getCredentials(token).map(credentials =>
      CredentialsResponse(
        credentials.sessionToken,
        credentials.secretKey,
        expirationTime(durationSeconds),
        credentials.accessKey,
        requestId)))
  }

  private case class Credentials(accessKey: String, secretKey: String, sessionToken: String)

  private def getCredentials(token: VerifiedToken) = {
    //TODO ranger authorization ??
    Some(Credentials("accesskey", "secretkey", "okSessionToken"))
  }

  private def expirationTime(durationSeconds: Int) = {
    System.currentTimeMillis() + durationSeconds * 1000
  }

  private def requestId = UUID.randomUUID().toString

  /**
   * aws doc:
   * The issuing authority of the web identity token presented. For OpenID Connect ID tokens,
   * this contains the value of the iss field. For OAuth 2.0 access tokens,
   * this contains the value of the ProviderId parameter that was passed in the AssumeRoleWithWebIdentity request.
   */
  private def providerID = "keyclock.wbaa.ing"

  /**
   * aws doc:
   * The unique user identifier that is returned by the identity provider.
   * This identifier is associated with the WebIdentityToken that was submitted with the AssumeRoleWithWebIdentity call.
   * The identifier is typically unique to the user and the application that acquired the WebIdentityToken (pairwise identifier).
   * For OpenID Connect ID tokens, this field contains the value returned by the identity provider as the token's sub (Subject) claim
   */
  private def subjectFromWebIdentityToken(token: VerifiedToken) = {
    s"SubjectFromWebIdentityToken - ${token.id}"
  }

  /**
   * aws doc:
   * The intended audience (also known as client ID) of the web identity token.
   * This is traditionally the client identifier issued to the application that requested the web identity token.
   */
  private def audience(token: VerifiedToken) = "" +
    s"audience ${token.id}"

  /**
   * aws doc:
   * The Amazon Resource Name (ARN) and the assumed role ID,
   * which are identifiers that you can use to refer to the resulting temporary security credentials.
   * For example, you can reference these credentials as a principal in a resource-based policy
   * by using the ARN or assumed role ID.
   * The ARN and ID include the RoleSessionName that you specified when you called AssumeRole
   *
   * @param roleArn         - arn role e.g. arn:gargoyle:sts...
   * @param roleSessionName - the session name
   * @return
   */
  private def assumedRoleUser(roleArn: String, roleSessionName: String) =
    AssumedRoleUser(s"$roleArn/$roleSessionName", s"id:$roleSessionName")
}

