package com.ing.wbaa.rokku.sts

import akka.http.scaladsl.model.Uri.Authority
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder

trait AWSSTSClient {

  def stsClient(authority: Authority): AWSSecurityTokenService = AWSSecurityTokenServiceClientBuilder
    .standard()
    .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("accesskey", "secretkey")))
    .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(s"http://${authority.host.address()}:${authority.port}/", Regions.DEFAULT_REGION.getName))
    .build()

}
