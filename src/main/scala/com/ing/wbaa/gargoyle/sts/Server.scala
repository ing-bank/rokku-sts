package com.ing.wbaa.gargoyle.sts

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.sts.config.{ GargoyleHttpSettings, GargoyleKeycloakSettings, GargoyleStsSettings }
import com.ing.wbaa.gargoyle.sts.keycloak.KeycloakTokenVerifier
import com.ing.wbaa.gargoyle.sts.service.UserTokenService

object Server extends App {
  new GargoyleStsService with KeycloakTokenVerifier with UserTokenService {
    override implicit lazy val system: ActorSystem = ActorSystem.create("gargoyle-sts")

    override protected[this] def httpSettings: GargoyleHttpSettings = GargoyleHttpSettings(system)

    override protected[this] def keycloakSettings: GargoyleKeycloakSettings = GargoyleKeycloakSettings(system)

    override protected[this] def stsSettings: GargoyleStsSettings = GargoyleStsSettings(system)
  }.startup
}
