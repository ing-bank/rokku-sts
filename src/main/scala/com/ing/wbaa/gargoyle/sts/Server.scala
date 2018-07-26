package com.ing.wbaa.gargoyle.sts

import akka.actor.ActorSystem

object Server extends App {
  val stsService: StsService = StsService()(ActorSystem.create("gargoyle-sts"))
}
