package com.ing.wbaa.airlock.sts

import akka.actor.ActorSystem
import com.ing.wbaa.airlock.sts.config.{MariaDBSettings, StsSettings}
import com.ing.wbaa.airlock.sts.service.db.MariaDb
import org.scalatest.AsyncWordSpec

import scala.util.{Failure, Success}

class MariaDbItTest extends AsyncWordSpec with MariaDb {
  val testSystem: ActorSystem = ActorSystem.create("test-system")

  protected[this] def mariaDBSettings: MariaDBSettings = MariaDBSettings(testSystem)

  protected[this] def stsSettings: StsSettings = StsSettings(testSystem)


  "MariaDB" should {

    "be reachable" in {
      checkConnection() transform {
        case Success(_) => Success(succeed)
        case Failure(err) => Failure(fail(err))
      }
    }

  }

}
