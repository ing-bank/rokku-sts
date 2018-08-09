package com.ing.wbaa.gargoyle.sts.service

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpec }

class UserServiceTest extends WordSpec with Matchers with ScalaFutures with UserService {

  import com.ing.wbaa.gargoyle._

  addUserInfo(okAccessKey, okSessionToken, okUserInfo)

  "User service" should {
    "verify user and return true" in {
      isCredentialActive(okAccessKey, okSessionToken).futureValue shouldBe true
    }

    "verify user and return false" in {
      isCredentialActive(okAccessKey, badSessionToken).futureValue shouldBe false
      isCredentialActive(badAccessKey, okSessionToken).futureValue shouldBe false
      isCredentialActive(badAccessKey, badSessionToken).futureValue shouldBe false
    }

    "get user information" in {
      getUserInfo(okAccessKey).futureValue shouldBe Some(okUserInfo)
    }

    "not get user information" in {
      getUserInfo(badAccessKey).futureValue shouldBe None
    }
  }
}
