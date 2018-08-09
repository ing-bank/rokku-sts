package com.ing.wbaa.gargoyle.sts.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class GargoyleHttpSettings(config: Config) extends Extension {
  val httpPort: Int = config.getInt("gargoyle.sts.http.port")
  val httpBind: String = config.getString("gargoyle.sts.http.bind")
}

object GargoyleHttpSettings extends ExtensionId[GargoyleHttpSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): GargoyleHttpSettings = new GargoyleHttpSettings(system.settings.config)

  override def lookup(): ExtensionId[GargoyleHttpSettings] = GargoyleHttpSettings
}

