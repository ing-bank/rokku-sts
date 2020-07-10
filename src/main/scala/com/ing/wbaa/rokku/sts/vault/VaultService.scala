package com.ing.wbaa.rokku.sts.vault

import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import com.bettercloud.vault.response.VaultResponse
import com.bettercloud.vault.{Vault, VaultConfig}
import com.ing.wbaa.rokku.sts.config.VaultSettings
import com.ing.wbaa.rokku.sts.data.UserName
import com.ing.wbaa.rokku.sts.data.aws.AwsCredential
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

trait VaultService extends LazyLogging {

  protected[this] def vaultSettings: VaultSettings

  protected[this] implicit def system: ActorSystem

  implicit protected[this] def executionContext: ExecutionContext

  protected lazy val vault: Vault = {
    val vault = new Vault(new VaultConfig()
      .address(vaultSettings.vaultUrl)
      .engineVersion(2)
      .token(vaultSettings.vaultToken)
      .readTimeout(vaultSettings.readTimeout)
      .openTimeout(vaultSettings.openTimeout)
      .build())
    vault
  }

  def insertNpaCredentialsToVault(username: UserName, awsCredential: AwsCredential): Future[Boolean] = Future {
    val secretsToSave: Map[String, AnyRef] = Map("accessKey" -> awsCredential.accessKey.value, "secretKey" -> awsCredential.secretKey.value)

    logger.info(s"Performing vault write operation to ${vaultSettings.vaultPath} for ${username.value}")
    Try {
      vault.withRetries(vaultSettings.retries, 500)
        .logical()
        .write(vaultSettings.vaultPath + "/" + username.value, secretsToSave.asJava)
    } match {
      case Success(writeOperation) => reportOnOperationOutcome(writeOperation, username)
      case Failure(e: Throwable)   => reportOnOperationOutcome(e, username)
    }
  }(executionContext)

  private def reportOnOperationOutcome(s: VaultResponse, name: UserName): Boolean = {
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

  private def reportOnOperationOutcome(e: Throwable, name: UserName): Boolean = {
    logger.error(s"Couldn't write credentials for ${name.value} to vault: \n" + e.getMessage)
    false
  }

}
