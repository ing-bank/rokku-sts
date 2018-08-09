package com.ing.wbaa.gargoyle.sts

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.sts.config.{ GargoyleHttpSettings, GargoyleKeycloakSettings }
import com.ing.wbaa.gargoyle.sts.oauth.KeycloakTokenVerifier
import com.ing.wbaa.gargoyle.sts.service.{ TokenService, TokenXML, UserService }

object Server extends App {
  new GargoyleStsService with KeycloakTokenVerifier with UserService with TokenService with TokenXML {
    override implicit lazy val system: ActorSystem = ActorSystem.create("gargoyle-sts")

    override def httpSettings: GargoyleHttpSettings = GargoyleHttpSettings(system)

    override protected[this] def keycloakSettings: GargoyleKeycloakSettings = GargoyleKeycloakSettings(system)
  }.startup
}
