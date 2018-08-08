package com.ing.wbaa.gargoyle.sts

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.sts.config.GargoyleHttpSettings
import com.ing.wbaa.gargoyle.sts.oauth.OAuth2TokenVerifierImpl
import com.ing.wbaa.gargoyle.sts.service.{ TokenServiceImpl, TokenXML, UserServiceImpl }

object Server extends App {
  new GargoyleStsService with OAuth2TokenVerifierImpl with UserServiceImpl with TokenServiceImpl with TokenXML {
    override implicit lazy val system: ActorSystem = ActorSystem.create("gargoyle-sts")
    override def httpSettings: GargoyleHttpSettings = GargoyleHttpSettings(system)
  }.startup
}
