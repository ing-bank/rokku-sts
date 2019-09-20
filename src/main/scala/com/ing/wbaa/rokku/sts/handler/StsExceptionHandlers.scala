package com.ing.wbaa.rokku.sts.handler

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import com.ing.wbaa.rokku.sts.util.JwtTokenException

object StsExceptionHandlers {

  implicit def exceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case _: JwtTokenException =>
        complete(StatusCodes.Forbidden)
    }
}
