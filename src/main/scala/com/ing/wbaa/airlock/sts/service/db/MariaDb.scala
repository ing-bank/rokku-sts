package com.ing.wbaa.airlock.sts.service.db

import java.sql.Connection

import com.ing.wbaa.airlock.sts.config.MariaDBSettings
import com.typesafe.scalalogging.LazyLogging
import org.mariadb.jdbc.MariaDbPoolDataSource

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

trait MariaDb extends LazyLogging {

  protected[this] def mariaDBSettings: MariaDBSettings

  protected[this] implicit def executionContext: ExecutionContext

  protected[this] lazy val mariaDbConnectionPool: MariaDbPoolDataSource = {
    val pool = new MariaDbPoolDataSource(
      mariaDBSettings.host,
      mariaDBSettings.port,
      mariaDBSettings.database
    )
    pool.setUser(mariaDBSettings.username)
    pool.setPassword(mariaDBSettings.password)
    pool
  }

  /**
   * Force initialization of the MariaDB client plugin.
   * This ensures we get connection errors on startup instead of when the first call is made.
   */
  protected[this] def forceInitMariaDbConnectionPool(): Unit = mariaDbConnectionPool

  protected[this] def withMariaDbConnection[T](databaseOperation: Connection => Future[T]): Future[T] = {
    Try(mariaDbConnectionPool.getConnection()) match {
      case Success(connection) =>
        val result = databaseOperation(connection)
        connection.close()
        result
      case Failure(exc) =>
        logger.error("Error when getting a connection from the pool", exc)
        Future.failed(exc)
    }
  }

  private[this] def selectOne(connection: Connection): Future[Unit] = Future {
    val statement = connection.prepareStatement("SELECT 1")
    val results = statement.executeQuery()

    assert(results.first())
  }

  /**
   * Performs a simple query to check the connectivity with the database/
   * @return A future that is completed when the query returns or the failure
   *         otherwise.
   */
  protected[this] final def checkDbConnection(): Future[Unit] = withMariaDbConnection(selectOne)
}
