package com.ing.wbaa.rokku.sts.service.db

import akka.actor.ActorSystem
import com.ing.wbaa.rokku.sts.config.RedisSettings
import com.typesafe.scalalogging.LazyLogging
import redis.clients.jedis.{ JedisPooled, Connection, Jedis }
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

  protected lazy val redisConnectionPool: JedisPooled = new JedisPooled(
    redisSettings.host,
    redisSettings.port,
    redisSettings.username,
    redisSettings.password,
  )

  /**
   * Force initialization of the Redis client. This ensures we get
   * connection errors on startup instead of when the first call is made.
   */
  protected[this] def forceInitRedisConnectionPool(): Unit = {
    val schema = new Schema()
      .addTagField("accessKey")
      .addTagField("isNPA")

    val prefixDefinition = new IndexDefinition()
      .setPrefixes("users:")

    // @TODO Check return value
    try {
      redisConnectionPool.ftCreate(
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

  protected[this] def withRedisConnection[T](
      databaseOperation: Connection => Future[T]
  ): Future[T] = {
    Try(redisConnectionPool.getPool().getResource()) match {
      case Success(connection) =>
        val result = databaseOperation(connection)
        connection.close()
        result
      case Failure(exc) =>
        logger.error("Error when getting a connection from the pool", exc)
        Future.failed(exc)
    }
  }

  protected[this] def withRedisPool[T](
      databaseOperation: JedisPooled => Future[T]
  ): Future[T] = {
    try {
      val result = databaseOperation(redisConnectionPool)
      result
    } catch {
      case exc: Exception =>
        logger.error("Error when performing database operation", exc)
        Future.failed(exc)
    }
  }

  private[this] def ping(connection: Connection): Future[Unit] = Future {
    val response = new Jedis(connection).ping()

    assert(response.toLowerCase().equals("pong"))
  }

  /**
   * Performs a simple query to check the connectivity with the database/
   *
   * @return
   *   A future that is completed when the query returns or the failure
   *   otherwise.
   */
  protected[this] final def checkDbConnection(): Future[Unit] =
    withRedisConnection(ping)
}
