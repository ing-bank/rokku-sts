package com.ing.wbaa.rokku.sts.handler

import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import com.ing.wbaa.rokku.sts.data.aws.{ AwsErrorCodes, AwsRoleArnException }
import com.ing.wbaa.rokku.sts.keycloak.KeycloakException
import com.ing.wbaa.rokku.sts.util.JwtTokenException

object StsExceptionHandlers {

  implicit def exceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case _: JwtTokenException =>
        complete(StatusCodes.Forbidden -> AwsErrorCodes.response(StatusCodes.Forbidden))
      case _: KeycloakException =>
        complete(StatusCodes.Forbidden -> AwsErrorCodes.response(StatusCodes.Forbidden))
      case _: AwsRoleArnException =>
        complete(StatusCodes.Unauthorized -> AwsErrorCodes.response(StatusCodes.Unauthorized))
    }
}
