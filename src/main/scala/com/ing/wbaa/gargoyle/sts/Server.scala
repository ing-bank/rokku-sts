package com.ing.wbaa.gargoyle.sts

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.sts.config.{ GargoyleHttpSettings, GargoyleKeycloakSettings }
import com.ing.wbaa.gargoyle.sts.db.STSUserTokenStore
import com.ing.wbaa.gargoyle.sts.keycloak.KeycloakTokenVerifier

object Server extends App {
  new GargoyleStsService with KeycloakTokenVerifier with STSUserTokenStore {
    override implicit lazy val system: ActorSystem = ActorSystem.create("gargoyle-sts")

    override def httpSettings: GargoyleHttpSettings = GargoyleHttpSettings(system)

    override protected[this] def keycloakSettings: GargoyleKeycloakSettings = GargoyleKeycloakSettings(system)
  }.startup
}
