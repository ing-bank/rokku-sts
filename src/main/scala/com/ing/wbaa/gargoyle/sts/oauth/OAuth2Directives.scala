package com.ing.wbaa.gargoyle.sts.oauth

import akka.http.scaladsl.server.Directive1
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

trait OAuth2Directives {

  val logger = Logger(LoggerFactory.getLogger("OAuth2Directives"))

  def oAuth2Authorization(oAuth2TokenVerifier: OAuth2TokenVerifier): Directive1[VerifiedToken] = {
    new OAuth2Authorization(oAuth2TokenVerifier).authorizeToken
  }
}

object OAuth2Directives extends OAuth2Directives

