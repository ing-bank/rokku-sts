package com.ing.wbaa.rokku.sts

import akka.actor.ActorSystem
import com.ing.wbaa.rokku.sts.config._
import com.ing.wbaa.rokku.sts.keycloak.KeycloakTokenVerifier
import com.ing.wbaa.rokku.sts.service.{ ExpiredTokenCleaner, UserTokenDbService }
import com.ing.wbaa.rokku.sts.service.db.MariaDb
import com.ing.wbaa.rokku.sts.service.db.dao.{ STSTokenDAO, STSUserAndGroupDAO }

object Server extends App {
  new RokkuStsService with KeycloakTokenVerifier with UserTokenDbService with STSUserAndGroupDAO with STSTokenDAO with MariaDb with ExpiredTokenCleaner {
    override implicit lazy val system: ActorSystem = ActorSystem.create("rokku-sts")

    override protected[this] def httpSettings: HttpSettings = HttpSettings(system)

    override protected[this] def keycloakSettings: KeycloakSettings = KeycloakSettings(system)

    override protected[this] def stsSettings: StsSettings = StsSettings(system)

    override protected[this] def mariaDBSettings: MariaDBSettings = MariaDBSettings(system)

    //Connects to Maria DB on startup
    forceInitMariaDbConnectionPool()
  }.startup
}
