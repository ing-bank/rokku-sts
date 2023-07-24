package com.ing.wbaa.rokku.sts.data.aws

import com.ing.wbaa.rokku.sts.data._
import org.scalatest.wordspec.AnyWordSpec

class AwsRoleArnTest extends AnyWordSpec {

  "AwsRoleArn" should {

    "get groups a user can assume" that {
      "could parse arn and existed in groups" in {
        val testRoleName = "admin"

        val result = AwsRoleArn(s"arn:aws:iam::123456789012:role/$testRoleName")
          .getRoleUserCanAssume(
            AuthenticationUserInfo(Username(""), Set.empty, AuthenticationTokenId(""), Set(UserAssumeRole(testRoleName)), isNPA = false)
          )
        assert(result.contains(UserAssumeRole(testRoleName)))
      }

      "could not parse arn, invalid ARN" in {
        val testRoleName = "admin"

        val result = AwsRoleArn(s"arn:aws:iam:invalid:123456789012:role/$testRoleName")
          .getRoleUserCanAssume(
            AuthenticationUserInfo(Username(""), Set.empty, AuthenticationTokenId(""), Set(UserAssumeRole(testRoleName)), isNPA = false)
          )
        assert(result.isEmpty)
      }

      "doesn't exist in groups of user" in {
        val testRoleName = "admin"

        val result = AwsRoleArn(s"arn:aws:iam::123456789012:role/$testRoleName")
          .getRoleUserCanAssume(
            AuthenticationUserInfo(Username(""), Set.empty, AuthenticationTokenId(""), Set.empty, isNPA = false)
          )
        assert(result.isEmpty)
      }
    }
  }
}
