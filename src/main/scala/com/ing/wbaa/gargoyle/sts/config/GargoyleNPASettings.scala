package com.ing.wbaa.gargoyle.sts.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

import scala.collection.mutable

// TODO Remove all of this, NPAs should be set in the DB directly or in Keycloak if possible
class GargoyleNPASettings(config: Config) extends Extension {
  import scala.collection.JavaConverters._

  val gargoyleNPAList: List[mutable.Map[String, String]] =
    config.getList("gargoyle.npa").unwrapped().asScala.toList
      .map { someConfig =>
        someConfig.asInstanceOf[java.util.Map[String, String]].asScala
      }
}

object GargoyleNPASettings extends ExtensionId[GargoyleNPASettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): GargoyleNPASettings = new GargoyleNPASettings(system.settings.config)

  override def lookup(): ExtensionId[GargoyleNPASettings] = GargoyleNPASettings
}

