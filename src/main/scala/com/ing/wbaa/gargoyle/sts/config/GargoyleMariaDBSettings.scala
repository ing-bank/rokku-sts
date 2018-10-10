package com.ing.wbaa.gargoyle.sts.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class GargoyleMariaDBSettings(config: Config) extends Extension {
  val host: String = config.getString("mariadb.host")
  val port: Int = config.getInt("mariadb.port")
  val database: String = config.getString("mariadb.database")
  val username: String = config.getString("mariadb.username")
  val password: String = config.getString("mariadb.password")
}

object GargoyleMariaDBSettings extends ExtensionId[GargoyleMariaDBSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): GargoyleMariaDBSettings = new GargoyleMariaDBSettings(system.settings.config)

  override def lookup(): ExtensionId[GargoyleMariaDBSettings] = GargoyleMariaDBSettings
}
