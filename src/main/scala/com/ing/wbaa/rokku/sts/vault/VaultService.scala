package com.ing.wbaa.rokku.sts.vault

import com.bettercloud.vault.response.LogicalResponse
import com.bettercloud.vault.{Vault, VaultConfig}
import com.ing.wbaa.rokku.sts.config.VaultSettings
import com.ing.wbaa.rokku.sts.data.UserName
import com.ing.wbaa.rokku.sts.data.aws.AwsCredential
import com.typesafe.scalalogging.LazyLogging

import scala.jdk.CollectionConverters._

trait VaultService extends LazyLogging {

  protected[this] def vaultSettings: VaultSettings

  protected lazy val vault: Vault = {
    val vault = new Vault(new VaultConfig()
      .address(vaultSettings.vaultUrl)
      .engineVersion(2)
      .token(vaultSettings.vaultToken)
      .build())
    vault
  }

  def insertNpaCredentialsToVault(username: UserName, awsCredential: AwsCredential): LogicalResponse = {
    val secretsToSave: Map[String,AnyRef]  = Map("accessKey" -> awsCredential.accessKey.value,"secretKey" -> awsCredential.secretKey.value)
    vault.logical().write(s"secret/${username.value}", secretsToSave.asJava)
  }
}
