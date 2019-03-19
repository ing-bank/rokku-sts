package com.ing.wbaa.airlock.sts.config

import java.util.concurrent.TimeUnit

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

import scala.concurrent.duration.Duration

class StsSettings(config: Config) extends Extension {
  private[this] val airlockStsConfig = config.getConfig("airlock.sts")
  val defaultTokenSessionDuration: Duration = Duration(airlockStsConfig.getInt("defaultTokenSessionHours"), TimeUnit.HOURS)
  val maxTokenSessionDuration: Duration = Duration(airlockStsConfig.getInt("maxTokenSessionHours"), TimeUnit.HOURS)
  val masterKey: String = airlockStsConfig.getString("masterKey")
  val encryptionAlgorithm: String = airlockStsConfig.getString("encryptionAlgorithm")
  val adminGroups = airlockStsConfig.getString("adminGroups").split(",").map(_.trim).toList
  val decodeSecret = airlockStsConfig.getString("decodeSecret")
}

object StsSettings extends ExtensionId[StsSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): StsSettings = new StsSettings(system.settings.config)

  override def lookup(): ExtensionId[StsSettings] = StsSettings
}
