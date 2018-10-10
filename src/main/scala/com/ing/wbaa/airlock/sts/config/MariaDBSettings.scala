package com.ing.wbaa.airlock.sts.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class MariaDBSettings(config: Config) extends Extension {
  val host: String = config.getString("mariadb.host")
  val port: Int = config.getInt("mariadb.port")
  val database: String = config.getString("mariadb.database")
  val username: String = config.getString("mariadb.username")
  val password: String = config.getString("mariadb.password")
}

object MariaDBSettings extends ExtensionId[MariaDBSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): MariaDBSettings = new MariaDBSettings(system.settings.config)

  override def lookup(): ExtensionId[MariaDBSettings] = MariaDBSettings
}
