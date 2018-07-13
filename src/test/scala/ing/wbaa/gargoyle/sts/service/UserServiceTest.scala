package ing.wbaa.gargoyle.sts.service

import org.scalatest.{ Matchers, WordSpec }

class UserServiceTest extends WordSpec with Matchers {

  import ing.wbaa.gargoyle._

  val userService = new UserServiceImpl()

  userService.addUserInfo(okAccessKey, okSessionToken, okUserInfo)

  "User service" should {
    "verify user and return true" in {
      userService.isCredentialActive(okAccessKey, okSessionToken) shouldBe true
    }

    "verify user and return false" in {
      userService.isCredentialActive(okAccessKey, badSessionToken) shouldBe false
      userService.isCredentialActive(badAccessKey, okSessionToken) shouldBe false
      userService.isCredentialActive(badAccessKey, badSessionToken) shouldBe false
    }

    "get user information" in {
      userService.getUserInfo(okAccessKey, okSessionToken) shouldBe Some(okUserInfo)
    }

    "not get user information" in {
      userService.getUserInfo(badAccessKey, badSessionToken) shouldBe None
      userService.getUserInfo(okAccessKey, badSessionToken) shouldBe None
      userService.getUserInfo(badAccessKey, okSessionToken) shouldBe None
    }
  }
}
