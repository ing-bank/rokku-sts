package com.ing.wbaa.rokku.sts.config

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.Config

class VaultSettings(config: Config) extends Extension {

  val vaultUrl: String = config.getString("vault.url")
  val vaultPath: String = config.getString("vault.path")
}

object VaultSettings extends ExtensionId[VaultSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): VaultSettings = new VaultSettings(system.settings.config)

  override def lookup(): ExtensionId[VaultSettings] = VaultSettings
}
