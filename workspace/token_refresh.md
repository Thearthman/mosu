<img src="https://r2cdn.perplexity.ai/pplx-full-logo-primary-dark%402x.png" style="height:64px;margin-right:32px"/>

# [https://github.com/Thearthman/mosu](https://github.com/Thearthman/mosu) I'm trying to implement osu token control for this music app I wrote. Current I have a problem relating to the token expiring after a period of time which logs the user out of the app and requires going to the auth2 callback link to manually refresh the token. Is there a way I can automatically refresh the token?

Based on the `mosu` repository you shared, it looks like you are using the `osu!api v2`, which follows standard OAuth 2.0 protocols. The issue of tokens expiring and forcing a re-login is a common challenge when handling `access_token` expiration without utilizing the `refresh_token`.

Here is how you can implement automatic token refreshing in your Android app to keep the user logged in without manual intervention.

### 1. The OAuth 2.0 Flow for Osu!

When you initially authenticate the user via the `authorization_code` grant, the osu! API returns a JSON response containing three key pieces of information:

1. **`access_token`**: Used to make API requests (short-lived, usually expires in 24 hours).
2. **`refresh_token`**: Used to get a new access token without user interaction (long-lived).
3. **`expires_in`**: The duration in seconds until the access token expires.

**The Problem:** Currently, you are likely only storing or using the `access_token`. When it expires, the API returns a `401 Unauthorized` error, and since you don't have a mechanism to renew it, the user is forced to log in again.

### 2. Implementation Strategy

You need to modify your `TokenManager` or `AuthRepository` to handle the `refresh_token`.

#### Step A: Store the Refresh Token

When the user first logs in and you receive the initial token response, save the `refresh_token` securely (e.g., in Jetpack DataStore, which you are already using) alongside the `access_token`.

#### Step B: Create a Token Refresh Function

You need a function that calls the `oauth/token` endpoint using the `refresh_token` grant type.

**Request Payload:**

```json
{
    "client_id": "YOUR_CLIENT_ID",
    "client_secret": "YOUR_CLIENT_SECRET",
    "grant_type": "refresh_token",
    "refresh_token": "THE_STORED_REFRESH_TOKEN"
}
```

**Response:**
You will get a *new* `access_token` and a *new* `refresh_token`. **Crucial:** You must replace the old refresh token with the new one in your storage, as the old one is invalidated immediately upon use (token rotation).

#### Step C: Intercepting 401 Errors (OkHttp Authenticator)

Since you are likely using Retrofit/OkHttp, the cleanest way to handle this is by implementing a custom `Authenticator`. This allows OkHttp to automatically retry a failed request after refreshing the token.

Here is a conceptual implementation in Kotlin:

```kotlin
class OsuAuthenticator(
    private val tokenStore: TokenStore, // Your DataStore wrapper
    private val apiService: OsuApiService // A separate service instance for auth calls to avoid circular deps
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // 1. Check if we already tried to refresh to prevent infinite loops
        if (response.responseCount > 1) {
            return null // Give up, let the user log in again
        }

        // 2. Get the stored refresh token strictly synchronously
        val refreshToken = runBlocking { tokenStore.getRefreshToken() } ?: return null

        // 3. specific call to refresh the token
        val newTokens = runBlocking {
            try {
                // Call the osu! API endpoint manually here
                apiService.refreshToken(
                    grantType = "refresh_token",
                    clientId = CLIENT_ID,
                    clientSecret = CLIENT_SECRET,
                    refreshToken = refreshToken
                )
            } catch (e: Exception) {
                null
            }
        }

        // 4. If refresh failed (e.g., refresh token explicitly revoked), return null
        if (newTokens == null) {
            return null
        }

        // 5. Save the NEW tokens
        runBlocking {
            tokenStore.saveTokens(newTokens.accessToken, newTokens.refreshToken)
        }

        // 6. Retry the original request with the new access token
        return response.request.newBuilder()
            .header("Authorization", "Bearer ${newTokens.accessToken}")
            .build()
    }
}
```


### 3. Wiring it up

Add this authenticator to your OkHttp client builder for your main API instance:

```kotlin
val client = OkHttpClient.Builder()
    .authenticator(OsuAuthenticator(tokenStore, authApi))
    .addInterceptor(AuthInterceptor(tokenStore)) // Your existing interceptor that adds the header
    .build()
```


### Summary of Changes Required

1. **Database/DataStore:** Update your schema to store `refresh_token` strings.
2. **API Interface:** Add a Retrofit method for `POST /oauth/token` that accepts `grant_type="refresh_token"`.
3. **Network Logic:** Implement the OkHttp `Authenticator` to detect 401s, swap the tokens, and retry the request transparently.

This ensures that as long as the user uses the app occasionally, they will never be logged out. If they don't use the app for a very long time (and the refresh token itself expires), only then will they need to log in manually.
<span style="display:none">[^1]</span>

<div align="center">⁂</div>

[^1]: https://github.com/Thearthman/mosu

