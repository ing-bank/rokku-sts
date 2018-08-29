package com.ing.wbaa.gargoyle.sts.config

import java.util.concurrent.TimeUnit

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

import scala.concurrent.duration.Duration

class GargoyleStsSettings(config: Config) extends Extension {
  private[this] val gargoyleStsConfig = config.getConfig("gargoyle.sts")
  val defaultTokenSessionDuration: Duration = Duration(gargoyleStsConfig.getInt("defaultTokenSessionHours"), TimeUnit.HOURS)
  val maxTokenSessionDuration: Duration = Duration(gargoyleStsConfig.getInt("maxTokenSessionHours"), TimeUnit.HOURS)
}

object GargoyleStsSettings extends ExtensionId[GargoyleStsSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): GargoyleStsSettings = new GargoyleStsSettings(system.settings.config)

  override def lookup(): ExtensionId[GargoyleStsSettings] = GargoyleStsSettings
}
