package ing.wbaa.s3.service

import ing.wbaa.s3.sts.service.TokenServiceImpl
import org.scalatest.{ Matchers, WordSpec }

class TokenServiceTest extends WordSpec with Matchers {

  val tokenService = new TokenServiceImpl

  //TODO it is a mock test
  "Token service" should {
    "get an assume role" in {
      tokenService.getAssumeRoleWithWebIdentity("arn", "roleSession", "token", 100).get.toString.isEmpty shouldBe false
    }

    "get a session token" in {
      tokenService.getSessionToken(1000).get.toString.isEmpty shouldBe false
    }

  }

}
