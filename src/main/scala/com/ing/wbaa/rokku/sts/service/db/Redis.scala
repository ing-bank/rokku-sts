package com.ing.wbaa.rokku.sts.service.db

import akka.actor.ActorSystem
import com.ing.wbaa.rokku.sts.config.RedisSettings
import com.typesafe.scalalogging.LazyLogging
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPooled

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

trait Redis extends LazyLogging {

  protected[this] implicit def system: ActorSystem

  protected def redisSettings: RedisSettings

  protected[this] implicit lazy val dbExecutionContext: ExecutionContext =
    Try {
      system.dispatchers.lookup("db-dispatcher")
    } match {
      case Success(dispatcher) => dispatcher
      case Failure(ex) =>
        logger.error(
          "Failed to configure dedicated db dispatcher, using default one, " + ex.getMessage
        )
        system.dispatcher
    }

  protected lazy val redisPooledConnection: JedisPooled = new JedisPooled(
    redisSettings.host,
    redisSettings.port,
    redisSettings.username,
    redisSettings.password,
  )

  protected[this] def withRedisPool[T](
      databaseOperation: JedisPooled => Future[T]
  ): Future[T] = {
    try {
      val result = databaseOperation(redisPooledConnection)
      result
    } catch {
      case exc: Exception =>
        logger.error("Error when performing database operation", exc)
        Future.failed(exc)
    }
  }

  /**
   * Performs a simple query to check the connectivity with the database/
   *
   * @return
   *   A future that is completed when the query returns or the failure
   *   otherwise.
   */
  protected[this] final def checkDbConnection(): Future[Unit] = {
    Future {
      val response = new Jedis(redisPooledConnection.getPool().getResource()).ping()
      assert(response.toLowerCase().equals("pong"))
    }
  }
}
