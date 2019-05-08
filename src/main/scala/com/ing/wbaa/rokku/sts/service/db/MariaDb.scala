package com.ing.wbaa.rokku.sts.service.db

import java.sql.Connection

import akka.actor.ActorSystem
import com.ing.wbaa.rokku.sts.config.MariaDBSettings
import com.typesafe.scalalogging.LazyLogging
import org.mariadb.jdbc.MariaDbPoolDataSource

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait MariaDb extends LazyLogging {

  implicit protected[this] def system: ActorSystem

  protected[this] def mariaDBSettings: MariaDBSettings

  implicit protected[this] lazy val dbExecutionContext: ExecutionContext =
    Try {
      system.dispatchers.lookup("db-dispatcher")
    } match {
      case Success(dispatcher) => dispatcher
      case Failure(ex) =>
        logger.error("Failed to configure dedicated db dispatcher, using default one, " + ex.getMessage)
        system.dispatcher
    }

  protected[this] lazy val mariaDbConnectionPool: MariaDbPoolDataSource = {
    val pool = new MariaDbPoolDataSource(mariaDBSettings.url)
    pool.setUser(mariaDBSettings.username)
    pool.setPassword(mariaDBSettings.password)
    pool
  }

  /**
   * Force initialization of the MariaDB client plugin.
   * This ensures we get connection errors on startup instead of when the first call is made.
   */
  protected[this] def forceInitMariaDbConnectionPool(): Unit = mariaDbConnectionPool

  protected[this] def withMariaDbConnection[T](databaseOperation: Connection => Future[T]): Future[T] =
    Try(mariaDbConnectionPool.getConnection()) match {
      case Success(connection) =>
        val result = databaseOperation(connection)
        connection.close()
        result
      case Failure(exc) =>
        logger.error("Error when getting a connection from the pool", exc)
        Future.failed(exc)
    }

  private[this] def selectOne(connection: Connection): Future[Unit] = Future {
    val statement = connection.prepareStatement("SELECT 1")
    val results = statement.executeQuery()

    assert(results.first())
  }

  /**
   * Performs a simple query to check the connectivity with the database/
   *
   * @return A future that is completed when the query returns or the failure
   *         otherwise.
   */
  final protected[this] def checkDbConnection(): Future[Unit] = withMariaDbConnection(selectOne)
}
