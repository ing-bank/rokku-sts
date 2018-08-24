package com.ing.wbaa.gargoyle.sts.service

import java.time.Instant
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.sts.config.GargoyleStsSettings
import com.ing.wbaa.gargoyle.sts.data.aws.AwsSessionTokenExpiration
import org.scalatest.{ DiagrammedAssertions, WordSpec }

import scala.concurrent.duration.Duration

class TokenGenerationTest extends WordSpec with DiagrammedAssertions {

  val testDefaultTokenSessionDuration = Duration(8, TimeUnit.HOURS)
  val testMaxTokenSessionDuration: Duration = Duration(24, TimeUnit.HOURS)

  val testTokenGeneration: TokenGeneration = new TokenGeneration {
    val testSystem: ActorSystem = ActorSystem.create("test-system")
    override protected[this] def stsSettings: GargoyleStsSettings = new GargoyleStsSettings(testSystem.settings.config) {
      override val defaultTokenSessionDuration: Duration = testDefaultTokenSessionDuration
      override val maxTokenSessionDuration: Duration = testMaxTokenSessionDuration
    }
  }

  "Token generation" should {
    val allowedCharacters = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')).toSet

    "Generate Aws Credential" in {
      val generatedAwsCredential = testTokenGeneration.generateAwsCredential
      assert(generatedAwsCredential.accessKey.value.forall(allowedCharacters.contains))
      assert(generatedAwsCredential.secretKey.value.forall(allowedCharacters.contains))
    }

    "Generate Aws Session" which {

      def assertExpirationValid(expiration: AwsSessionTokenExpiration, durationFromNow: Duration, allowedOffsetMillis: Long = 1000) = {
        val diff = Instant.now().toEpochMilli + durationFromNow.toMillis - expiration.value.toEpochMilli
        assert(diff >= 0 && diff < allowedOffsetMillis)
      }

      "has no duration specified" in {
        val generatedAwsSession = testTokenGeneration.generateAwsSession(None)
        assert(generatedAwsSession.sessionToken.value.forall(allowedCharacters.contains))
        assertExpirationValid(generatedAwsSession.expiration, testDefaultTokenSessionDuration)
      }

      "has duration within range of max specified" in {
        val customDuration = Duration(2, TimeUnit.HOURS)
        val generatedAwsSession = testTokenGeneration.generateAwsSession(Some(customDuration))
        assert(generatedAwsSession.sessionToken.value.forall(allowedCharacters.contains))
        assertExpirationValid(generatedAwsSession.expiration, customDuration)
      }

      "has duration larger than max specified" in {
        val customDuration = Duration(25, TimeUnit.HOURS)
        val generatedAwsSession = testTokenGeneration.generateAwsSession(Some(customDuration))
        assert(generatedAwsSession.sessionToken.value.forall(allowedCharacters.contains))
        assertExpirationValid(generatedAwsSession.expiration, testMaxTokenSessionDuration)
      }
    }
  }

}
