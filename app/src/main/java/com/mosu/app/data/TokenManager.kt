package com.mosu.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mosu.app.data.api.OsuApi
import com.mosu.app.data.api.RetrofitClient
import com.mosu.app.data.model.Account
import kotlinx.coroutines.flow.Flow
import retrofit2.Retrofit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

class TokenManager(private val context: Context) {

    companion object {
        private val CURRENT_ACCOUNT_KEY = stringPreferencesKey("current_account_id")
        private val ACCOUNTS_KEY = stringPreferencesKey("accounts_json")

        // Account-specific keys
        private fun accessTokenKey(accountId: String) = stringPreferencesKey("access_token_$accountId")
        private fun refreshTokenKey(accountId: String) = stringPreferencesKey("refresh_token_$accountId")
        private fun tokenExpiryKey(accountId: String) = longPreferencesKey("token_expiry_$accountId")
        private fun clientIdKey(accountId: String) = stringPreferencesKey("client_id_$accountId")
        private fun clientSecretKey(accountId: String) = stringPreferencesKey("client_secret_$accountId")

        // Legacy keys for backward compatibility
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val TOKEN_EXPIRY_KEY = longPreferencesKey("token_expiry")
    }

    // Current account ID for backward compatibility
    val currentAccountId: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[CURRENT_ACCOUNT_KEY] ?: "main" // Default to "main" for backward compatibility
        }

    // Get current account ID synchronously
    suspend fun getCurrentAccountId(): String {
        return context.dataStore.data.first()[CURRENT_ACCOUNT_KEY] ?: "main"
    }

    // Set current account
    suspend fun setCurrentAccount(accountId: String) {
        context.dataStore.edit { preferences ->
            preferences[CURRENT_ACCOUNT_KEY] = accountId
        }
    }

    // Current account's access token
    val accessToken: Flow<String?> = context.dataStore.data
        .map { preferences ->
            val accountId = preferences[CURRENT_ACCOUNT_KEY] ?: "main"
            preferences[accessTokenKey(accountId)]
        }

    // Synchronous access to current token for interceptors
    suspend fun getCurrentAccessToken(): String? {
        val accountId = getCurrentAccountId()
        return context.dataStore.data.first()[accessTokenKey(accountId)]
    }

    val refreshToken: Flow<String?> = context.dataStore.data
        .map { preferences ->
            val accountId = preferences[CURRENT_ACCOUNT_KEY] ?: "main"
            preferences[refreshTokenKey(accountId)]
        }

    val tokenExpiry: Flow<Long?> = context.dataStore.data
        .map { preferences ->
            val accountId = preferences[CURRENT_ACCOUNT_KEY] ?: "main"
            preferences[tokenExpiryKey(accountId)]
        }

    suspend fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long) {
        val accountId = getCurrentAccountId()
        val expiryTime = System.currentTimeMillis() + (expiresIn * 1000) // Convert to milliseconds
        context.dataStore.edit { preferences ->
            preferences[accessTokenKey(accountId)] = accessToken
            preferences[refreshTokenKey(accountId)] = refreshToken
            preferences[tokenExpiryKey(accountId)] = expiryTime
        }
    }

    suspend fun updateAccessToken(accessToken: String, expiresIn: Long) {
        val accountId = getCurrentAccountId()
        val expiryTime = System.currentTimeMillis() + (expiresIn * 1000)
        context.dataStore.edit { preferences ->
            preferences[accessTokenKey(accountId)] = accessToken
            preferences[tokenExpiryKey(accountId)] = expiryTime
        }
    }

    suspend fun saveToken(token: String) {
        // Legacy method for backward compatibility
        val accountId = getCurrentAccountId()
        context.dataStore.edit { preferences ->
            preferences[accessTokenKey(accountId)] = token
        }
    }

    suspend fun clearCurrentAccountToken() {
        val accountId = getCurrentAccountId()
        context.dataStore.edit { preferences ->
            preferences.remove(accessTokenKey(accountId))
            preferences.remove(refreshTokenKey(accountId))
            preferences.remove(tokenExpiryKey(accountId))
        }
    }

    suspend fun clearAllTokens() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    // Account management methods
    suspend fun saveAccountCredentials(accountId: String, clientId: String, clientSecret: String) {
        context.dataStore.edit { preferences ->
            preferences[clientIdKey(accountId)] = clientId
            preferences[clientSecretKey(accountId)] = clientSecret
        }
    }

    suspend fun getAccountCredentials(accountId: String): Pair<String?, String?> {
        val preferences = context.dataStore.data.first()
        return Pair(
            preferences[clientIdKey(accountId)],
            preferences[clientSecretKey(accountId)]
        )
    }

    suspend fun deleteAccount(accountId: String) {
        context.dataStore.edit { preferences ->
            preferences.remove(accessTokenKey(accountId))
            preferences.remove(refreshTokenKey(accountId))
            preferences.remove(tokenExpiryKey(accountId))
            preferences.remove(clientIdKey(accountId))
            preferences.remove(clientSecretKey(accountId))

            // If this was the current account, switch to main
            if (preferences[CURRENT_ACCOUNT_KEY] == accountId) {
                preferences[CURRENT_ACCOUNT_KEY] = "main"
            }
        }
    }

    fun isTokenExpired(): Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            val accountId = preferences[CURRENT_ACCOUNT_KEY] ?: "main"
            val expiry = preferences[tokenExpiryKey(accountId)]
            expiry != null && System.currentTimeMillis() >= expiry
        }

    /**
     * Proactively refreshes the token if it's close to expiry (within 5 minutes)
     * Returns true if refresh was attempted and successful, false otherwise
     */
    suspend fun refreshTokenIfNeeded(clientId: String, clientSecret: String): Boolean {
        val accountId = getCurrentAccountId()
        val expiry = context.dataStore.data.first()[tokenExpiryKey(accountId)]
        if (expiry == null) return false

        // Refresh if token expires within 5 minutes (300,000 milliseconds)
        val fiveMinutesFromNow = System.currentTimeMillis() + 300_000
        if (expiry <= fiveMinutesFromNow) {
            return refreshAccessToken(clientId, clientSecret)
        }
        return true // Token is still valid
    }

    /**
     * Refreshes the access token using the refresh token
     * Returns true if refresh was successful, false otherwise
     */
    suspend fun refreshAccessToken(clientId: String, clientSecret: String): Boolean {
        val accountId = getCurrentAccountId()
        val refreshToken = context.dataStore.data.first()[refreshTokenKey(accountId)]
        if (refreshToken.isNullOrEmpty()) {
            return false
        }

        return try {
            // Use Retrofit API for token refresh (avoid circular dependency with authenticated client)
            val baseClient = RetrofitClient.getBaseClient()
            val retrofit = Retrofit.Builder()
                .baseUrl("https://osu.ppy.sh/")
                .client(baseClient)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build()
                .create(OsuApi::class.java)

            val tokenResponse = retrofit.refreshToken(
                clientId = clientId,
                clientSecret = clientSecret,
                grantType = "refresh_token",
                refreshToken = refreshToken
            )

            // Save the new tokens (refresh token rotation - always replace with new one)
            saveTokens(tokenResponse.accessToken, tokenResponse.refreshToken ?: refreshToken, tokenResponse.expiresIn)
            return true
        } catch (e: Exception) {
            // Refresh failed, clear tokens for current account
            clearCurrentAccountToken()
            return false
        }
    }

    /**
     * Get all available account IDs
     */
    suspend fun getAvailableAccountIds(): List<String> {
        val preferences = context.dataStore.data.first()
        val accountIds = mutableSetOf<String>()

        // Check for accounts by looking for access token keys
        preferences.asMap().keys.forEach { key ->
            if (key.name.startsWith("access_token_")) {
                val accountId = key.name.removePrefix("access_token_")
                accountIds.add(accountId)
            }
        }

        // Always include main account if it has credentials
        if (preferences[clientIdKey("main")] != null || preferences[clientSecretKey("main")] != null) {
            accountIds.add("main")
        }

        return accountIds.toList()
    }
}

