package com.ing.wbaa.rokku.sts.api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ FormData, StatusCodes }
import akka.http.scaladsl.server.{ AuthorizationFailedRejection, MissingFormFieldRejection, Route }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.ing.wbaa.rokku.sts.config.StsSettings
import com.ing.wbaa.rokku.sts.data._
import com.ing.wbaa.rokku.sts.data.aws.AwsCredential
import org.scalatest.{ BeforeAndAfterAll, DiagrammedAssertions, WordSpec }

import scala.concurrent.Future

class AdminApiTest extends WordSpec
  with DiagrammedAssertions
  with ScalatestRouteTest
  with BeforeAndAfterAll {

  class testAdminApi extends AdminApi {

    val testSystem: ActorSystem = ActorSystem.create("test-system")

    override protected[this] def stsSettings: StsSettings = new StsSettings(testSystem.settings.config) {
      override val adminGroups: List[String] = List("admins")
    }

    protected[this] def verifyAuthenticationToken(token: BearerToken): Option[AuthenticationUserInfo] =
      token.value match {
        case "valid"    => Some(AuthenticationUserInfo(UserName("username"), Set(UserGroup("admins"), UserGroup("group2")), AuthenticationTokenId("tokenOk")))
        case "notAdmin" => Some(AuthenticationUserInfo(UserName("username"), Set(UserGroup("group1"), UserGroup("group2")), AuthenticationTokenId("tokenOk")))
        case _          => None
      }

    override protected[this] def insertAwsCredentials(username: UserName, awsCredential: AwsCredential, isNpa: Boolean): Future[Boolean] = Future(true)

    override protected[this] def enableOrDisableUserAccount(username: UserName, enabled: Boolean): Future[Boolean] = Future.successful(true)
  }

  private[this] val testRoute: Route = new testAdminApi().adminRoutes
  val validOAuth2TokenHeader: RequestTransformer = addHeader("Authorization", "Bearer valid")
  val notAdminOAuth2TokenHeader: RequestTransformer = addHeader("Authorization", "Bearer notAdmin")

  "Admin Api" should {
    "check response" that {
      "return OK if user is in admin groups and all FormFields are posted" in {
        Post("/admin/npa", FormData("npaAccount" -> "testNPA", "awsAccessKey" -> "SomeAccessKey", "awsSecretKey" -> "SomeSecretKey")) ~> validOAuth2TokenHeader ~> testRoute ~> check {
          assert(status == StatusCodes.OK)
        }
      }
      "return OK if user is in admin groups for disable or enable request" in {
        Put("/admin/account/testuser/enable") ~> validOAuth2TokenHeader ~> testRoute ~> check {
          assert(status == StatusCodes.OK)
        }
      }
      "return Rejected if user is not in admin groups" in {
        Post("/admin/npa", FormData("npaAccount" -> "testNPA", "awsAccessKey" -> "SomeAccessKey", "awsSecretKey" -> "SomeSecretKey")) ~> notAdminOAuth2TokenHeader ~> testRoute ~> check {
          assert(rejections.contains(AuthorizationFailedRejection))
        }
      }
      "return Rejected if user is not in admin groups for disable or enable request" in {
        Put("/admin/account/testuser/disable") ~> notAdminOAuth2TokenHeader ~> testRoute ~> check {
          assert(rejections.contains(AuthorizationFailedRejection))
        }
      }
      "return Rejected if user presents no token" in {
        Post("/admin/npa", FormData("npaAccount" -> "testNPA", "awsAccessKey" -> "SomeAccessKey", "awsSecretKey" -> "SomeSecretKey")) ~> testRoute ~> check {
          assert(rejections.contains(AuthorizationFailedRejection))
        }
      }
      "return Rejected if user FormData is invalid" in {
        Post("/admin/npa", FormData("npaAccount" -> "testNPA", "awsAccessKey" -> "SomeAccessKey")) ~> validOAuth2TokenHeader ~> testRoute ~> check {
          assert(rejections.contains(MissingFormFieldRejection("awsSecretKey")))
        }
      }
    }
  }

}
