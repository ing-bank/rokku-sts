package com.ing.wbaa.rokku.sts

import akka.actor.ActorSystem
import com.ing.wbaa.rokku.sts.config._
import com.ing.wbaa.rokku.sts.keycloak.{ KeycloakClient, KeycloakTokenVerifier }
import com.ing.wbaa.rokku.sts.service.{ UserTokenDbService }
import com.ing.wbaa.rokku.sts.service.db.Redis
import com.ing.wbaa.rokku.sts.service.db.dao.{ STSTokenDAO, STSUserAndGroupDAO }
import com.ing.wbaa.rokku.sts.vault.VaultService

object Server extends App {
  new RokkuStsService with KeycloakTokenVerifier with UserTokenDbService with STSUserAndGroupDAO with STSTokenDAO with Redis with VaultService with KeycloakClient {
    override implicit lazy val system: ActorSystem = ActorSystem.create("rokku-sts")

    override protected[this] def httpSettings: HttpSettings = HttpSettings(system)

    override protected[this] def keycloakSettings: KeycloakSettings = KeycloakSettings(system)

    override protected[this] def stsSettings: StsSettings = StsSettings(system)

    override protected[this] def vaultSettings: VaultSettings = VaultSettings(system)

    override protected[this] def redisSettings: RedisSettings = RedisSettings(system)

    //Connects to Redis on startup and initializes indeces
    forceInitRedisConnectionPool()
  }.startup
}
