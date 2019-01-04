package com.ing.wbaa.airlock.sts.service.db.security

import java.math.BigInteger
import java.security.SecureRandom
import java.util.Base64

import com.typesafe.scalalogging.LazyLogging
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

import scala.util.{ Failure, Success, Try }

trait Encryption extends LazyLogging {

  // todo: move to config
  private final val ALGORITHM = "PBKDF2WithHmacSHA512"
  private final val HASH_LENGTH = 512
  private final val ITERATIONS = 10000
  private final val SALT_LENGTH = 32
  private final val BIGINT_LENGTH = 128

  def generateSalt: String = {
    val random = new SecureRandom()
    new BigInteger(BIGINT_LENGTH, random).toString(SALT_LENGTH)
  }

  // wrap return type to EncryptedKey
  def encryptSecret(toEncrypt: String, salt: String): String = Try {
    val pbeKeySpec: PBEKeySpec = new PBEKeySpec(toEncrypt.toCharArray, salt.getBytes("UTF-8"), ITERATIONS, HASH_LENGTH)
    val secretKeyFactory: SecretKeyFactory = SecretKeyFactory.getInstance(ALGORITHM)
    val hashedArray: Array[Byte] = secretKeyFactory.generateSecret(pbeKeySpec).getEncoded
    Base64.getEncoder.encodeToString(hashedArray)
  } match {
    case Success(encryptedKey) => encryptedKey
    case Failure(ex) =>
      logger.error("Cannot encrypt key")
      throw ex
  }

  def verifyEncryptedSecret(encrypted: String, toEncrypt: String, salt: String): Boolean = {
    encrypted == encryptSecret(toEncrypt, salt)
  }

}
