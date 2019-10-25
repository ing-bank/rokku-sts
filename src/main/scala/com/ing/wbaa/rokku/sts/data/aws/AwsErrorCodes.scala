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

  def response(code: StatusCode, resource: String = "")(implicit requestId: RequestId = RequestId("")): NodeSeq = {
    val responseError = errors.getOrElse(code, ("Unexpected Error", "Unexpected Error"))
    <ErrorResponse>
      <Error>
        <Code>{ responseError._1 }</Code>
        <Message>{ responseError._2 }</Message>
        <Resource>{ resource }</Resource>
        <RequestId>{ requestId.value }</RequestId>
      </Error>
    </ErrorResponse>
  }

}
