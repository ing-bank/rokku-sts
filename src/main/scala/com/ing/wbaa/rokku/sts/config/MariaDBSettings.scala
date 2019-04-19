package com.ing.wbaa.rokku.sts.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class MariaDBSettings(config: Config) extends Extension {
  val url: String = config.getString("mariadb.url")
  val username: String = config.getString("mariadb.username")
  val password: String = config.getString("mariadb.password")
}

object MariaDBSettings extends ExtensionId[MariaDBSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): MariaDBSettings = new MariaDBSettings(system.settings.config)

  override def lookup(): ExtensionId[MariaDBSettings] = MariaDBSettings
}
