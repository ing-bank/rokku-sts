package com.ing.wbaa.gargoyle.sts.oauth

import akka.http.scaladsl.server.Directive1

trait OAuth2Directives {

  def oAuth2Authorization(oAuth2TokenVerifier: OAuth2TokenVerifier): Directive1[VerifiedToken] = {
    new OAuth2Authorization(oAuth2TokenVerifier).authorizeToken
  }
}

object OAuth2Directives extends OAuth2Directives

