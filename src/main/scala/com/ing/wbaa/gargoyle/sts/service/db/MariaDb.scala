package com.ing.wbaa.gargoyle.sts.service.db

import java.sql.Connection

import com.ing.wbaa.gargoyle.sts.config.GargoyleMariaDBSettings
import com.typesafe.scalalogging.LazyLogging
import org.mariadb.jdbc.MariaDbPoolDataSource

import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

trait MariaDb extends LazyLogging {

  protected[this] def gargoyleMariaDBSettings: GargoyleMariaDBSettings

  protected[this] lazy val mariaDbConnectionPool: MariaDbPoolDataSource = {
    val pool = new MariaDbPoolDataSource(
      gargoyleMariaDBSettings.host,
      gargoyleMariaDBSettings.port,
      gargoyleMariaDBSettings.database
    )
    pool.setUser(gargoyleMariaDBSettings.username)
    pool.setPassword(gargoyleMariaDBSettings.password)
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
}
