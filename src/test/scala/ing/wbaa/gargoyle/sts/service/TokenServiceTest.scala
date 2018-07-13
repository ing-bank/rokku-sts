package ing.wbaa.gargoyle.sts.service

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
