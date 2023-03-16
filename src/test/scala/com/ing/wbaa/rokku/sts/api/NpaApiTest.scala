package com.ing.wbaa.rokku.sts.api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ FormData, StatusCodes }
import akka.http.scaladsl.server.{ AuthorizationFailedRejection, MissingFormFieldRejection, MissingHeaderRejection, Route }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.ing.wbaa.rokku.sts.config.KeycloakSettings
import com.ing.wbaa.rokku.sts.config.StsSettings
import com.ing.wbaa.rokku.sts.data._
import com.ing.wbaa.rokku.sts.data.aws.AwsCredential
import com.ing.wbaa.rokku.sts.data.aws.AwsAccessKey
import com.ing.wbaa.rokku.sts.data.aws.AwsSecretKey
import com.ing.wbaa.rokku.sts.keycloak.KeycloakUserId
import org.scalatest.BeforeAndAfterAll
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AnyWordSpec
import com.ing.wbaa.rokku.sts.service.ConflictException

import scala.concurrent.Future

class NpaApiTest extends AnyWordSpec
  with Diagrams
  with ScalatestRouteTest
  with BeforeAndAfterAll {

  val npaUser = "npa-user"
  val disabledNpaUser = "npa-user-disabled"
  val nonNpaUser = "non-npa-user"
  val npaAwsCredential = AwsCredential(AwsAccessKey("accesskey"), AwsSecretKey("secretkey"))

  val npaUserSessionToken: RequestTransformer = addHeader("Authorization", s"Bearer ${npaUser}-token")
  val disabledNpaUserSessionToken: RequestTransformer = addHeader("Authorization", s"Bearer ${disabledNpaUser}-token")
  val nonNpaUserSessionToken: RequestTransformer = addHeader("Authorization", s"Bearer ${nonNpaUser}-token")

  private[this] val testRoute: Route = new testNpaApi().npaRoutes

  class testNpaApi extends NpaApi {

    val testSystem: ActorSystem = ActorSystem.create("test-system")

    override protected[this] def keycloakSettings: KeycloakSettings = new KeycloakSettings(testSystem.settings.config) {
      override val npaRole: String = "rokku-npa-test"
    }

    override protected[this] def stsSettings: StsSettings = new StsSettings(testSystem.settings.config) {}

    protected[this] def registerNpaUser(userName: Username): Future[AwsCredential] = userName.value match {
      case `npaUser`         => Future.successful(npaAwsCredential)
      case `nonNpaUser`      => Future.successful(npaAwsCredential)
      case `disabledNpaUser` => Future.failed(new ConflictException("User already exists"))
      case _                 => Future.failed(new RuntimeException("Unexpected call of function registerNpaUser"))
    }

    protected[this] def getUserAccountByName(userName: Username): Future[Option[UserAccount]] = userName.value match {
      case `npaUser`         => Future.successful(Some(UserAccount(userName, Some(npaAwsCredential), AccountStatus(true), NPA(true), Set())))
      case `nonNpaUser`      => Future.successful(Some(UserAccount(userName, Some(npaAwsCredential), AccountStatus(true), NPA(false), Set())))
      case `disabledNpaUser` => Future.successful(Some(UserAccount(userName, Some(npaAwsCredential), AccountStatus(false), NPA(true), Set())))
      case _                 => Future.failed(new RuntimeException("Unexpected call of function getUserAccountByName"))
    }

    protected[this] def verifyAuthenticationToken(token: BearerToken): Option[AuthenticationUserInfo] =
      token.value match {
        case "non-npa-user-token"      => Some(AuthenticationUserInfo(Username(nonNpaUser), Set(), AuthenticationTokenId("nonNpaUser?"), Set()))
        case "npa-user-token"          => Some(AuthenticationUserInfo(Username(npaUser), Set(), AuthenticationTokenId("npaUser"), Set(UserAssumeRole(keycloakSettings.npaRole))))
        case "npa-user-disabled-token" => Some(AuthenticationUserInfo(Username(disabledNpaUser), Set(), AuthenticationTokenId("disabledNpaUser"), Set(UserAssumeRole(keycloakSettings.npaRole))))
        case _                         => None
      }

  }

  "Npa Api" should {
    "check response" that {
      "return 201 and expected awsCredential when registering a user as NPA" in {
        Post("/npa/registry") ~> npaUserSessionToken ~> testRoute ~> check {
          assert(status == StatusCodes.Created)
          assert(responseAs[String] == s"""{"accessKey":"${npaAwsCredential.accessKey.value}","secretKey":"${npaAwsCredential.secretKey.value}"}""")
        }
      }
      "return 409 when user already exists" in {
        Post("/npa/registry") ~> disabledNpaUserSessionToken ~> testRoute ~> check {
          assert(status == StatusCodes.Conflict)
        }
      }
      "return 403 when user post request doesn't have the required keycloak role" in {
        Post("/npa/registry") ~> nonNpaUserSessionToken ~> testRoute ~> check {
          assert(rejections.contains(AuthorizationFailedRejection))
        }
      }
      "return 200 when npa user requests to get his credentials" in {
        Get("/npa/credentials") ~> npaUserSessionToken ~> testRoute ~> check {
          assert(status == StatusCodes.OK)
          assert(responseAs[String] == s"""{"accessKey":"${npaAwsCredential.accessKey.value}","secretKey":"${npaAwsCredential.secretKey.value}"}""")
        }
      }
      "return 404 when npa user requests to get his credentials but the user is disabled" in {
        Get("/npa/credentials") ~> disabledNpaUserSessionToken ~> testRoute ~> check {
          assert(status == StatusCodes.NotFound)
        }
      }
      "return 403 when user get requestdoesn't have the required keycloak role" in {
        Get("/npa/credentials") ~> nonNpaUserSessionToken ~> testRoute ~> check {
          assert(rejections.contains(AuthorizationFailedRejection))
        }
      }
    }
  }
}
