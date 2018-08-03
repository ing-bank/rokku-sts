package com.ing.wbaa.gargoyle.sts.service

import java.time.format.DateTimeFormatter
import java.time.{ Instant, LocalDateTime, ZoneId, ZoneOffset }

import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport

import scala.language.implicitConversions
import scala.xml._

trait TokenXML extends ScalaXmlSupport {

  implicit def getSessionTokenResponseToXML(credentials: CredentialsResponse): NodeSeq = {
    <GetSessionTokenResponse>
      <GetSessionTokenResult>{ credentialToXml(credentials) }</GetSessionTokenResult>
      <ResponseMetadata>
        <RequestId>58c5dbae-abef-11e0-8cfe-09039844ac7d</RequestId>
      </ResponseMetadata>
    </GetSessionTokenResponse>
  }

  implicit def assumeRoleWithWebIdentityResponseToXML(aRWWIResponse: AssumeRoleWithWebIdentityResponse): NodeSeq = {
    <AssumeRoleWithWebIdentityResponse>
      <AssumeRoleWithWebIdentityResult>
        <SubjectFromWebIdentityToken>{ aRWWIResponse.subjectFromWebIdentityToken }</SubjectFromWebIdentityToken>
        <Audience>{ aRWWIResponse.audience }</Audience>
        { assumedRoleUserToXml(aRWWIResponse.assumedRoleUser) }
        { credentialToXml(aRWWIResponse.credentialsResponse) }
        <Provider>{ aRWWIResponse.provider }</Provider>
      </AssumeRoleWithWebIdentityResult>
      <ResponseMetadata>
        <RequestId>ad4156e9-bce1-11e2-82e6-6b6efEXAMPLE</RequestId>
      </ResponseMetadata>
    </AssumeRoleWithWebIdentityResponse>
  }

  private def assumedRoleUserToXml(assumedRoleUser: AssumedRoleUser): NodeSeq = {
    <AssumedRoleUser>
      <Arn>{ assumedRoleUser.arn }</Arn>
      <AssumedRoleId>{ assumedRoleUser.assumedRoleId }</AssumedRoleId>
    </AssumedRoleUser>
  }

  private def credentialToXml(credentials: CredentialsResponse): NodeSeq = {
    <Credentials>
      <SessionToken>{ credentials.sessionToken }</SessionToken>
      <SecretAccessKey>{ credentials.secretAccessKey }</SecretAccessKey>
      <Expiration>{ Instant.ofEpochMilli(credentials.expiration) }</Expiration>
      <AccessKeyId>{ credentials.accessKeyId }</AccessKeyId>
    </Credentials>
  }
}

