package com.ing.wbaa.rokku.sts.data.aws

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import com.ing.wbaa.rokku.sts.data.RequestId

import scala.xml.NodeSeq

object AwsErrorCodes {

  val errors: Map[StatusCode, (String, String)] =
    Map(
      StatusCodes.Forbidden -> (("AccessDenied", "Access Denied")),
      StatusCodes.BadRequest -> (("Bad Request", "Unhandled action")),
      StatusCodes.Unauthorized -> (("Unauthorized", "Unauthorized")),
      StatusCodes.InternalServerError -> (("InternalServerError", "Internal Server Error")))

  def response(code: StatusCode, resource: String = "", message: Option[String] = None)(implicit requestId: RequestId = RequestId("")): NodeSeq = {
    val responseError = errors.getOrElse(code, ("Unexpected Error", "Unexpected Error"))
    val messageError = message.getOrElse(responseError._2)
    <ErrorResponse>
      <Error>
        <Code>{ responseError._1 }</Code>
        <Message>{ messageError }</Message>
        <Resource>{ resource }</Resource>
        <RequestId>{ requestId.value }</RequestId>
      </Error>
    </ErrorResponse>
  }

}
