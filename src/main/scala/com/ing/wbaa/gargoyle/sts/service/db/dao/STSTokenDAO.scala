/*package com.ing.wbaa.gargoyle.sts.service.db.dao

import java.sql.Connection

import com.ing.wbaa.gargoyle.sts.data.aws.AwsSessionToken
import org.mariadb.jdbc.MariaDbPoolDataSource

import scala.concurrent.Future

trait STSTokenDAO {

  protected[this] def mariaDbClientConnectionPool: MariaDbPoolDataSource

  protected[this] def withMariaDbConnection[T](f: Connection => Future[T]): Future[T]

  def getToken(awsSessionToken: AwsSessionToken): Unit = {
    val query = "SELECT * FROM TOKENS WHERE "
  }

}*/

