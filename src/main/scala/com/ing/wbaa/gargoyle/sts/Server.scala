package com.ing.wbaa.gargoyle.sts

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.sts.config.GargoyleHttpSettings
import com.ing.wbaa.gargoyle.sts.oauth.OAuth2TokenVerifier
import com.ing.wbaa.gargoyle.sts.service.{ TokenService, TokenXML, UserService }

object Server extends App {
  new GargoyleStsService with OAuth2TokenVerifier with UserService with TokenService with TokenXML {
    override implicit lazy val system: ActorSystem = ActorSystem.create("gargoyle-sts")
    override def httpSettings: GargoyleHttpSettings = GargoyleHttpSettings(system)
  }.startup
}
