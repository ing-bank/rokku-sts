[![Build Status](https://travis-ci.org/ing-bank/rokku-sts.svg?branch=master)](https://travis-ci.org/ing-bank/rokku-sts)
[![codecov.io](http://codecov.io/github/ing-bank/rokku-sts/coverage.svg?branch=master)](https://codecov.io/gh/ing-bank/rokku-sts?branch=master)
[![](https://images.microbadger.com/badges/image/wbaa/rokku-sts:latest.svg)](https://hub.docker.com/r/wbaa/rokku-sts/tags/)

# Rokku STS

STS stands for Short Token Service. The Rokku STS performs operations that are specific to managing service tokens. 
For a higher level view of purpose of the Rokku STS service, please view the [Rokku](https://github.com/ing-bank/rokku) project.

The Rokku STS simulates the following STS action:
 * [GetSessionToken](https://docs.aws.amazon.com/STS/latest/APIReference/API_GetSessionToken.html)
 
This is the internal endpoint that is exposed:


 * **Checks if a user credentials are active**
 
        /isCredentialActive?accessKey=userAccessKey&sessionToken=userSessionToken
   
   Response status:
   
   * _FORBIDDEN_
   * _OK_
      
       * With the following body response (for status OK) :
   ```json
     {
     "userName": "testuser",
     "userAssumedGroup": "testGroup",
     "accessKey": "userAccessKey",
     "secretKey": "userSercretKey"
     }
   ```
 
   
## Quickstart
#### What Do You Need

To get a quickstart on running the Rokku STS, you'll need the following:
* Docker
* SBT

1. Launch the Docker images which contain the dependencies for Rokku STS:

        docker-compose up
        
2. When the docker services are up and running, you can start the Rokku STS:

        sbt run
     
3. Have fun requesting tokens
 
## Architecture

[MVP1](docs/mvp1-flow.md)

#### Dependencies
The STS service is dependant on two services:

* [Keycloak](https://www.keycloak.org/) for MFA authentication of users.
* A persistence store to maintain the user and session tokens issued, in the current infrastructure that is [MariaDB](https://mariadb.org).

For the persistence, Rokku STS does not autogenerate the tables required. So if you launch your own MariaDB database, 
you will need to create the tables as well. You can find the script to create the database, and the related tables 
[here](https://github.com/ing-bank/rokku-dev-mariadb/blob/master/database/rokkudb.sql).

 
## Test (mock version)

`docker run -p 12345:12345 wbaa/rokku-sts:latest`

to get the credential you need to provide a valid token in on of the places:
* header `Authorization Bearer valid`
* cookie `X-Authorization-Token: valid`
* parameter or form `WebIdentityToken=valid`

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

NOTE: since EP is protected with token, you may need to add header with token to access isCredentialsActive endpoint

```
Default token that should match settings from test reference.conf file

-H "Authorization: eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzZXJ2aWNlIjoicm9ra3UiLCJpc3MiOiJyb2trdSJ9.aCpyvC53lWdF_IOdZQp0fO8W4tH_LeK3vQcIvt5W1-0"
```

### aws cli

```text
aws sts get-session-token  --endpoint-url http://localhost:12345 --region localhost --token-code validToken
```

### NPA S3 users 

STS allows NPA (non personal account) access, in cases where client is not able to authenticate
with Keycloak server. 
In order to notify STS that user is NPA user, below steps needs to be done:

1. User needs to be in administrator groups (user groups are taken from Keycloak)

2. Check settings of the value `STS_ADMIN_GROUPS` in application.conf and set groups accordingly. Config accepts 
coma separated string: "testgroup, othergroup"

3. Use postman or other tool of choice to send x-www-form-urlencoded values:

```
npaAccount = value
awsAccessKey = value
awsSecretKey = value
```

as POST:

```
curl -X POST \
     -d "npaAccount=${NPA_ACCOUNT}&awsAccessKey=${NPA_ACCESS_KEY}&awsSecretKey=${NPA_SECRET_KEY}" \
     -H "Authorization: Bearer ${KEYCLOAK_TOKEN}" \
     http://127.0.0.1:12345/admin/npa
```

NPA user access key and account names must be unique, otherwise adding NPA will fail.

User must also:
- be allowed in Ranger Sever policies to access Ceph S3 resources 

When accessing Rokku with aws cli or sdk, just export `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`
with NO `AWS_SESSION_TOKEN`


### Enable or disable user account

STS user account details are taken from Keycloak, but additionally one can mark user account as disabled in Rokku-STS
by running:
```
Enable:
curl -H "Authorization: Bearer ${KEYCLOAK_TOKEN}" -X PUT http://localhost:12345/admin/account/{USER_NAME}/enable 

Disable:
curl -H "Authorization: Bearer ${KEYCLOAK_TOKEN}" -X PUT http://localhost:12345/admin/account/{USER_NAME}/disable
```

User needs to be in administrator groups (user groups are taken from Keycloak). Check settings of the value `STS_ADMIN_GROUPS` in application.conf and set groups accordingly.

### Production settings

If you plan to run rokku-sts in non-dev mode, make sure you at least set ENV value or edit application.conf

```
STS_MASTER_KEY = "radomKeyString"
``` 
