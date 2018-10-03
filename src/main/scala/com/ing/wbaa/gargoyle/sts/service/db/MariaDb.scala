package com.ing.wbaa.gargoyle.sts.service.db

import java.sql.Connection

import com.ing.wbaa.gargoyle.sts.config.GargoyleMariaDBSettings
import com.typesafe.scalalogging.LazyLogging
import org.mariadb.jdbc.MariaDbPoolDataSource

import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

trait MariaDb extends LazyLogging {

  protected[this] def gargoyleMariaDBSettings: GargoyleMariaDBSettings

  private lazy val MARIA_DB_DRIVER = "jdbc:mariadb://"

  private[this] lazy val mariaDbConnectionPool = {
    val url = MARIA_DB_DRIVER + gargoyleMariaDBSettings.host + ":" + gargoyleMariaDBSettings.port +
      "/" + gargoyleMariaDBSettings.database
    val pool = new MariaDbPoolDataSource(url)
    pool.setUser(gargoyleMariaDBSettings.username)
    pool.setPassword(gargoyleMariaDBSettings.password)
    pool

  }

  /**
   * Force initialization of the MariaDB client plugin.
   * This ensures we get connection errors on startup instead of when the first call is made.
   */
  protected[this] def mariaDbClientConnectionPool: MariaDbPoolDataSource = mariaDbConnectionPool

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
