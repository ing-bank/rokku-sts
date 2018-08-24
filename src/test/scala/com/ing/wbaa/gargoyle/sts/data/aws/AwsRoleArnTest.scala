package com.ing.wbaa.gargoyle.sts.data.aws

import com.ing.wbaa.gargoyle.sts.data.{ KeycloakTokenId, KeycloakUserInfo, UserGroup, UserName }
import org.scalatest.WordSpec

class AwsRoleArnTest extends WordSpec {

  "AwsRoleArn" should {

    "get groups a user can assume" that {
      "could parse arn and existed in groups" in {
        val testGroup = "application_abc/component_xyz/S3Access"

        val result = AwsRoleArn(s"arn:aws:iam::123456789012:role/$testGroup")
          .getGroupUserCanAssume(
            KeycloakUserInfo(UserName(""), Set(UserGroup(testGroup)), KeycloakTokenId(""))
          )
        assert(result.contains(UserGroup(testGroup)))
      }

      "could not parse arn, invalid ARN" in {
        val testGroup = "application_abc/component_xyz/S3Access"

        val result = AwsRoleArn(s"arn:aws:iam:invalid:123456789012:role/$testGroup")
          .getGroupUserCanAssume(
            KeycloakUserInfo(UserName(""), Set(UserGroup(testGroup)), KeycloakTokenId(""))
          )
        assert(result.isEmpty)
      }

      "doesn't exist in groups of user" in {
        val testGroup = "application_abc/component_xyz/S3Access"

        val result = AwsRoleArn(s"arn:aws:iam::123456789012:role/$testGroup")
          .getGroupUserCanAssume(
            KeycloakUserInfo(UserName(""), Set.empty, KeycloakTokenId(""))
          )
        assert(result.isEmpty)
      }
    }
  }
}
