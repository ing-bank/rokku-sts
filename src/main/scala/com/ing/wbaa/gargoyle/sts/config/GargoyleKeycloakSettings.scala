package com.ing.wbaa.gargoyle.sts.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class GargoyleKeycloakSettings(config: Config) extends Extension {
  private[this] val gargoyleStsConfig = config.getConfig("gargoyle.sts")
  val realmPublicKeyId: String = gargoyleStsConfig.getString("keycloak.realmPublicKeyId")
  val realm: String = gargoyleStsConfig.getString("keycloak.realm")
  val resource: String = gargoyleStsConfig.getString("keycloak.resource")
  val url: String = gargoyleStsConfig.getString("keycloak.url")
}

object GargoyleKeycloakSettings extends ExtensionId[GargoyleKeycloakSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): GargoyleKeycloakSettings = new GargoyleKeycloakSettings(system.settings.config)

  override def lookup(): ExtensionId[GargoyleKeycloakSettings] = GargoyleKeycloakSettings
}
