package com.mosu.app.data.api

import com.mosu.app.data.SettingsManager
import com.mosu.app.data.TokenManager
import com.mosu.app.data.api.model.BeatmapPlaycount
import com.mosu.app.data.api.model.OsuUserCompact
import com.mosu.app.data.api.model.SearchResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Authenticated wrapper around OsuApi that automatically handles token refresh
 */
class AuthenticatedOsuApi(
    private val osuApi: OsuApi,
    private val tokenManager: TokenManager,
    private val settingsManager: SettingsManager
) {

    private val authInterceptor = AuthInterceptor(tokenManager, settingsManager)

    // Wrap all API calls with authentication
    suspend fun getMe(): OsuUserCompact {
        return makeAuthenticatedCall { authHeader ->
            osuApi.getMe(authHeader)
        }
    }

    suspend fun getUserMostPlayed(userId: String, limit: Int = 10, offset: Int = 0): List<BeatmapPlaycount> {
        return makeAuthenticatedCall { authHeader ->
            osuApi.getUserMostPlayed(authHeader, userId, limit, offset)
        }
    }

    suspend fun getUserRecentScores(userId: String, limit: Int = 50, includeFails: Int = 1, mode: String = "osu"): List<com.mosu.app.data.api.model.RecentScore> {
        return makeAuthenticatedCall { authHeader ->
            osuApi.getUserRecentScores(authHeader, userId, limit, includeFails, mode)
        }
    }

    suspend fun getUserFavoriteBeatmapsets(userId: String, limit: Int = 50, offset: Int = 0): List<com.mosu.app.data.api.model.BeatmapsetCompact> {
        return makeAuthenticatedCall { authHeader ->
            osuApi.getUserFavoriteBeatmapsets(authHeader, userId, limit, offset)
        }
    }

    suspend fun searchBeatmapsets(
        played: String? = null,
        query: String? = null,
        genre: Int? = null,
        status: String? = null,
        cursorString: String? = null
    ): SearchResponse {
        return makeAuthenticatedCall { authHeader ->
            osuApi.searchBeatmapsets(authHeader, played, query, genre, status, cursorString)
        }
    }

    suspend fun getBeatmapsetDetail(beatmapsetId: Long): com.mosu.app.data.api.model.BeatmapsetDetail {
        return makeAuthenticatedCall { authHeader ->
            osuApi.getBeatmapsetDetail(authHeader, beatmapsetId)
        }
    }

    /**
     * Makes an authenticated API call
     * Token refresh is handled automatically by OkHttp Authenticator
     */
    private suspend fun <T> makeAuthenticatedCall(
        apiCall: suspend (authHeader: String) -> T
    ): T {
        val token = tokenManager.accessToken.first()
        if (token.isNullOrEmpty()) {
            throw IllegalStateException("No access token available")
        }

        val authHeader = "Bearer $token"
        return apiCall(authHeader)
    }
}

/**
 * Interceptor for adding Authorization headers to requests
 */
class AuthInterceptor(
    private val tokenManager: TokenManager,
    private val settingsManager: SettingsManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Get current token synchronously
        val token = runBlocking { tokenManager.getCurrentAccessToken() }
        android.util.Log.d("AuthInterceptor", "Token from DataStore: ${token?.take(10)}...")
        if (token.isNullOrEmpty()) {
            // Log when token is missing for debugging
            android.util.Log.w("AuthInterceptor", "No access token available for request: ${originalRequest.url}")
            return chain.proceed(originalRequest)
        }

        // Proactively refresh token if needed
        val refreshNeeded = runBlocking {
            val isExpired = tokenManager.isTokenExpired().first() ?: false
            android.util.Log.d("AuthInterceptor", "Token expired check: $isExpired")

            if (isExpired) {
                val clientId = settingsManager.clientId.first()
                val clientSecret = settingsManager.clientSecret.first()
                android.util.Log.d("AuthInterceptor", "Client credentials available for refresh: ${clientId.isNotEmpty() && clientSecret.isNotEmpty()}")

                if (clientId.isNotEmpty() && clientSecret.isNotEmpty()) {
                    val refreshSuccess = tokenManager.refreshTokenIfNeeded(clientId, clientSecret)
                    android.util.Log.d("AuthInterceptor", "Token refresh result: $refreshSuccess")
                    refreshSuccess
                } else {
                    false
                }
            } else {
                true // Token is still valid
            }
        }

        // Get the potentially refreshed token
        val currentToken = if (refreshNeeded) {
            runBlocking { tokenManager.getCurrentAccessToken() }
        } else {
            // Token refresh failed or wasn't attempted, use original token
            token
        }

        if (currentToken.isNullOrEmpty()) {
            android.util.Log.w("AuthInterceptor", "No valid token available after refresh attempt")
            return chain.proceed(originalRequest)
        }
        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $currentToken")
            .build()

        return chain.proceed(authenticatedRequest)
    }
}
