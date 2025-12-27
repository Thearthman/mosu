package com.mosu.app.data.api

import android.content.Context
import com.mosu.app.data.SettingsManager
import com.mosu.app.data.TokenManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.json.JSONObject

/**
 * OkHttp Authenticator that automatically refreshes OAuth tokens when they expire
 */
class TokenAuthenticator(
    private val context: Context,
    private val tokenManager: TokenManager,
    private val settingsManager: SettingsManager
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Only retry once to avoid infinite loops
        if (response.request.header("Authorization")?.startsWith("Bearer ") == true) {
            // This request already had auth, don't retry
            return null
        }

        return runBlocking {
            try {
                // Try to refresh the token
                val clientId = settingsManager.clientId.first()
                val clientSecret = settingsManager.clientSecret.first()

                if (!clientId.isNullOrEmpty() && !clientSecret.isNullOrEmpty()) {
                    val refreshSuccess = tokenManager.refreshAccessToken(clientId, clientSecret)

                    if (refreshSuccess) {
                        // Retry with new token
                        val newToken = tokenManager.accessToken.first()
                        if (!newToken.isNullOrEmpty()) {
                            return@runBlocking response.request.newBuilder()
                                .header("Authorization", "Bearer $newToken")
                                .build()
                        }
                    }
                }
            } catch (e: Exception) {
                // Refresh failed
            }

            // Refresh failed, don't retry
            null
        }
    }
}
