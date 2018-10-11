package com.ing.wbaa.airlock.sts.data.aws

import com.ing.wbaa.airlock.sts.data._
import org.scalatest.WordSpec

class AwsRoleArnTest extends WordSpec {

  "AwsRoleArn" should {

    "get groups a user can assume" that {
      "could parse arn and existed in groups" in {
        val testGroup = "application_abc/component_xyz/S3Access"

        val result = AwsRoleArn(s"arn:aws:iam::123456789012:role/$testGroup")
          .getGroupUserCanAssume(
            AuthenticationUserInfo(UserName(""), Set(UserGroup(testGroup)), AuthenticationTokenId(""))
          )
        assert(result.contains(UserAssumedGroup(testGroup)))
      }

      "could not parse arn, invalid ARN" in {
        val testGroup = "application_abc/component_xyz/S3Access"

        val result = AwsRoleArn(s"arn:aws:iam:invalid:123456789012:role/$testGroup")
          .getGroupUserCanAssume(
            AuthenticationUserInfo(UserName(""), Set(UserGroup(testGroup)), AuthenticationTokenId(""))
          )
        assert(result.isEmpty)
      }

      "doesn't exist in groups of user" in {
        val testGroup = "application_abc/component_xyz/S3Access"

        val result = AwsRoleArn(s"arn:aws:iam::123456789012:role/$testGroup")
          .getGroupUserCanAssume(
            AuthenticationUserInfo(UserName(""), Set.empty, AuthenticationTokenId(""))
          )
        assert(result.isEmpty)
      }
    }
  }
}
