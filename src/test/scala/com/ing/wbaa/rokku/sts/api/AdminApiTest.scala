package com.ing.wbaa.rokku.sts.api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ FormData, StatusCodes }
import akka.http.scaladsl.server.{ AuthorizationFailedRejection, MissingFormFieldRejection, MissingHeaderRejection, Route }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.ing.wbaa.rokku.sts.config.StsSettings
import com.ing.wbaa.rokku.sts.data._
import com.ing.wbaa.rokku.sts.data.aws.AwsCredential
import com.ing.wbaa.rokku.sts.keycloak.KeycloakUserId
import org.scalatest.BeforeAndAfterAll
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Future

class AdminApiTest extends AnyWordSpec
  with Diagrams
  with ScalatestRouteTest
  with BeforeAndAfterAll {

  class testAdminApi extends AdminApi {

    val testSystem: ActorSystem = ActorSystem.create("test-system")

    override protected[this] def stsSettings: StsSettings = new StsSettings(testSystem.settings.config) {
      override val adminGroups: List[String] = List("admins")
    }

    protected[this] def verifyAuthenticationToken(token: BearerToken): Option[AuthenticationUserInfo] =
      token.value match {
        case "valid"    => Some(AuthenticationUserInfo(Username("username"), Set(UserGroup("admins"), UserGroup("group2")), AuthenticationTokenId("tokenOk"), Set.empty, isNPA = false))
        case "notAdmin" => Some(AuthenticationUserInfo(Username("username"), Set(UserGroup("group1"), UserGroup("group2")), AuthenticationTokenId("tokenOk"), Set.empty, isNPA = false))
        case _          => None
      }

    override protected[this] def insertAwsCredentials(username: Username, awsCredential: AwsCredential, isNpa: Boolean): Future[Boolean] = Future(true)

    override protected[this] def setAccountStatus(username: Username, enabled: Boolean): Future[Boolean] = Future.successful(true)
    override protected[this] def getAllNPAAccounts: Future[NPAAccountList] = Future(NPAAccountList(List(NPAAccount("testNPA", true))))

    override protected[this] def insertNpaCredentialsToVault(username: Username, safeName: String, awsCredential: AwsCredential): Future[Boolean] = Future(true)

    protected[this] def insertUserToKeycloak(username: Username): Future[KeycloakUserId] = username.value match {
      case "duplicate" => Future.failed(new RuntimeException("duplicate"))
      case _           => Future.successful(KeycloakUserId("1)"))
    }
  }

  private[this] val testRoute: Route = new testAdminApi().adminRoutes
  val validOAuth2TokenHeader: RequestTransformer = addHeader("Authorization", "Bearer valid")
  val notAdminOAuth2TokenHeader: RequestTransformer = addHeader("Authorization", "Bearer notAdmin")

  val bearerToken: String => String = issuer => {
    val stsSettings: StsSettings = new StsSettings(system.settings.config)
    val algorithm = Algorithm.HMAC256(stsSettings.decodeSecret)
    JWT.create()
      .withIssuer(issuer)
      .withClaim("service", "rokku")
      .sign(algorithm)
  }

  "Admin Api" should {
    "check response" that {
      "return OK if user is in admin groups and all FormFields are posted" in {
        Post("/admin/npa", FormData("npaAccount" -> "testNPA", "safeName" -> "vault", "awsAccessKey" -> "SomeAccessKey", "awsSecretKey" -> "SomeSecretKey")) ~> validOAuth2TokenHeader ~> testRoute ~> check {
          assert(status == StatusCodes.OK)
        }
      }
      "return OK if user is in admin groups for disable or enable request" in {
        Put("/admin/account/testuser/enable") ~> validOAuth2TokenHeader ~> testRoute ~> check {
          assert(status == StatusCodes.OK)
        }
      }
      "return Rejected if user is not in admin groups" in {
        Post("/admin/npa", FormData("npaAccount" -> "testNPA", "safeName" -> "vault", "awsAccessKey" -> "SomeAccessKey", "awsSecretKey" -> "SomeSecretKey")) ~> notAdminOAuth2TokenHeader ~> testRoute ~> check {
          assert(rejections.contains(AuthorizationFailedRejection))
        }
      }
      "return Rejected if user is not in admin groups for disable or enable request" in {
        Put("/admin/account/testuser/disable") ~> notAdminOAuth2TokenHeader ~> testRoute ~> check {
          assert(rejections.contains(AuthorizationFailedRejection))
        }
      }
      "return Rejected if user presents no token" in {
        Post("/admin/npa", FormData("npaAccount" -> "testNPA", "safeName" -> "vault", "awsAccessKey" -> "SomeAccessKey", "awsSecretKey" -> "SomeSecretKey")) ~> testRoute ~> check {
          assert(rejections.contains(AuthorizationFailedRejection))
        }
      }
      "return Rejected if service token is missing" in {
        Post("/admin/service/npa", FormData("npaAccount" -> "testNPA", "safeName" -> "vault", "awsAccessKey" -> "SomeAccessKey", "awsSecretKey" -> "SomeSecretKey")) ~> testRoute ~> check {
          assert(rejections.contains(MissingHeaderRejection("Authorization")))
        }
      }
      "return OK if service token is correct" in {
        Post("/admin/service/npa", FormData("npaAccount" -> "testNPA", "safeName" -> "vault", "awsAccessKey" -> "SomeAccessKey", "awsSecretKey" -> "SomeSecretKey"))
          .addHeader(RawHeader("Authorization", bearerToken("rokku"))) ~> testRoute ~> check {
            assert(status == StatusCodes.OK)
          }
      }
      "return Rejected if service token is not correct" in {
        Post("/admin/service/npa", FormData("npaAccount" -> "testNPA1", "safeName" -> "vault", "awsAccessKey" -> "SomeAccessKey", "awsSecretKey" -> "SomeSecretKey"))
          .addHeader(RawHeader("Authorization", bearerToken("rokku1"))) ~> Route.seal(testRoute) ~> check {
            assert(status == StatusCodes.BadRequest)
          }
      }
      "return Rejected if user FormData is invalid" in {
        Post("/admin/npa", FormData("npaAccount" -> "testNPA", "safeName" -> "vault", "awsAccessKey" -> "SomeAccessKey")) ~> validOAuth2TokenHeader ~> testRoute ~> check {
          assert(rejections.contains(MissingFormFieldRejection("awsSecretKey")))
        }
      }
      "return OK if user is in admin groups for list NPA's" in {
        Get("/admin/npa/list") ~> validOAuth2TokenHeader ~> testRoute ~> check {
          assert(status == StatusCodes.OK)
          assert(responseAs[String] == """{"data":[{"accountName":"testNPA","enabled":true}]}""")
        }
      }
      "return Rejected if user is not in admin groups for list NPA's" in {
        Get("/admin/npa/list") ~> notAdminOAuth2TokenHeader ~> testRoute ~> check {
          assert(rejections.contains(AuthorizationFailedRejection))
        }
      }
      "return OK when user is in admin groups for adding user to keycloak" in {
        Post("/admin/keycloak/user", FormData("username" -> "test1")) ~> validOAuth2TokenHeader ~> testRoute ~> check {
          assert(status == StatusCodes.OK)
          assert(responseAs[String] == """{"code":"Add user ok","message":"test1 added","target":"keycloak"}""")
        }
      }
      "return Error when user exists for adding user to keycloak" in {
        Post("/admin/keycloak/user", FormData("username" -> "duplicate")) ~> validOAuth2TokenHeader ~> testRoute ~> check {
          assert(status == StatusCodes.OK)
          assert(responseAs[String] == """{"code":"Add user error","message":"duplicate","target":"keycloak"}""")
        }
      }
      "return Rejected when user is not in admin groups for adding user to keycloak" in {
        Post("/admin/keycloak/user", FormData("username" -> "test1")) ~> notAdminOAuth2TokenHeader ~> testRoute ~> check {
          assert(rejections.contains(AuthorizationFailedRejection))
        }
      }
    }
  }
}
