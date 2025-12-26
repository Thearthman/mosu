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
     * Makes an authenticated API call with automatic token refresh
     */
    private suspend fun <T> makeAuthenticatedCall(
        apiCall: suspend (authHeader: String) -> T
    ): T {
        val token = tokenManager.accessToken.first()
        if (token.isNullOrEmpty()) {
            throw IllegalStateException("No access token available")
        }

        val authHeader = "Bearer $token"

        return try {
            apiCall(authHeader)
        } catch (e: Exception) {
            // Check if it's an authentication error (401)
            if (isAuthError(e)) {
                // Try to refresh token
                val clientId = settingsManager.clientId.first()
                val clientSecret = settingsManager.clientSecret.first()

                if (!clientId.isNullOrEmpty() && !clientSecret.isNullOrEmpty()) {
                    val refreshSuccess = tokenManager.refreshAccessToken(clientId, clientSecret)

                    if (refreshSuccess) {
                        // Retry with new token
                        val newToken = tokenManager.accessToken.first()
                        if (!newToken.isNullOrEmpty()) {
                            val newAuthHeader = "Bearer $newToken"
                            return apiCall(newAuthHeader)
                        }
                    }
                }
            }
            // Re-throw if refresh failed or wasn't an auth error
            throw e
        }
    }

    /**
     * Checks if the exception is an authentication error
     */
    private fun isAuthError(e: Exception): Boolean {
        // This is a simplified check - in a real app you'd check HTTP status codes
        // For now, we'll assume any exception might be auth-related and try refresh
        return true
    }
}

/**
 * Interceptor for adding auth headers (can be used with OkHttp if needed)
 */
class AuthInterceptor(
    private val tokenManager: TokenManager,
    private val settingsManager: SettingsManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Add auth header if we have a token
        val token = runBlocking { tokenManager.accessToken.first() }
        val authenticatedRequest = if (!token.isNullOrEmpty()) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(authenticatedRequest)

        // If we get a 401, try to refresh token
        if (response.code == 401) {
            runBlocking {
                val clientId = settingsManager.clientId.first()
                val clientSecret = settingsManager.clientSecret.first()

                if (!clientId.isNullOrEmpty() && !clientSecret.isNullOrEmpty()) {
                    tokenManager.refreshAccessToken(clientId, clientSecret)
                }
            }
        }

        return response
    }
}
