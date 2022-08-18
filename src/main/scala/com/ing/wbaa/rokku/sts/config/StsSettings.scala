package com.ing.wbaa.rokku.sts.config

import java.util.concurrent.TimeUnit

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

import scala.concurrent.duration.Duration

class StsSettings(config: Config) extends Extension {
  private[this] val rokkuStsConfig = config.getConfig("rokku.sts")
  val defaultTokenSessionDuration: Duration = Duration(rokkuStsConfig.getInt("defaultTokenSessionHours"), TimeUnit.HOURS)
  val maxTokenSessionDuration: Duration = Duration(rokkuStsConfig.getInt("maxTokenSessionHours"), TimeUnit.HOURS)
  val masterKey: String = rokkuStsConfig.getString("masterKey")
  val encryptionAlgorithm: String = rokkuStsConfig.getString("encryptionAlgorithm")
  val adminGroups = rokkuStsConfig.getString("adminGroups").split(",").map(_.trim).toList
  val decodeSecret = rokkuStsConfig.getString("decodeSecret")
}

object StsSettings extends ExtensionId[StsSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): StsSettings = new StsSettings(system.settings.config)

  override def lookup: ExtensionId[StsSettings] = StsSettings
}
