OAuth2Provider wrapper around scala-oauth2-provider

# OAuth2Provider:

Light weighted OAuth2 provider written fully in Scala which wraps [scala-oauth2-provider](https://github.com/nulab/scala-oauth2-provider)

- [Play Framework](https://www.playframework.com/)
- [Akka Framework](https://github.com/nulab/akka-http-oauth2-provider)


### Run

```
sbt -Dplay.evolutions.db.default.autoApply=true run
```

### Debug

```
sbt run -jvm-debug 9000 -Dplay.evolutions.db.default.autoApply=true
```

## Generate access tokens with different [grant_type](https://oauth.net/2/grant-types/) using curl

### Authorization code

```
curl http://localhost:9000/oauth2/generate_access_token -X POST -d "client_id=alice_client_id" -d "client_secret=alice_client_secret" -d "redirect_uri=http://alice.com/redirectUrl" -d "code=alice_code" -d "grant_type=authorization_code" | jq
```
Pls note that `code=alice_code` has to be inserted into database which will be deleted once after access token in generated.

#### Sample response:
```
{
  "token_type": "Bearer",
  "access_token": "0RERQydZxb5rFlLXkbyl7IKh8FagQMuz822lZC5c",
  "expires_in": 3599,
  "refresh_token": "H566rUP9136m9Z74sLvbeALNStBSwvpGvxjVWTTs"
}
```


### Client credentials

```
curl http://localhost:9000/oauth2/generate_access_token -X POST -d "client_id=bob_client_id" -d "client_secret=bob_client_secret" -d "grant_type=client_credentials" | jq
```

#### Sample response:
```
{
  "token_type": "Bearer",
  "access_token": "xkxk63646JKti6FAuq0IMRDuHQQVDPe0JrMNj9Pm",
  "expires_in": 3599,
  "refresh_token": "RjlH2ybRPoVjkQMMyQIZmn9BCmOYm9ejVgZZMFfZ"
}
```

### Password

```
curl http://localhost:9000/oauth2/generate_access_token -X POST -d "client_id=alice_client_id2" -d "client_secret=alice_client_secret2" -d "username=alice@example.com" -d "password=alice" -d "grant_type=password" | jq
```

#### Sample response:
```
{
  "token_type": "Bearer",
  "access_token": "Gh7GQZ2iMJfZdAOQn4AnNKMRVSFADfEPEAGk32Uw",
  "expires_in": 3599,
  "refresh_token": "hq77HikYEHw5EO7pw3IZPAOu7JFMVpNTiSRZYodj"
}
```

### Refresh token

```
curl http://localhost:9000/oauth2/generate_access_token -X POST -d "client_id=alice_client_id2" -d "client_secret=alice_client_secret2" -d "refresh_token=${refresh_token}" -d "grant_type=refresh_token"
```

#### Sample request: 
``` 
curl http://localhost:9000/oauth2/generate_access_token -X POST -d "client_id=alice_client_id2" -d "client_secret=alice_client_secret2" -d "refresh_token=hq77HikYEHw5EO7pw3IZPAOu7JFMVpNTiSRZYodj" -d "grant_type=refresh_token" | jq
``` 
Pls note the refresh token if of Alice from above with `grant_type=password`

#### Sample response:
```
{
  "token_type": "Bearer",
  "access_token": "3EvvIVCLCQPdFxfDY4trGrzS2J05gCZnrVExFdXL",
  "expires_in": 3599,
  "refresh_token": "fnQyt1EFDU7XZhEaKZzG23UdGFkM7te7ckpnAP1K"
}
```

### Access resource using access_token

You can access application resource using access token.

```
curl -H "Authorization: Bearer ${access_token}" http://localhost:9000/resources | jq
```

#### Sample request: 
``` 
curl -H "Authorization: Bearer 0RERQydZxb5rFlLXkbyl7IKh8FagQMuz822lZC5c" http://localhost:9000/resources | jq
``` 
Pls note the access token of Alice from above with `grant_type=authorization_code`

#### Sample response:
```
{
  "accountInfo": {
    "emailId": "alice@example.com"
  },
  "oauthClientId": "alice_client_id",
  "redirectUri": "http://alice.com/redirectUrl"
}
```