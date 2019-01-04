package com.ing.wbaa.airlock.sts

import akka.actor.ActorSystem
import com.ing.wbaa.airlock.sts.config._
import com.ing.wbaa.airlock.sts.keycloak.KeycloakTokenVerifier
import com.ing.wbaa.airlock.sts.service.UserTokenDbService
import com.ing.wbaa.airlock.sts.service.db.MariaDb
import com.ing.wbaa.airlock.sts.service.db.dao.{ STSTokenDAO, STSUserAndGroupDAO }

object Server extends App {
  new AirlockStsService with KeycloakTokenVerifier with UserTokenDbService with STSUserAndGroupDAO with STSTokenDAO with MariaDb {
    override implicit lazy val system: ActorSystem = ActorSystem.create("airlock-sts")

    override protected[this] def httpSettings: HttpSettings = HttpSettings(system)

    override protected[this] def keycloakSettings: KeycloakSettings = KeycloakSettings(system)

    override protected[this] def stsSettings: StsSettings = StsSettings(system)

    override protected[this] def mariaDBSettings: MariaDBSettings = MariaDBSettings(system)

    //Connects to Maria DB on startup
    forceInitMariaDbConnectionPool()
  }.startup
}
