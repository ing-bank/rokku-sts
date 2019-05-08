package com.ing.wbaa.rokku.sts.service

import java.time.Instant
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import com.ing.wbaa.rokku.sts.config.StsSettings
import com.ing.wbaa.rokku.sts.data.aws.AwsSessionTokenExpiration
import org.scalatest.{DiagrammedAssertions, WordSpec}

import scala.concurrent.duration.Duration

class TokenGenerationTest extends WordSpec with DiagrammedAssertions with TokenGeneration {

  val testSystem: ActorSystem = ActorSystem.create("test-system")
  override protected[this] def stsSettings: StsSettings = new StsSettings(testSystem.settings.config) {
    override val defaultTokenSessionDuration: Duration = Duration(8, TimeUnit.HOURS)
    override val maxTokenSessionDuration: Duration = Duration(24, TimeUnit.HOURS)
  }

  "Token generation" should {
    val allowedCharacters = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')).toSet

    "Generate Aws Credential" in {
      val generatedAwsCredential = generateAwsCredential
      assert(generatedAwsCredential.accessKey.value.forall(allowedCharacters.contains))
      assert(generatedAwsCredential.secretKey.value.forall(allowedCharacters.contains))
    }

    "Generate Aws Session".which {

      def assertExpirationValid(
        expiration: AwsSessionTokenExpiration,
        durationFromNow: Duration,
        allowedOffsetMillis: Long = 1000
      ) = {
        val diff = Instant.now().toEpochMilli + durationFromNow.toMillis - expiration.value.toEpochMilli
        assert(diff >= 0 && diff < allowedOffsetMillis)
      }

      "has no duration specified" in {
        val generatedAwsSession = generateAwsSession(None)
        assert(generatedAwsSession.sessionToken.value.forall(allowedCharacters.contains))
        assertExpirationValid(generatedAwsSession.expiration, stsSettings.defaultTokenSessionDuration)
      }

      "has duration within range of max specified" in {
        val customDuration = Duration(2, TimeUnit.HOURS)
        val generatedAwsSession = generateAwsSession(Some(customDuration))
        assert(generatedAwsSession.sessionToken.value.forall(allowedCharacters.contains))
        assertExpirationValid(generatedAwsSession.expiration, customDuration)
      }

      "has duration larger than max specified" in {
        val customDuration = Duration(25, TimeUnit.HOURS)
        val generatedAwsSession = generateAwsSession(Some(customDuration))
        assert(generatedAwsSession.sessionToken.value.forall(allowedCharacters.contains))
        assertExpirationValid(generatedAwsSession.expiration, stsSettings.maxTokenSessionDuration)
      }
    }
  }

}
