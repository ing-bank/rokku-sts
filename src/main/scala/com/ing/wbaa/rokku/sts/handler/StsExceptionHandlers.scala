package com.ing.wbaa.rokku.sts.handler

import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import com.ing.wbaa.rokku.sts.data.aws.{ AwsErrorCodes, AwsRoleArnException }
import com.ing.wbaa.rokku.sts.keycloak.KeycloakException

object StsExceptionHandlers {

  val exceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case ex: KeycloakException =>
        complete(StatusCodes.Forbidden -> AwsErrorCodes.response(StatusCodes.Forbidden, message = Some(ex.getMessage)))
      case ex: AwsRoleArnException =>
        complete(StatusCodes.Unauthorized -> AwsErrorCodes.response(StatusCodes.Unauthorized, message = Some(ex.getMessage)))
    }
}
