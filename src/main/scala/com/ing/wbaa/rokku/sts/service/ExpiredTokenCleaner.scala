package com.ing.wbaa.rokku.sts.service

import java.time.Instant
import java.time.temporal.ChronoUnit

import akka.actor.{ Actor, ActorSystem, Props, Timers }
import com.ing.wbaa.rokku.sts.data.aws.AwsSessionTokenExpiration
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

trait ExpiredTokenCleaner {

  protected[this] implicit def system: ActorSystem

  protected[this] def cleanExpiredTokens(expirationDate: AwsSessionTokenExpiration): Future[Int]

  system.actorOf(Props(new ExpiredTokenCleanActor(cleanExpiredTokens)), "expiredTokenCleanActor")
}

object ExpiredTokenCleanActor {

  private case object CleanerKey

  private case object CleanToken

}

class ExpiredTokenCleanActor(cleanExpiredTokens: AwsSessionTokenExpiration => Future[Int]) extends Actor with Timers with LazyLogging {

  import ExpiredTokenCleanActor._

  implicit val exContext: ExecutionContext = context.dispatcher

  timers.startPeriodicTimer(CleanerKey, CleanToken, 1.day)

  override def receive: Receive = {

    case CleanToken =>
      logger.debug("start clean expired tokens")
      cleanExpiredTokens(AwsSessionTokenExpiration(Instant.now().minus(1, ChronoUnit.DAYS))).andThen {
        case result => logger.debug("removed {} tokens", result)
      }

  }
}
