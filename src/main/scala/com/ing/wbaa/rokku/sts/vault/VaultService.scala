package com.ing.wbaa.rokku.sts.vault

import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import com.bettercloud.vault.response.VaultResponse
import com.bettercloud.vault.{ Vault, VaultConfig }
import com.ing.wbaa.rokku.sts.config.VaultSettings
import com.ing.wbaa.rokku.sts.data.Username
import com.ing.wbaa.rokku.sts.data.aws.AwsCredential
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContext, Future }
import scala.jdk.CollectionConverters._
import scala.util.{ Failure, Success, Try }

trait VaultService extends LazyLogging {

  protected[this] def vaultSettings: VaultSettings

  protected[this] implicit def system: ActorSystem

  implicit protected[this] def executionContext: ExecutionContext

  def insertNpaCredentialsToVault(username: Username, safeName: String, awsCredential: AwsCredential): Future[Boolean] = Future {

    if (safeName.equalsIgnoreCase(vaultSettings.vaultPath)) {
      writeSingleVaultEntry(username, safeName, awsCredential)
    } else {
      writeSingleVaultEntry(username, vaultSettings.vaultPath, awsCredential)
      writeSingleVaultEntry(username, safeName, awsCredential)
    }

  }(executionContext)

  private def writeSingleVaultEntry(username: Username, safeName: String, awsCredential: AwsCredential) = {
    val vault = getVaultInstance()
    val secretsToSave: Map[String, AnyRef] = Map("accessKey" -> awsCredential.accessKey.value, "secretKey" -> awsCredential.secretKey.value)
    logger.info(s"Performing vault write operation to ${vaultSettings.vaultPath} for ${username.value}")
    Try {
      vault.withRetries(vaultSettings.retries, 500)
        .logical()
        .write(safeName + "/" + username.value, secretsToSave.asJava)
    } match {
      case Success(writeOperation) => reportOnOperationOutcome(writeOperation, username)
      case Failure(e: Throwable)   => reportOnOperationOutcome(e, username)
    }
  }

  private def getVaultInstance(): Vault = {
    val vaultForAuth = new Vault(new VaultConfig()
      .address(vaultSettings.vaultUrl)
      .engineVersion(2)
      .readTimeout(vaultSettings.readTimeout)
      .openTimeout(vaultSettings.openTimeout)
      .build())

    val token: String = Try { vaultForAuth.auth().loginByJwt(vaultSettings.auth, vaultSettings.role, vaultSettings.jwt).getAuthClientToken() } match {
      case Success(value)        => value
      case Failure(e: Throwable) => { logger.error(e.getMessage); throw e }
    }

    val vault = new Vault(new VaultConfig()
      .address(vaultSettings.vaultUrl)
      .engineVersion(2)
      .token(token)
      .readTimeout(vaultSettings.readTimeout)
      .openTimeout(vaultSettings.openTimeout)
      .build())
    vault
  }

  private def reportOnOperationOutcome(s: VaultResponse, name: Username): Boolean = {
    val status = s.getRestResponse.getStatus
    val retries = s.getRetries
    val body = new String(s.getRestResponse.getBody, StandardCharsets.UTF_8)

    logger.debug("Vault operation report: \n" +
      s"Retires: $retries \n" +
      s"Response code: $status \n" +
      s"Body repose: $body")

    if (status == 200 || status == 204) {
      logger.info(s"Succesfully wrote credentials for ${name.value} to vault")
      true
    } else {
      logger.error(s"Couldn't write credentials for ${name.value} to vault, got $status return code")
      false
    }
  }

  private def reportOnOperationOutcome(e: Throwable, name: Username): Boolean = {
    logger.error(s"Couldn't write credentials for ${name.value} to vault: \n" + e.getMessage)
    false
  }

}
