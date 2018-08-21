[![Build Status](https://travis-ci.org/kr7ysztof/gargoyle-sts.svg?branch=master)](https://travis-ci.org/kr7ysztof/gargoyle-sts)
[![codecov.io](http://codecov.io/github/kr7ysztof/gargoyle-sts/coverage.svg?branch=master)](https://codecov.io/gh/kr7ysztof/gargoyle-sts?branch=master)
[![](https://images.microbadger.com/badges/image/kr7ysztof/gargoyle-sts:master.svg)](https://microbadger.com/images/kr7ysztof/gargoyle-sts:master)
[![](https://images.microbadger.com/badges/version/kr7ysztof/gargoyle-sts:master.svg)](https://microbadger.com/images/kr7ysztof/gargoyle-sts:master)

# Gargoyle STS

STS service for [gargoyle-s3proxy](https://github.com/arempter/gargoyle-s3proxy) project.

It simulates two sts actions:
 * [AssumeRoleWithWebIdentity](https://docs.aws.amazon.com/STS/latest/APIReference/API_AssumeRoleWithWebIdentity.html)
 * [GetSessionToken](https://docs.aws.amazon.com/STS/latest/APIReference/API_GetSessionToken.html)
 
and has two internals endpoints:
 * /isCredentialActive?accessKey=userAccessKey&sessionToken=userSessionToken - _checks in the user credentials are active_
   
   Response status:
    * **OK**
    * **FORBIDDEN**
   
 * /userInfo?accessKey=userAccessKey - _return a user information_
 
   Response:
   * Status **OK**
```json
  {
    "userId": "testuser",
    "groups": [
        "testgroup",
        "groupTwo"
    ]
  }
```
   * Status **NOTFOUND**
 
## Test (mock version)

`docker run -p 12345:12345 kr7ysztof/gargoyle-sts:master`

to get the credential you need to provide a valid token in on of the places:
* header `Authorization Bearer valid`
* cookie `X-Authorization-Token: valid`
* parameter or form `WebIdentityToken=valid`

```http://localhost:12345?Action=AssumeRoleWithWebIdentity&DurationSeconds=3600&ProviderId=testRrovider.com&RoleSessionName=app1&RoleArn=arn:aws:iam::123456789012:role/FederatedWebIdentityRole&WebIdentityToken=valid```

returns:

```xml
<AssumeRoleWithWebIdentityResponse>
      <AssumeRoleWithWebIdentityResult>
          <SubjectFromWebIdentityToken>amzn1.account.AF6RHO7KZU5XRVQJGXK6HB56KR2A</SubjectFromWebIdentityToken>
          <Audience>client.5498841531868486423.1548@apps.example.com</Audience>
          <AssumedRoleUser>
              <Arn>arn:aws:sts::123456789012:assumed-role/FederatedWebIdentityRole/app1</Arn>
              <AssumedRoleId>AROACLKWSDQRAOEXAMPLE:app1</AssumedRoleId>
          </AssumedRoleUser>
          <Credentials>
              <SessionToken>okSessionToken</SessionToken>
              <SecretAccessKey>secretKey</SecretAccessKey>
              <Expiration>2019-10-24T23:00:23Z</Expiration>
              <AccessKeyId>okAccessKey</AccessKeyId>
          </Credentials>
          <Provider>www.amazon.com</Provider>
      </AssumeRoleWithWebIdentityResult>
      <ResponseMetadata>
          <RequestId>ad4156e9-bce1-11e2-82e6-6b6efEXAMPLE</RequestId>
      </ResponseMetadata>
  </AssumeRoleWithWebIdentityResponse>
```

```http://localhost:12345?Action=GetSessionToken```

returns:

```xml
<GetSessionTokenResponse>
    <GetSessionTokenResult>
        <Credentials>
            <SessionToken>
             okSessionToken
            </SessionToken>
            <SecretAccessKey>
             secretKey
            </SecretAccessKey>
            <Expiration>2019-07-11T19:55:29.611Z</Expiration>
            <AccessKeyId>okAccessKey</AccessKeyId>
        </Credentials>
    </GetSessionTokenResult>
    <ResponseMetadata>
        <RequestId>58c5dbae-abef-11e0-8cfe-09039844ac7d</RequestId>
    </ResponseMetadata>
</GetSessionTokenResponse>
```

```http://localhost:12345/isCredentialActive?accessKey=okAccessKey&sessionToken=okSessionToken```
returns status OK or Forbidden

```http://localhost:12345/userInfo?accessKey=okAccessKey```
returns returns status OK or NotFound

### aws cli

```text
aws sts get-session-token  --endpoint-url http://localhost:12345 --region localhost --token-code validToken

aws sts assume-role-with-web-identity --role-arn arn:test:resource:name --role-session-name testsession --web-identity-token validToken --endpoint-url http://localhost:12345
```
