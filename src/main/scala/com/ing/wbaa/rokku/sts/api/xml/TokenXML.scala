package com.ing.wbaa.rokku.sts.api.xml

import java.util.UUID

import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport
import com.ing.wbaa.rokku.sts.data.AuthenticationTokenId
import com.ing.wbaa.rokku.sts.data.aws.{ AwsCredentialWithToken, AwsRoleArn }

import scala.xml.NodeSeq

trait TokenXML extends ScalaXmlSupport {

  private def requestId = UUID.randomUUID().toString

  protected def getSessionTokenResponseToXML(awsCredentialWithToken: AwsCredentialWithToken): NodeSeq = {
    <GetSessionTokenResponse>
      <GetSessionTokenResult>{ credentialToXml(awsCredentialWithToken) }</GetSessionTokenResult>
      <ResponseMetadata>
        <RequestId>{ requestId }</RequestId>
      </ResponseMetadata>
    </GetSessionTokenResponse>
  }

  protected def assumeRoleResponseToXML(
      awsCredentialWithToken: AwsCredentialWithToken,
      roleArn: AwsRoleArn,
      roleSessionName: String,
      keycloakTokenId: AuthenticationTokenId
  ): NodeSeq = {
    <AssumeRoleResponse>
      <AssumeRoleResult>
        { assumedRoleUserToXml(roleArn, roleSessionName) }
        { credentialToXml(awsCredentialWithToken) }
        <Provider>{ providerID }</Provider>
      </AssumeRoleResult>
      <ResponseMetadata>
        <RequestId>{ requestId }</RequestId>
      </ResponseMetadata>
    </AssumeRoleResponse>
  }

  /**
   * aws doc:
   * The issuing authority of the web identity token presented. For OpenID Connect ID tokens,
   * this contains the value of the iss field. For OAuth 2.0 access tokens,
   * this contains the value of the ProviderId parameter that was passed in the AssumeRoleWithWebIdentity request.
   */
  private val providerID = "keycloak.wbaa.ing"

  private def credentialToXml(awsCredentialWithToken: AwsCredentialWithToken): NodeSeq = {
    <Credentials>
      <SessionToken>{ awsCredentialWithToken.session.sessionToken.value }</SessionToken>
      <SecretAccessKey>{ awsCredentialWithToken.awsCredential.secretKey.value }</SecretAccessKey>
      <Expiration>{ awsCredentialWithToken.session.expiration.value }</Expiration>
      <AccessKeyId>{ awsCredentialWithToken.awsCredential.accessKey.value }</AccessKeyId>
    </Credentials>
  }

  /**
   * aws doc:
   * The Amazon Resource Name (ARN) and the assumed role ID,
   * which are identifiers that you can use to refer to the resulting temporary security credentials.
   * For example, you can reference these credentials as a principal in a resource-based policy
   * by using the ARN or assumed role ID.
   * The ARN and ID include the RoleSessionName that you specified when you called AssumeRole
   *
   * @param roleArn         - arn role e.g. arn:rokku:sts...
   * @param roleSessionName - the session name
   * @return
   */
  private def assumedRoleUserToXml(roleArn: AwsRoleArn, roleSessionName: String): NodeSeq = {
    <AssumedRoleUser>
      <Arn>{ s"${roleArn.arn}/$roleSessionName" }</Arn>
      <AssumedRoleId>{ s"id:$roleSessionName" }</AssumedRoleId>
    </AssumedRoleUser>
  }
}
