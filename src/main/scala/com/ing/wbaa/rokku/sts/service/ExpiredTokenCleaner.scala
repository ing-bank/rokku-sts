package com.ing.wbaa.rokku.sts.service

import java.time.Instant
import java.time.temporal.ChronoUnit

import akka.actor.ActorSystem
import com.ing.wbaa.rokku.sts.data.aws.AwsSessionTokenExpiration
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.concurrent.duration._

trait ExpiredTokenCleaner extends Runnable with LazyLogging {

  protected[this] implicit def system: ActorSystem

  protected[this] def cleanExpiredTokens(expirationDate: AwsSessionTokenExpiration): Future[Int]

  protected[this] implicit val exContext: ExecutionContextExecutor = system.dispatcher

  system.scheduler.scheduleWithFixedDelay(10.seconds, 1.day)(this)

  override def run(): Unit = {
    logger.debug("start clean expired tokens")
    cleanExpiredTokens(AwsSessionTokenExpiration(Instant.now().minus(1, ChronoUnit.DAYS))).andThen {
      case result => logger.debug("removed {} tokens", result)
    }
  }
}
