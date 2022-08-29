package com.ing.wbaa.rokku.sts.service.db

import akka.actor.ActorSystem
import com.ing.wbaa.rokku.sts.config.RedisSettings
import com.typesafe.scalalogging.LazyLogging
import redis.clients.jedis.{ JedisPooled, Jedis }
import redis.clients.jedis.exceptions.JedisDataException
import redis.clients.jedis.search.{ Schema, IndexDefinition, IndexOptions }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

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

  private val DuplicateKeyExceptionMsg = "Index already exists"

  protected val UsersIndex = "users-idx"

  protected lazy val redisPooledConnection: JedisPooled = new JedisPooled(
    redisSettings.host,
    redisSettings.port,
    redisSettings.username,
    redisSettings.password,
  )

  /**
   * Create secondary search index for users fields
   */
  protected[this] def createSecondaryIndex(): Unit = {
    val schema = new Schema()
      .addTagField("accessKey")
      .addTagField("isNPA")

    val prefixDefinition = new IndexDefinition()
      .setPrefixes("users:")

    try {
      redisPooledConnection.ftCreate(
        UsersIndex,
        IndexOptions.defaultOptions().setDefinition(prefixDefinition), schema)
      logger.info(s"Created index ${UsersIndex}")
    } catch {
      case exc: JedisDataException =>
        exc.getMessage() match {
          case DuplicateKeyExceptionMsg =>
            logger.info(s"Index ${UsersIndex} already exists. Continuing...")
          case _ =>
            logger.error(s"Unable to create index $UsersIndex. Error: ${exc.getMessage()}")
        }
      case exc: Exception =>
        logger.error(s"Unable to create index $UsersIndex. Error: ${exc.getMessage()}")
    }
  }

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
