package com.ing.wbaa.gargoyle.sts.service

import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class TokenServiceTest extends WordSpec
  with Matchers
  with ScalaFutures
  with TokenService {

  val timeout = Timeout(1.second)
  import com.ing.wbaa.gargoyle._

  "Token service" should {
    "get an assume role" in {
      val durationSeconds = 3600
      val assumeRole = getAssumeRoleWithWebIdentity("arn", "roleSession", verifiedToken, durationSeconds)
        .futureValue(timeout).get

      assumeRole.subjectFromWebIdentityToken shouldBe "SubjectFromWebIdentityToken - user ID"
      assumeRole.audience shouldBe "audience user ID"
      assumeRole.assumedRoleUser.arn shouldBe "arn/roleSession"
      assumeRole.assumedRoleUser.assumedRoleId shouldBe "id:roleSession"
      assumeRole.credentialsResponse.sessionToken shouldBe "okSessionToken"
      assumeRole.credentialsResponse.secretAccessKey shouldBe "secretkey"
      assumeRole.credentialsResponse.expiration shouldBe <=(System.currentTimeMillis() + durationSeconds * 1000)
      assumeRole.credentialsResponse.accessKeyId shouldBe "accesskey"
      assumeRole.credentialsResponse.requestId should not be empty
      assumeRole.provider shouldBe "keyclock.wbaa.ing"
    }

    "get a session token" in {
      val durationSeconds = 3600
      val credentials = getSessionToken(verifiedToken, durationSeconds)
        .futureValue(timeout).get
      credentials.sessionToken shouldBe "okSessionToken"
      credentials.secretAccessKey shouldBe "secretkey"
      credentials.expiration shouldBe <=(System.currentTimeMillis() + durationSeconds * 1000)
      credentials.accessKeyId shouldBe "accesskey"
      credentials.requestId should not be empty
    }
  }

  override implicit def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
}
