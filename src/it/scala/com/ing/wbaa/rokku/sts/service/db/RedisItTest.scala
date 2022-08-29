package com.ing.wbaa.rokku.sts.service.db

import akka.actor.ActorSystem
import com.ing.wbaa.rokku.sts.config.{ RedisSettings, StsSettings }
import org.scalatest.wordspec.AsyncWordSpec

import scala.util.{ Failure, Success }

class RedisItTest extends AsyncWordSpec with Redis {
  val system: ActorSystem = ActorSystem.create("test-system")

  protected[this] def redisSettings: RedisSettings = RedisSettings(system)

  protected[this] def stsSettings: StsSettings = StsSettings(system)

  override lazy val dbExecutionContext = executionContext

  "Redis" should {

    "be reachable" in {
      checkDbConnection() transform {
        case Success(_)   => Success(succeed)
        case Failure(err) => Failure(fail(err))
      }
    }

    "create index upon forceInitRedisConnectionPool call" in {
      createSecondaryIndex()
      val info = redisPooledConnection.ftInfo(UsersIndex)
      assert(info.containsValue(UsersIndex))
    }

  }

}
