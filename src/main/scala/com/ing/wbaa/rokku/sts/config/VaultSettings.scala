package com.ing.wbaa.rokku.sts.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

import scala.io.Source

class VaultSettings(config: Config) extends Extension {

  val vaultUrl: String = config.getString("vault.url")
  val vaultPath: String = config.getString("vault.path")
  val readTimeout: Int = config.getInt("vault.read-timeout")
  val openTimeout: Int = config.getInt("vault.open-timeout")
  val retries: Int = config.getInt("vault.retries")
  val jwt: String = Source.fromFile(config.getString("vault.service-account.token-location")).getLines().next()
  val auth: String = config.getString("vault.service-account.auth-path")
  val role: String = config.getString("vault.service-account.role")

}

object VaultSettings extends ExtensionId[VaultSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): VaultSettings = new VaultSettings(system.settings.config)

  override def lookup(): ExtensionId[VaultSettings] = VaultSettings
}
