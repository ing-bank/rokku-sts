package com.ing.wbaa.rokku.sts.keycloak

import akka.Done
import akka.actor.ActorSystem
import com.ing.wbaa.rokku.sts.config.KeycloakSettings
import com.ing.wbaa.rokku.sts.data.Username
import com.ing.wbaa.rokku.sts.helper.OAuth2TokenRequest
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.ExecutionContextExecutor

class KeycloakClientItTest extends AsyncWordSpec with Diagrams with OAuth2TokenRequest with KeycloakClient {

  override implicit val testSystem: ActorSystem = ActorSystem.create("test-system")
  override implicit val exContext: ExecutionContextExecutor = testSystem.dispatcher

  val keycloakSettings: KeycloakSettings = new KeycloakSettings(testSystem.settings.config)

  "Keycloak client" should {
    val username = "test"
    var createdUserId = KeycloakUserId("")

    "add a user" in {
      insertUserToKeycloak(Username(username)).map(addedUserId => {
        createdUserId = addedUserId
        assert(addedUserId.id.nonEmpty)
      })
    }

    "thrown error when adding existing user" in {
      recoverToSucceededIf[javax.ws.rs.WebApplicationException](insertUserToKeycloak(Username(username)))
    }

    "delete the created user" in {
      deleteUserFromKeycloak(createdUserId).map(d => assert(d == Done))
    }
  }
}
