package com.ing.wbaa.gargoyle.sts.oauth

import akka.http.scaladsl.server.Directive1

trait OAuth2Directives {

  def oAuth2Authorization(tokenVerifier: BearerToken => Option[VerifiedToken]): Directive1[VerifiedToken] = {
    new OAuth2Authorization(tokenVerifier).authorizeToken
  }
}

object OAuth2Directives extends OAuth2Directives

