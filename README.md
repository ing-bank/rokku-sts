[![Build Status](https://travis-ci.org/ing-bank/rokku-sts.svg?branch=master)](https://travis-ci.org/ing-bank/rokku-sts)
[![codecov](https://codecov.io/gh/ing-bank/rokku-sts/branch/master/graph/badge.svg)](https://codecov.io/gh/ing-bank/rokku-sts)
[![](https://images.microbadger.com/badges/image/wbaa/rokku-sts:latest.svg)](https://hub.docker.com/r/wbaa/rokku-sts/tags/)

# Rokku STS

STS stands for Short Token Service. The Rokku STS performs operations that are specific to managing service tokens.
For a higher level view of purpose of the Rokku STS service, please view the [Rokku](https://github.com/ing-bank/rokku) project.

The Rokku STS simulates the following STS actions:
 * [GetSessionToken](https://docs.aws.amazon.com/STS/latest/APIReference/API_GetSessionToken.html)
 * [AssumeRole](https://docs.aws.amazon.com/STS/latest/APIReference/API_AssumeRole.html)

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
     "userGroups": "testGroup",
     "accessKey": "userAccessKey",
     "secretKey": "userSercretKey",
     "userRole": "userRole"
     }
   ```


## Quickstart
#### What Do You Need

To get a quickstart on running the Rokku STS, you'll need the following:
* Docker
* SBT

1. Launch the Docker images which contain the dependencies for Rokku STS:

        docker-compose up --build --force-recreate

2. When the docker services are up and running, you can start the Rokku STS:

        sbt run

3. Have fun requesting tokens

## Architecture

[MVP1](docs/mvp1-flow.md)

#### Dependencies
The STS service is dependant on two services:

* [Keycloak](https://www.keycloak.org/) for MFA authentication of users.
* [Redis] A persistence store to maintain the user and session tokens issued


## Test (mock version)

`docker run -p 12345:12345 wbaa/rokku-sts:latest`

to get the credential you need to provide a valid token in on of the places:
* header `Authorization Bearer valid`
* cookie `X-Authorization-Token: valid`
* parameter or form `WebIdentityToken=valid`

### ```http://localhost:12345?Action=GetSessionToken```

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

### ```http://localhost:12345?Action=AssumeRole&RoleArn=arn:aws:iam::account-id:role/admin&RoleSessionName=test```

returns:

```xml
<AssumeRoleResponse>
      <AssumeRoleResult>
        <AssumedRoleUser>
            <Arn>arn:aws:iam::account-id:role/admin/test</Arn>
            <AssumedRoleId>id:test</AssumedRoleId>
        </AssumedRoleUser>
        <Credentials>
            <SessionToken>okSessionToken</SessionToken>
            <SecretAccessKey>secretKey</SecretAccessKey>
            <Expiration>2019-10-07T20:08:57.450Z</Expiration>
            <AccessKeyId>okAccessKey</AccessKeyId>
        </Credentials>
      </AssumeRoleResult>
      <ResponseMetadata>
        <RequestId>4265be0e-6246-4e3a-af72-b1a7cc997a94</RequestId>
      </ResponseMetadata>
</AssumeRoleResponse>
```
_the [dev keycloak docker](https://github.com/ing-bank/rokku-dev-keycloak) has a `userone` who has the admin role._


### ```http://localhost:12345/isCredentialActive?accessKey=okAccessKey&sessionToken=okSessionToken```
returns status OK or Forbidden

NOTE: since EP is protected with token, you may need to add header with token to access isCredentialsActive endpoint

```
Default token that should match settings from test reference.conf file

-H "Authorization: eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzZXJ2aWNlIjoicm9ra3UiLCJpc3MiOiJyb2trdSJ9.aCpyvC53lWdF_IOdZQp0fO8W4tH_LeK3vQcIvt5W1-0"
```

### aws cli

```bash
aws sts get-session-token  --endpoint-url http://localhost:12345 --region localhost --token-code validToken
```

```bash
aws sts assume-role --role-arn arn:aws:iam::account-id:role/admin --role-session-name testrole --endpoint-url http://localhost:12345 --token-code validToken
```

## NPA users

STS allows users with the `KEYCLOAK_NPA_ROLE` to be registered as NPAs. Only these users will have access to the `/npa/*` endpoints.
When a user is registered as an NPA it can authenticate itself without the need of a session token from keycloak.
If the user already exists and aws credentials are issued for him then this operation will not be allowed and the request will
return a 409 Conflict.

### Registering user as an npa
```
 curl -X POST "127.0.0.1:12345/npa/registry" -H "Authorization: Bearer $keycloak_token_session"
```
The response of the above request will be of the form 
```json
{
    "accessKey": "the-npa-access-key",
    "secretKey": "the-npa-secret-key"
}
```

### Getting NPAs credentials
```
 curl -X GET "127.0.0.1:12345/npa/credentials" -H "Authorization: Bearer $keycloak_token_session"
```
The response of the above request will be of the form 
```json
{
    "accessKey": "the-npa-access-key",
    "secretKey": "the-npa-secret-key"
}
```


### Old deprecated admin API for NPA users

STS allows NPA (non personal account) access, in cases where client is not able to authenticate
with Keycloak server.
In order to notify STS that user is NPA user, below steps needs to be done:

1. User needs to be in administrator groups (user groups are taken from Keycloak)

2. Check settings of the value `STS_ADMIN_GROUPS` in application.conf and set groups accordingly. Config accepts
coma separated string: "testgroup, othergroup"

3. A safe needs to exists with the correct name in vault, otherwise secrets will not be written to vault (404 in logs is an indication of that)

4. Use postman or other tool of choice to send x-www-form-urlencoded values:

```
npaAccount = value
safeName = vaule
awsAccessKey = value
awsSecretKey = value
```

as POST:

```
curl -X POST \
     -d "npaAccount=${NPA_ACCOUNT}&safeName=${SAFE_NAME}&awsAccessKey=${NPA_ACCESS_KEY}&awsSecretKey=${NPA_SECRET_KEY}" \
     -H "Authorization: Bearer ${KEYCLOAK_TOKEN}" \
     http://127.0.0.1:12345/admin/npa
```

NPA user access key and account names must be unique, otherwise adding NPA will fail.

User must also:
- be allowed in Ranger Sever policies to access Ceph S3 resources

When accessing Rokku with aws cli or sdk, just export `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`
with NO `AWS_SESSION_TOKEN`


## Enable or disable user account

STS user account details are taken from Keycloak, but additionally one can mark user account as disabled in Rokku-STS
by running:
```
Enable:
curl -H "Authorization: Bearer ${KEYCLOAK_TOKEN}" -X PUT http://localhost:12345/admin/account/{USER_NAME}/enable

Disable:
curl -H "Authorization: Bearer ${KEYCLOAK_TOKEN}" -X PUT http://localhost:12345/admin/account/{USER_NAME}/disable
```

User needs to be in administrator groups (user groups are taken from Keycloak). Check settings of the value `STS_ADMIN_GROUPS` in application.conf and set groups accordingly.

## Production settings

If you plan to run rokku-sts in non-dev mode, make sure you at least set ENV value or edit application.conf

```
STS_MASTER_KEY = "radomKeyString"
```
