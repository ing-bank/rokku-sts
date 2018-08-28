package com.ing.wbaa.gargoyle.sts.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class GargoyleKeycloakSettings(config: Config) extends Extension {
  private[this] val gargoyleStsKeycloakConfig = config.getConfig("gargoyle.sts.keycloak")

  val realmPublicKeyId: String = gargoyleStsKeycloakConfig.getString("realmPublicKeyId")
  val realm: String = gargoyleStsKeycloakConfig.getString("realm")
  val resource: String = gargoyleStsKeycloakConfig.getString("resource")
  val url: String = gargoyleStsKeycloakConfig.getString("url")
  val checkRealmUrl: Boolean = gargoyleStsKeycloakConfig.getBoolean("verifyToken.checkRealmUrl")
}

object GargoyleKeycloakSettings extends ExtensionId[GargoyleKeycloakSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): GargoyleKeycloakSettings = new GargoyleKeycloakSettings(system.settings.config)

  override def lookup(): ExtensionId[GargoyleKeycloakSettings] = GargoyleKeycloakSettings
}
