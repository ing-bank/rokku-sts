package ing.wbaa.gargoyle.sts

import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.model.{AWSSecurityTokenServiceException, AssumeRoleWithWebIdentityRequest, GetSessionTokenRequest}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

class STSItTest extends WordSpec with Matchers with BeforeAndAfterAll with AWSSTSClient {

  val stsServer = new CoreActorSystem
    with Routes
    with Actors
    with Web

  val stsAwsClient: AWSSecurityTokenService = stsClient()

  override protected def afterAll(): Unit = {
    stsAwsClient.shutdown()
    stopSTSServer()
  }

  "STS getSessionToken" should {
    "return credentials for valid token" in {
      val credentials = stsAwsClient.getSessionToken(new GetSessionTokenRequest()
        .withTokenCode("validToken"))
        .getCredentials

      credentials.getAccessKeyId.stripMargin.trim shouldEqual "okAccessKey"
      credentials.getSecretAccessKey.stripMargin.trim shouldEqual "secretKey"
      credentials.getSessionToken.stripMargin.trim shouldEqual "okSessionToken"
      credentials.getExpiration.getTime shouldEqual 1562874929611L
    }

    "throw AWSSecurityTokenServiceException because invalid token" in {
      an[AWSSecurityTokenServiceException] should be thrownBy stsAwsClient.getSessionToken(new GetSessionTokenRequest()
        .withTokenCode("invalidToken"))
        .getCredentials
    }
  }

  "STS assumeRoleWithWebIdentity" should {
    "return credentials for valid token" in {
      val credentials = stsAwsClient.assumeRoleWithWebIdentity(new AssumeRoleWithWebIdentityRequest()
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
      an[AWSSecurityTokenServiceException] should be thrownBy stsAwsClient.assumeRoleWithWebIdentity(new AssumeRoleWithWebIdentityRequest()
        .withRoleArn("arn")
        .withProviderId("provider")
        .withRoleSessionName("sessionName")
        .withWebIdentityToken("invalidToken"))
        .getCredentials
    }
  }


  def stopSTSServer(): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    stsServer.webServer.flatMap(_.unbind())
      .onComplete { _ =>
        stsServer.materializer.shutdown()
        stsServer.system.terminate()
      }
  }
}
