package com.ing.wbaa.rokku.sts.config.exceptions

class UnavailableConfig(message: String = null, cause: Throwable = null) extends Exception (UnavailableConfig.defaultMessage(message, cause), cause){
}

object UnavailableConfig {
  def defaultMessage(message: String, cause: Throwable) =
    if (message != null) message
    else if (cause != null) cause.toString()
    else null
}
