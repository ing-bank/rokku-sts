package com.ing.wbaa.rokku.sts.data.aws

import com.ing.wbaa.rokku.sts.data.{ AuthenticationUserInfo, UserAssumeRole }
import com.typesafe.scalalogging.LazyLogging

case class AwsRoleArn(arn: String) extends LazyLogging {

  /**
   * Checks whether or not this ARN is contained in a set of roles that the user has.
   *
   * @param userInfo The user from keycloak that would like to assume the role this ARN specifies
   * @return The role the user can assume with this ARN
   */
  def getRoleUserCanAssume(userInfo: AuthenticationUserInfo): Option[UserAssumeRole] = {
    logger.debug("arn = {}", arn)
    logger.debug("user roles = {}", userInfo.userRoles)
    getRoleFromArn.filter(extractedRole => userInfo.userRoles.map(_.value).contains(extractedRole.value))
  }

  /**
   * get the role-name from ARN string
   *
   * Arn format:
   * "arn:aws:iam::account-id:role/role-name"
   */
  private[this] val getRoleFromArn: Option[UserAssumeRole] = {
    val arnRegex = "arn:aws:iam::.+:role/(.+)".r
    arnRegex.findFirstMatchIn(arn).filter(_.groupCount == 1).map(theMatch => UserAssumeRole(theMatch.group(1)))
  }
}

class AwsRoleArnException(message: String) extends Exception(message)
