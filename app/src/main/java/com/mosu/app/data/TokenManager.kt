package com.mosu.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mosu.app.data.api.RetrofitClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

class TokenManager(private val context: Context) {

    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val TOKEN_EXPIRY_KEY = longPreferencesKey("token_expiry")
    }

    val accessToken: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[ACCESS_TOKEN_KEY]
        }

    val refreshToken: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[REFRESH_TOKEN_KEY]
        }

    val tokenExpiry: Flow<Long?> = context.dataStore.data
        .map { preferences ->
            preferences[TOKEN_EXPIRY_KEY]
        }

    suspend fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long) {
        val expiryTime = System.currentTimeMillis() + (expiresIn * 1000) // Convert to milliseconds
        context.dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = accessToken
            preferences[REFRESH_TOKEN_KEY] = refreshToken
            preferences[TOKEN_EXPIRY_KEY] = expiryTime
        }
    }

    suspend fun updateAccessToken(accessToken: String, expiresIn: Long) {
        val expiryTime = System.currentTimeMillis() + (expiresIn * 1000)
        context.dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = accessToken
            preferences[TOKEN_EXPIRY_KEY] = expiryTime
        }
    }

    suspend fun saveToken(token: String) {
        // Legacy method for backward compatibility
        context.dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = token
        }
    }

    suspend fun clearToken() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    fun isTokenExpired(): Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            val expiry = preferences[TOKEN_EXPIRY_KEY]
            expiry != null && System.currentTimeMillis() >= expiry
        }

    /**
     * Refreshes the access token using the refresh token
     * Returns true if refresh was successful, false otherwise
     */
    suspend fun refreshAccessToken(clientId: String, clientSecret: String): Boolean {
        val refreshToken = refreshToken.first()
        if (refreshToken.isNullOrEmpty()) {
            return false
        }

        return try {
            // Create refresh token request
            val requestBody = FormBody.Builder()
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build()

            val request = Request.Builder()
                .url("https://osu.ppy.sh/oauth/token")
                .post(requestBody)
                .build()

            val response = RetrofitClient.okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val newAccessToken = json.getString("access_token")
                val newRefreshToken = json.optString("refresh_token", refreshToken) // Some providers don't return new refresh token
                val expiresIn = json.getLong("expires_in")

                // Save the new tokens
                saveTokens(newAccessToken, newRefreshToken, expiresIn)
                return true
            } else {
                // Refresh failed, clear tokens
                clearToken()
                return false
            }
        } catch (e: Exception) {
            // Refresh failed, clear tokens
            clearToken()
            return false
        }
    }
}

