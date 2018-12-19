package com.ing.wbaa.airlock.sts.api.xml

import java.util.UUID

import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport
import com.ing.wbaa.airlock.sts.data.aws.AwsCredentialWithToken

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

  private def credentialToXml(awsCredentialWithToken: AwsCredentialWithToken): NodeSeq = {
    <Credentials>
      <SessionToken>{ awsCredentialWithToken.session.sessionToken.value }</SessionToken>
      <SecretAccessKey>{ awsCredentialWithToken.awsCredential.secretKey.value }</SecretAccessKey>
      <Expiration>{ awsCredentialWithToken.session.expiration.value }</Expiration>
      <AccessKeyId>{ awsCredentialWithToken.awsCredential.accessKey.value }</AccessKeyId>
    </Credentials>
  }
}
