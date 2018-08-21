package com.ing.wbaa.gargoyle.sts.db

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ AsyncWordSpec, DiagrammedAssertions }

import scala.concurrent.ExecutionContext

class STSUserTokenStoreTest extends AsyncWordSpec with DiagrammedAssertions with ScalaFutures with STSUserTokenStore {

  import com.ing.wbaa.gargoyle._

  addUserInfo(okAccessKey, okSessionToken, okUserInfo)

  "User service" should {
    "verify user and return true" in {
      isCredentialActive(okAccessKey, okSessionToken).map(a => assert(a))
    }

    "verify user and return false" in {
      isCredentialActive(okAccessKey, badSessionToken).map(a => assert(!a))
      isCredentialActive(badAccessKey, okSessionToken).map(a => assert(!a))
      isCredentialActive(badAccessKey, badSessionToken).map(a => assert(!a))
    }

    "get user information" in {
      getUserInfo(okAccessKey).map(a => assert(a.contains(okUserInfo)))
    }

    "not get user information" in {
      getUserInfo(badAccessKey).map(a => assert(a.isEmpty))
    }
  }

  override implicit def executionContext: ExecutionContext = ExecutionContext.global
}
