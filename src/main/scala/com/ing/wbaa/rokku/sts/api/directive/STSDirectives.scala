package com.ing.wbaa.rokku.sts.api.directive

import akka.http.scaladsl.model.headers.{ Authorization, OAuth2BearerToken }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import com.ing.wbaa.rokku.sts.data.aws.{ AwsRoleArnException, AwsRoleArn }
import com.ing.wbaa.rokku.sts.data.{ AuthenticationUserInfo, BearerToken, UserAssumeRole }
import com.typesafe.scalalogging.LazyLogging

object STSDirectives extends LazyLogging {

  /**
   * get the token from http reguest and verify by the provided tokenVerifier
   *
   * @return the verifiedToken or rejection
   */
  def authorizeToken(tokenVerifier: BearerToken => Option[AuthenticationUserInfo]): Directive1[AuthenticationUserInfo] = {
    bearerToken.flatMap {
      case Some(token) =>
        logger.debug("received oauth token={}", token)
        tokenVerifier(token) match {
          case Some(t) => provide(t)
          case None =>
            logger.error("Authorization Token could not be verified")
            reject(AuthorizationFailedRejection).toDirective[Tuple1[AuthenticationUserInfo]]
        }
      case None =>
        logger.info("no credential token")
        reject(AuthorizationFailedRejection)
    }
  }

  /**
   * check if user has a arn role
   * @param userInfo
   * @param arn
   * @return assumeRole or rejection
   */
  def assumeRole(userInfo: AuthenticationUserInfo, arn: AwsRoleArn): Directive1[UserAssumeRole] = {
    arn.getRoleUserCanAssume(userInfo) match {
      case Some(role) => provide(role)
      case None =>
        logger.warn("user {} cannot assume role {}", userInfo.userName, arn.arn)
        throw new AwsRoleArnException("user cannot assume the role=" + arn.arn)
    }
  }

  /**
   * because the token can be in many places we have to check:
   * - header - OAuth2BearerToken
   * - cookie - X-Authorization-Token
   * - parameters - WebIdentityToken or TokenCode
   * - body - WebIdentityToken or TokenCode
   *
   * @return the directive with authorization token
   */
  private val bearerToken: Directive1[Option[BearerToken]] =
    for {
      tokenFromAuthBearerHeader <- optionalTokenFromAuthBearerHeader
      tokenFromAuthCookie <- optionalTokenFromCookie
      tokenFromWebIdentityToken <- optionalTokenFromWebIdentityToken
      tokenFromTokenCode <- optionalTokenFromTokenCode
    } yield tokenFromAuthBearerHeader
      .orElse(tokenFromAuthCookie)
      .orElse(tokenFromWebIdentityToken)
      .orElse(tokenFromTokenCode)

  private def optionalTokenFromTokenCode = {
    val tokenCodeString = "TokenCode" ? ""
    for {
      tokenFromParam <- parameter(tokenCodeString).map(stringToBearerTokenOption)
      tokenFromField <- formField(tokenCodeString).map(stringToBearerTokenOption)
    } yield tokenFromParam.orElse(tokenFromField)
  }

  private def optionalTokenFromWebIdentityToken = {
    val webIdentityTokenString = "WebIdentityToken" ? ""
    for {
      tokenFromParam <- parameter(webIdentityTokenString).map(stringToBearerTokenOption)
      tokenFromField <- formField(webIdentityTokenString).map(stringToBearerTokenOption)
    } yield tokenFromParam.orElse(tokenFromField)
  }

  private def optionalTokenFromCookie = {
    optionalCookie("X-Authorization-Token").map(_.map(c => BearerToken(c.value)))
  }

  private def optionalTokenFromAuthBearerHeader = {
    optionalHeaderValueByType(classOf[Authorization]).map(extractBearerToken)
  }

  private def extractBearerToken(authHeader: Option[Authorization]): Option[BearerToken] =
    authHeader.collect {
      case Authorization(OAuth2BearerToken(token)) => BearerToken(token)
    }

  private val stringToBearerTokenOption: String => Option[BearerToken] = t => if (t.isEmpty) None else Some(BearerToken(t))

}
