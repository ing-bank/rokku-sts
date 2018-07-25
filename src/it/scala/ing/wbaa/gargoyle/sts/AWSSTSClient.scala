package ing.wbaa.gargoyle.sts

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.services.securitytoken.{AWSSecurityTokenService, AWSSecurityTokenServiceClientBuilder}


trait AWSSTSClient {

  private val server = new CoreActorSystem
    with Routes
    with Actors
    with Web

  def stsClient(): AWSSecurityTokenService = AWSSecurityTokenServiceClientBuilder
    .standard()
    .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("accesskey", "secretkey")))
    .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://localhost:12345/", Regions.DEFAULT_REGION.getName))
    .build()


  def stopWebServer(): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    server.webServer.flatMap(_.unbind())
      .onComplete { _ =>
        server.materializer.shutdown()
        server.system.terminate()
      }
  }
}
