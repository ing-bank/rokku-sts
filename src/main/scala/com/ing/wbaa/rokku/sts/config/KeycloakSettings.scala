package com.ing.wbaa.rokku.sts.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class KeycloakSettings(config: Config) extends Extension {
  private[this] val rokkuStsKeycloakConfig = config.getConfig("rokku.sts.keycloak")

  val realmPublicKeyId: String = rokkuStsKeycloakConfig.getString("realmPublicKeyId")
  val realm: String = rokkuStsKeycloakConfig.getString("realm")
  val resource: String = rokkuStsKeycloakConfig.getString("resource")
  val url: String = rokkuStsKeycloakConfig.getString("url")
  val checkRealmUrl: Boolean = rokkuStsKeycloakConfig.getBoolean("verifyToken.checkRealmUrl")
  val issuerForList: Set[String] =
    rokkuStsKeycloakConfig.getString("verifyToken.issuerForList").split(",").map(_.trim).toSet
  val NPAClaimKey: String = rokkuStsKeycloakConfig.getString("verifyToken.NPAClaimKey")
  val NPAClaimExpectedValue: String = rokkuStsKeycloakConfig.getString("verifyToken.NPAClaimExpectedValue")
  val clientSecret: String = rokkuStsKeycloakConfig.getString("clientSecret")
  val adminUsername: String = rokkuStsKeycloakConfig.getString("adminUsername")
  val adminPassword: String = rokkuStsKeycloakConfig.getString("adminPassword")
  val httpRelativePath: String = rokkuStsKeycloakConfig.getString("httpRelativePath") //can be removed when keyclock docker image for dev will be upgraded to version 18 or above (see https://www.keycloak.org/server/all-config#_httptls http-relative-path)
  val npaRole: String = rokkuStsKeycloakConfig.getString("npaRole")
}

object KeycloakSettings extends ExtensionId[KeycloakSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): KeycloakSettings = new KeycloakSettings(system.settings.config)

  override def lookup: ExtensionId[KeycloakSettings] = KeycloakSettings
}
