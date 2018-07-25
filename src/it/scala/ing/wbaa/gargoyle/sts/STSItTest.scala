package ing.wbaa.gargoyle.sts

import akka.http.scaladsl.model.StatusCodes
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.model.{AWSSecurityTokenServiceException, AssumeRoleWithWebIdentityRequest, GetSessionTokenRequest}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

class STSItTest extends WordSpec with Matchers with BeforeAndAfterAll with AWSSTSClient {

  var sts: AWSSecurityTokenService = stsClient()

  override protected def afterAll(): Unit = {
    sts.shutdown()
    stopWebServer()
  }

  "STS getSessionToken" should {
    "return credentials for valid token" in {
      val credentials = sts.getSessionToken(new GetSessionTokenRequest()
        .withTokenCode("validToken"))
        .getCredentials

      credentials.getAccessKeyId.stripMargin.trim shouldEqual "okAccessKey"
      credentials.getSecretAccessKey.stripMargin.trim shouldEqual "secretKey"
      credentials.getSessionToken.stripMargin.trim shouldEqual "okSessionToken"
      credentials.getExpiration.getTime shouldEqual 1562874929611L
    }

    "throw AWSSecurityTokenServiceException because invalid token" in {
      an [AWSSecurityTokenServiceException] should be thrownBy sts.getSessionToken(new GetSessionTokenRequest()
        .withTokenCode("invalidToken"))
        .getCredentials
    }
  }

  "STS assumeRoleWithWebIdentity" should {
    "return credentials for valid token" in {
      val credentials = sts.assumeRoleWithWebIdentity(new AssumeRoleWithWebIdentityRequest()
        .withRoleArn("arn")
        .withProviderId("provider")
        .withRoleSessionName("sessionName")
        .withWebIdentityToken("validToken"))
        .getCredentials

      credentials.getAccessKeyId.stripMargin.trim shouldEqual "okAccessKey"
      credentials.getSecretAccessKey.stripMargin.trim shouldEqual "secretKey"
      credentials.getSessionToken.stripMargin.trim shouldEqual "okSessionToken"
      credentials.getExpiration.getTime shouldEqual 1571958023000L
    }

    "throw AWSSecurityTokenServiceException because invalid token" in {
      an [AWSSecurityTokenServiceException] should be thrownBy sts.assumeRoleWithWebIdentity(new AssumeRoleWithWebIdentityRequest()
        .withRoleArn("arn")
        .withProviderId("provider")
        .withRoleSessionName("sessionName")
        .withWebIdentityToken("invalidToken"))
        .getCredentials
    }
  }
}
