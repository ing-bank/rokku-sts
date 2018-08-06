package com.ing.wbaa.gargoyle.sts.service

import java.util.UUID

case class CredentialsResponse(
    sessionToken: String,
    secretAccessKey: String,
    expiration: Long,
    accessKeyId: String,
    requestId: String)

case class AssumeRoleWithWebIdentityResponse(
    subjectFromWebIdentityToken: String,
    audience: String,
    assumedRoleUser: AssumedRoleUser,
    credentialsResponse: CredentialsResponse,
    provider: String)

case class AssumedRoleUser(arn: String, assumedRoleId: String)

trait TokenService {
  /**
   *
   * @param roleArn          - The Amazon Resource Name (ARN) of the role that the caller is assuming.
   *                         Length Constraints: Minimum length of 20. Maximum length of 2048.
   *                         Pattern: [\u0009\u000A\u000D\u0020-\u007E\u0085\u00A0-\uD7FF\uE000-\uFFFD\u10000-\u10FFFF]+
   * @param roleSessionName  - An identifier for the assumed role session.
   *                         Typically, you pass the name or identifier that is associated with the user who is using your application.
   *                         That way, the temporary security credentials that your application will use are associated with that user.
   *                         This session name is included as part of the ARN and assumed role ID in the AssumedRoleUser response element.
   *                         The regex used to validate this parameter is a string of characters consisting of
   *                         upper- and lower-case alphanumeric characters with no spaces.
   *                         You can also include underscores or any of the following characters: =,.@-
   *                         Length Constraints: Minimum length of 2. Maximum length of 64.
   *                         Pattern: [\w+=,.@-]*
   * @param webIdentityToken - The OAuth 2.0 access token or OpenID Connect ID token that is provided by the identity provider.
   *                         Length Constraints: Minimum length of 4. Maximum length of 2048.
   * @param durationSeconds  - The duration, in seconds, of the role session.
   *                         Valid Range: Minimum value of 900. Maximum value of 43200.
   * @return optional AssumeRoleWithWebIdentityResponse
   */
  def getAssumeRoleWithWebIdentity(
      roleArn: String,
      roleSessionName: String,
      webIdentityToken: String,
      durationSeconds: Int): Option[AssumeRoleWithWebIdentityResponse]

  /**
   *
   * @param durationSeconds - The duration, in seconds, of the role session.
   *                        Valid Range: Minimum value of 900. Maximum value of 43200.
   * @return optional CredentialsResponse
   */
  def getSessionToken(durationSeconds: Int): Option[CredentialsResponse]
}

/**
 * Simple s3 token service implementation for test
 */
class TokenServiceImpl extends TokenService {

  override def getAssumeRoleWithWebIdentity(
      roleArn: String,
      roleSessionName: String,
      webIdentityToken: String,
      durationSeconds: Int): Option[AssumeRoleWithWebIdentityResponse] = {
    getCredentials.flatMap(credentials =>
      Some(AssumeRoleWithWebIdentityResponse(
        subjectFromWebIdentityToken,
        audience,
        assumedRoleUser(roleArn, roleSessionName),
        CredentialsResponse(
          credentials.sessionToken,
          credentials.secretKey,
          expirationTime(durationSeconds),
          credentials.accessKey,
          requestId),
        providerID)))
  }

  override def getSessionToken(durationSeconds: Int): Option[CredentialsResponse] = {
    getCredentials.flatMap(credentials =>
      Some(CredentialsResponse(
        credentials.sessionToken,
        credentials.secretKey,
        expirationTime(durationSeconds),
        credentials.accessKey,
        requestId)))
  }

  private case class Credentials(accessKey: String, secretKey: String, sessionToken: String)

  //TODO now it is a mock implementation
  private def getCredentials = {
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
  private def providerID = "ing.wbaa"

  /**
   * aws doc:
   * The unique user identifier that is returned by the identity provider.
   * This identifier is associated with the WebIdentityToken that was submitted with the AssumeRoleWithWebIdentity call.
   * The identifier is typically unique to the user and the application that acquired the WebIdentityToken (pairwise identifier).
   * For OpenID Connect ID tokens, this field contains the value returned by the identity provider as the token's sub (Subject) claim
   */
  private def subjectFromWebIdentityToken = {
    "SubjectFromWebIdentityToken"
  }

  /**
   * aws doc:
   * The intended audience (also known as client ID) of the web identity token.
   * This is traditionally the client identifier issued to the application that requested the web identity token.
   */
  private def audience = "" +
    "audience"

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

