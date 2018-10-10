package com.ing.wbaa.airlock.sts.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class HttpSettings(config: Config) extends Extension {
  val httpPort: Int = config.getInt("airlock.sts.http.port")
  val httpBind: String = config.getString("airlock.sts.http.bind")
}

object HttpSettings extends ExtensionId[HttpSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): HttpSettings = new HttpSettings(system.settings.config)

  override def lookup(): ExtensionId[HttpSettings] = HttpSettings
}

