package ing.wbaa.s3.sts.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

class S3Api {

  val routes: Route = getAccessToken

  val testAssumeRoleWithWebIdentityResponse: String =
    """<AssumeRoleWithWebIdentityResponse xmlns="https://sts.amazonaws.com/doc/2011-06-15/">
                         <AssumeRoleWithWebIdentityResult>
                           <SubjectFromWebIdentityToken>amzn1.account.AF6RHO7KZU5XRVQJGXK6HB56KR2A</SubjectFromWebIdentityToken>
                           <Audience>client.5498841531868486423.1548@apps.example.com</Audience>
                           <AssumedRoleUser>
                             <Arn>arn:aws:sts::123456789012:assumed-role/FederatedWebIdentityRole/app1</Arn>
                             <AssumedRoleId>AROACLKWSDQRAOEXAMPLE:app1</AssumedRoleId>
                           </AssumedRoleUser>
                           <Credentials>
                             <SessionToken>AQoDYXdzEE0a8ANXXXXXXXXNO1ewxE5TijQyp+IEXAMPLE</SessionToken>
                             <SecretAccessKey>wJalrXUtnFEMI/K7MDENG/bPxRfiCYzEXAMPLEKEY</SecretAccessKey>
                             <Expiration>2014-10-24T23:00:23Z</Expiration>
                             <AccessKeyId>ASgeIAIOSFODNN7EXAMPLE</AccessKeyId>
                           </Credentials>
                           <Provider>www.amazon.com</Provider>
                         </AssumeRoleWithWebIdentityResult>
                         <ResponseMetadata>
                           <RequestId>ad4156e9-bce1-11e2-82e6-6b6efEXAMPLE</RequestId>
                         </ResponseMetadata>
                       </AssumeRoleWithWebIdentityResponse>""".stripMargin

  val testGetSessionTokenResponse: String =
    """
      <GetSessionTokenResponse xmlns="https://sts.amazonaws.com/doc/2011-06-15/">
        <GetSessionTokenResult>
          <Credentials>
            <SessionToken>
             AQoEXAMPLEH4aoAH0gNCAPyJxz4BlCFFxWNE1OPTgk5TthT+FvwqnKwRcOIfrRh3c/L
             To6UDdyJwOOvEVPvLXCrrrUtdnniCEXAMPLE/IvU1dYUg2RVAJBanLiHb4IgRmpRV3z
             rkuWJOgQs8IZZaIv2BXIa2R4OlgkBN9bkUDNCJiBeb/AXlzBBko7b15fjrBs2+cTQtp
             Z3CYWFXG8C5zqx37wnOE49mRl/+OtkIKGO7fAE
            </SessionToken>
            <SecretAccessKey>
            wJalrXUtnFEMI/K7MDENG/bPxRfiCYzEXAMPLEKEY
            </SecretAccessKey>
            <Expiration>2011-07-11T19:55:29.611Z</Expiration>
            <AccessKeyId>ASIAIOSFODNN7EXAMPLE</AccessKeyId>
          </Credentials>
        </GetSessionTokenResult>
        <ResponseMetadata>
          <RequestId>58c5dbae-abef-11e0-8cfe-09039844ac7d</RequestId>
        </ResponseMetadata>
      </GetSessionTokenResponse>
    """.stripMargin

  def getAccessToken: Route = logRequestResult("debug") {
    get {
      pathSingleSlash {
        parameter("Action") {
          case action if "AssumeRoleWithWebIdentity".equals(action) => complete(HttpEntity(contentType = ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), testAssumeRoleWithWebIdentityResponse))
          case action if "GetSessionToken".equals(action) => complete(HttpEntity(contentType = ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), testGetSessionTokenResponse))
          case _ => complete(StatusCodes.BadRequest)
        }
      }
    }
  }

}
