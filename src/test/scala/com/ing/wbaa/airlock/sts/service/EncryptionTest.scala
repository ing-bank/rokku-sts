package com.ing.wbaa.airlock.sts.service

import akka.actor.ActorSystem
import com.ing.wbaa.airlock.sts.config.StsSettings
import com.ing.wbaa.airlock.sts.service.db.security.Encryption
import org.scalatest.{ DiagrammedAssertions, WordSpec }

class EncryptionTest extends WordSpec with DiagrammedAssertions with Encryption {

  val testSystem: ActorSystem = ActorSystem.create("test-system")
  override protected[this] def stsSettings: StsSettings = new StsSettings(testSystem.settings.config) {
    override val masterKey: String = "PjosfsapupwieqXZWasdi8986pasdcmasxcyvuuxewtgwebcaih"
    override val encryptionAlgorithm: String = "AES"
  }

  private final val SECRET_KEY = "mySuperSecret"

  "Encryption" should {
    "Encrypt and decrypt secretKey" in {
      val encryptedKey = encryptSecret(SECRET_KEY)
      val decryptedKey = decryptSecret(encryptedKey)

      assert(decryptedKey == SECRET_KEY)
    }
  }

}
