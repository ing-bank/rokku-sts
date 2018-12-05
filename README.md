[![Build Status](https://travis-ci.org/ing-bank/airlock-sts.svg?branch=master)](https://travis-ci.org/ing-bank/airlock-sts)
[![codecov.io](http://codecov.io/github/ing-bank/airlock-sts/coverage.svg?branch=master)](https://codecov.io/gh/ing-bank/airlock-sts?branch=master)
[![](https://images.microbadger.com/badges/image/wbaa/airlock-sts:latest.svg)](https://hub.docker.com/r/wbaa/airlock-sts/tags/)

# Airlock STS

STS stands for Short Token Service. The Airlock STS performs operations that are specific to managing service tokens. 
For a higher level view of purpose of the Airlock STS service, please view the [Airlock](https://github.com/ing-bank/airlock) project.

The Airlock STS simulates two STS actions:
 * [AssumeRoleWithWebIdentity](https://docs.aws.amazon.com/STS/latest/APIReference/API_AssumeRoleWithWebIdentity.html)
 * [GetSessionToken](https://docs.aws.amazon.com/STS/latest/APIReference/API_GetSessionToken.html)
 
This is the internal endpoint that is exposed:


 * **Checks if a user credentials are active**
 
        /isCredentialActive?accessKey=userAccessKey&sessionToken=userSessionToken
   
   Response status:
   
   * _FORBIDDEN_
   * _OK_
      
       * With the following body respons(for status OK) :
   ```json
     {
     "userName": "testuser",
     "userAssumedGroup": ["testGroup1", "testGroup2"],
     "accessKey": "userAccessKey",
     "secretKey": "userSercretKey"
     }
   ```
 
   
## Quickstart
#### What Do You Need

To get a quickstart on running the Airlock STS, you'll need the following:
* Docker
* SBT

1. Launch the Docker images which contain the dependencies for Airlock STS:

        docker-compose up
        
2. When the docker services are up and running, you can start the Airlock STS:

        sbt run
     
3. Have fun requesting tokens
 
## Architecture

[MVP1](docs/mvp1-flow.md)

#### Dependencies
The STS service is dependant on two services:

* [Keycloak](https://www.keycloak.org/) for MFA authentication of users.
* A persistence store to maintain the user and session tokens issued, in the current infrastructure that is [MariaDB](https://mariadb.org).

For the persistence, Airlock STS does not autogenerate the tables required. So if you launch your own MariaDB database, 
you will need to create the tables as well. You can find the script to create the database, and the related tables 
[here](https://github.com/ing-bank/airlock-dev-mariadb/blob/master/database/airlockdb.sql).

 
## Test (mock version)

`docker run -p 12345:12345 nielsdenissen/airlock-sts:latest`

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

### aws cli

```text
aws sts get-session-token  --endpoint-url http://localhost:12345 --region localhost --token-code validToken

aws sts assume-role-with-web-identity --role-arn arn:test:resource:name --role-session-name testsession --web-identity-token validToken --endpoint-url http://localhost:12345
```

### NPA S3 users 

STS allows NPA (non personal account) access, in cases where client is not able to authenticate
with Keycloak server. 
In order to notify STS that user is NPA user, manual insert to db (users table) is needed.

Either create sql script, or run insert in STS database (mariadb)

```
insert into users values ('npa_user_name','accesskey','secretkey','1');
```

User must also:

- exist in Ceph S3 with above added access and secret keys
- be allowed in Ranger Sever policies to access Ceph S3 resources 

When accessing Airlock with aws cli or sdk, just export `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`
with NO `AWS_SESSION_TOKEN`
