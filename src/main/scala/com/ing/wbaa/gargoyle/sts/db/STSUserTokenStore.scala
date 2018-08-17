package com.ing.wbaa.gargoyle.sts.db

import com.ing.wbaa.gargoyle.sts.data.UserInfo
import com.ing.wbaa.gargoyle.sts.data.aws.AwsCredentialWithToken
import scala.concurrent.Future
import scala.concurrent.duration.Duration

trait STSUserTokenStore {

  //  private var userStore: mutable.Map[, ] = Map()

  def getAwsCredentialWithToken(userInfo: UserInfo, durationSeconds: Option[Duration]): Future[Option[AwsCredentialWithToken]] = ???

}
