package com.ing.wbaa.airlock.sts.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class KeycloakSettings(config: Config) extends Extension {
  private[this] val airlockStsKeycloakConfig = config.getConfig("airlock.sts.keycloak")

  val realmPublicKeyId: String = airlockStsKeycloakConfig.getString("realmPublicKeyId")
  val realm: String = airlockStsKeycloakConfig.getString("realm")
  val resource: String = airlockStsKeycloakConfig.getString("resource")
  val url: String = airlockStsKeycloakConfig.getString("url")
  val checkRealmUrl: Boolean = airlockStsKeycloakConfig.getBoolean("verifyToken.checkRealmUrl")
}

object KeycloakSettings extends ExtensionId[KeycloakSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): KeycloakSettings = new KeycloakSettings(system.settings.config)

  override def lookup(): ExtensionId[KeycloakSettings] = KeycloakSettings
}
