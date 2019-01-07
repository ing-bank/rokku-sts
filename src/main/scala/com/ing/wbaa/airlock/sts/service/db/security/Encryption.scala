package com.ing.wbaa.airlock.sts.service.db.security

import java.util.Base64

import com.ing.wbaa.airlock.sts.config.StsSettings
import com.typesafe.scalalogging.LazyLogging
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

import scala.util.{ Failure, Success, Try }

trait Encryption extends LazyLogging {

  protected[this] def stsSettings: StsSettings

  private final lazy val MASTER_KEY = stsSettings.masterKey
  private final lazy val ALGORITHM = stsSettings.encryptionAlgorithm

  def encryptSecret(toEncrypt: String): String = {
    Try {
      val cipher: Cipher = Cipher.getInstance(ALGORITHM)
      cipher.init(Cipher.ENCRYPT_MODE, generateEncryptionKey)
      Base64.getEncoder.encodeToString(cipher.doFinal(toEncrypt.getBytes("UTF-8")))
    } match {
      case Success(encryptedKey) =>
        encryptedKey
      case Failure(ex) =>
        logger.error("Cannot encrypt secretKey")
        throw ex
    }
  }

  def decryptSecret(toDecrypt: String): String = {
    Try {
      val cipher: Cipher = Cipher.getInstance(ALGORITHM)
      cipher.init(Cipher.DECRYPT_MODE, generateEncryptionKey)
      new String(cipher.doFinal(Base64.getDecoder.decode(toDecrypt.getBytes("UTF-8"))))
    } match {
      case Success(encryptedKey) => encryptedKey
      case Failure(ex) =>
        logger.error("Cannot decrypt secretKey")
        throw ex
    }
  }

  private def generateEncryptionKey: SecretKeySpec = {
    new SecretKeySpec(MASTER_KEY.getBytes("UTF-8").take(32), ALGORITHM)
  }

}
