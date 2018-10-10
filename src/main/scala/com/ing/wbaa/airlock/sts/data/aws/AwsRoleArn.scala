package com.ing.wbaa.airlock.sts.data.aws

import com.ing.wbaa.airlock.sts.data.{ AuthenticationUserInfo, UserAssumedGroup }
import com.typesafe.scalalogging.LazyLogging

case class AwsRoleArn(arn: String) extends LazyLogging {

  /**
   * Parses the ARN to a group the user can assume.
   *
   * Arn format:
   * "arn:aws:iam::account-id:role/role-name"
   */
  private[this] val getUserGroup: Option[UserAssumedGroup] = {
    val arnRegex = """arn:aws:iam::.+:role/(.+)""".r

    arnRegex.findFirstMatchIn(arn).fold[Option[UserAssumedGroup]] {
      logger.info(s"RoleARN specified could not be parsed: $arn")
      None
    } { theMatch =>
      if (theMatch.groupCount == 1) Some(UserAssumedGroup(theMatch.group(1)))
      else {
        logger.info(s"RoleARN specified could not be parsed, too many groups found in match: $arn")
        None
      }
    }
  }

  /**
   * Checks whether or not this ARN is contained in a set of groups that the user has.
   *
   * @param keycloakUserInfo The user from keycloak that would like to assume the role this ARN specifies
   * @return The group the user can assume with this ARN
   */
  def getGroupUserCanAssume(keycloakUserInfo: AuthenticationUserInfo): Option[UserAssumedGroup] =
    getUserGroup.filter(extractedGroup => keycloakUserInfo.userGroups.map(_.value).contains(extractedGroup.value))
}

