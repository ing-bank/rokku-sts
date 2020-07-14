package com.ing.wbaa.rokku.sts.config

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.ing.wbaa.rokku.sts.config.exceptions.UnavailableConfig
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging

import scala.io.Source
import scala.util.{Failure, Success, Try}

class VaultSettings(config: Config) extends Extension with LazyLogging {

  private val filePath: String = config.getString("vault.token-file")
  val vaultUrl: String = config.getString("vault.url")
  val vaultPath: String = config.getString("vault.path")
  val vaultToken: String = readTokenFromFile(filePath)
  val readTimeout: Int = config.getInt("vault.read-timeout")
  val openTimeout: Int = config.getInt("vault.open-timeout")
  val retries: Int = config.getInt("vault.retries")


  private def readTokenFromFile(filePath: String): String = {
    Try {
      val src = Source.fromFile(filePath)
      val token = src.getLines().next()
      token
    } match {
      case Success(token) =>
        logger.debug("Successfully read token from file")
        token
      case Failure(e) =>
        logger.error("Couldn't read token from file. I won't be able to write to vault. The error was: " + e.getMessage)
        throw new UnavailableConfig(cause = e)
    }
  }
}

object VaultSettings extends ExtensionId[VaultSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): VaultSettings = new VaultSettings(system.settings.config)

  override def lookup(): ExtensionId[VaultSettings] = VaultSettings
}
