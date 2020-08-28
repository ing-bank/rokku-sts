package com.ing.wbaa.rokku.sts.keycloak

import akka.Done
import com.ing.wbaa.rokku.sts.config.KeycloakSettings
import com.ing.wbaa.rokku.sts.data.UserName
import com.typesafe.scalalogging.LazyLogging
import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.{ CreatedResponseUtil, KeycloakBuilder }
import org.keycloak.representations.idm.UserRepresentation

import scala.concurrent.{ ExecutionContext, Future }

case class KeycloakUserId(id: String)

trait KeycloakClient extends LazyLogging {

  implicit protected[this] def executionContext: ExecutionContext

  protected[this] def keycloakSettings: KeycloakSettings

  private lazy val keycloak = KeycloakBuilder.builder()
    .serverUrl(s"${keycloakSettings.url}/auth")
    .realm(keycloakSettings.realm)
    .grantType(OAuth2Constants.PASSWORD)
    .clientId(keycloakSettings.resource)
    .clientSecret(keycloakSettings.clientSecret)
    .username(keycloakSettings.adminUsername)
    .password(keycloakSettings.adminPassword)
    .build()

  /**
   * Create a disabled user in a keycloak
   *
   * @param username npa username
   * @return the created user keycloak id
   */
  def insertUserToKeycloak(username: UserName): Future[KeycloakUserId] = {

    val user = new UserRepresentation()
    user.setEnabled(false)
    user.setUsername(username.value)
    user.setFirstName("added by STS")
    user.setLastName("added by STS")

    Future {
      val response = keycloak.realm(keycloakSettings.realm).users().create(user)
      val userId = CreatedResponseUtil.getCreatedId(response)
      logger.info("Keycloak add user status {} and Response: {}", response.getStatus, response.getStatusInfo)
      logger.info("user {} added to keyckoak - userid={}", username, userId)
      KeycloakUserId(userId)
    }
  }

  /**
   * delete a user from keycloak
   * @param userID - the keycloak user id to delete
   * @return Done if no error
   */
  def deleteUserFromKeycloak(userID: KeycloakUserId): Future[Done] = {
    Future {
      keycloak.realm(keycloakSettings.realm).users().delete(userID.id)
      logger.info("user {} deleted from keycloak", userID)
      Done
    }
  }

}
