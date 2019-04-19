package com.ing.wbaa.rokku.sts.service.db

import akka.actor.ActorSystem
import com.ing.wbaa.rokku.sts.config.{MariaDBSettings, StsSettings}
import org.scalatest.AsyncWordSpec

import scala.util.{Failure, Success}

class MariaDbItTest extends AsyncWordSpec with MariaDb {
  val system: ActorSystem = ActorSystem.create("test-system")

  protected[this] def mariaDBSettings: MariaDBSettings = MariaDBSettings(system)

  protected[this] def stsSettings: StsSettings = StsSettings(system)

  override lazy val dbExecutionContext = executionContext

  "MariaDB" should {

    "be reachable" in {
      checkDbConnection() transform {
        case Success(_) => Success(succeed)
        case Failure(err) => Failure(fail(err))
      }
    }

  }

}
