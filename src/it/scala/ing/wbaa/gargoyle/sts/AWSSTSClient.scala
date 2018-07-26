package ing.wbaa.gargoyle.sts

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.services.securitytoken.{AWSSecurityTokenService, AWSSecurityTokenServiceClientBuilder}


trait AWSSTSClient {

  def stsClient(): AWSSecurityTokenService = AWSSecurityTokenServiceClientBuilder
    .standard()
    .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("accesskey", "secretkey")))
    .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://localhost:12345/", Regions.DEFAULT_REGION.getName))
    .build()

}
