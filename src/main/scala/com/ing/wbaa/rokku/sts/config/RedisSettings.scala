package com.ing.wbaa.rokku.sts.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class RedisSettings(config: Config) extends Extension {
  val host: String = config.getString("redis.host")
  val port: Int = config.getInt("redis.port")
  val username: String = config.getString("redis.username")
  val password: String = config.getString("redis.password")
}

object RedisSettings extends ExtensionId[RedisSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): RedisSettings = new RedisSettings(system.settings.config)

  override def lookup: ExtensionId[RedisSettings] = RedisSettings
}
