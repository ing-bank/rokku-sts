package com.ing.wbaa.rokku.sts.service

import akka.actor.ActorSystem
import com.ing.wbaa.rokku.sts.config.StsSettings
import com.ing.wbaa.rokku.sts.service.db.security.Encryption
import org.scalatest.{ DiagrammedAssertions, WordSpec }

class EncryptionTest extends WordSpec with DiagrammedAssertions with Encryption {

  val testSystem: ActorSystem = ActorSystem.create("test-system")
  override protected[this] def stsSettings: StsSettings = new StsSettings(testSystem.settings.config) {
    override val masterKey: String = "PjosfsapupwieqXZWasdi8986pasdcmasxcyvuuxewtgwebcaih"
    override val encryptionAlgorithm: String = "AES"
  }

  private final val SECRET_KEY = "mySuperSecret"
  private final val USERNAME = "someAccessKey"

  "Encryption" should {
    "Encrypt and decrypt secretKey" in {
      val encryptedKey = encryptSecret(SECRET_KEY, USERNAME)
      val decryptedKey = decryptSecret(encryptedKey, USERNAME)

      assert(decryptedKey == SECRET_KEY)
    }
  }

}
