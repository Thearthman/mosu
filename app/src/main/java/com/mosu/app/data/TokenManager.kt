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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import retrofit2.Retrofit

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

/**
 * Stores the OAuth session for the single signed-in osu! account.
 *
 * Mosu supports exactly one account at a time; the access/refresh tokens, their
 * expiry, and the OAuth client id/secret all live here under a fixed set of
 * keys. These are authentication material, so they belong to the auth store
 * rather than the preferences store.
 */
class TokenManager(private val context: Context) {

    /** The OAuth application credentials used to authorize/refresh the session. */
    data class OAuthCredentials(val clientId: String, val clientSecret: String) {
        val isConfigured: Boolean
            get() = clientId.isNotBlank() && clientSecret.isNotBlank()
    }

    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val TOKEN_EXPIRY_KEY = longPreferencesKey("token_expiry")
        internal val CLIENT_ID_KEY = stringPreferencesKey("client_id")
        internal val CLIENT_SECRET_KEY = stringPreferencesKey("client_secret")

        // Legacy multi-account keys, only read once to migrate an existing session.
        private val LEGACY_CURRENT_ACCOUNT_KEY = stringPreferencesKey("current_account_id")
        internal val LEGACY_PREFIXES = listOf(
            "access_token_",
            "refresh_token_",
            "token_expiry_",
            "client_id_",
            "client_secret_"
        )
        private fun legacyStringKey(prefix: String, accountId: String) =
            stringPreferencesKey("$prefix$accountId")
        private fun legacyExpiryKey(accountId: String) =
            longPreferencesKey("token_expiry_$accountId")
    }

    val accessToken: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[ACCESS_TOKEN_KEY] }

    val refreshToken: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[REFRESH_TOKEN_KEY] }

    val clientId: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[CLIENT_ID_KEY] ?: "" }

    val clientSecret: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[CLIENT_SECRET_KEY] ?: "" }

    // Synchronous access to the current token for interceptors.
    suspend fun getAccessToken(): String? {
        return context.dataStore.data.first()[ACCESS_TOKEN_KEY]
    }

    suspend fun getCredentials(): OAuthCredentials {
        val preferences = context.dataStore.data.first()
        return OAuthCredentials(
            clientId = preferences[CLIENT_ID_KEY] ?: "",
            clientSecret = preferences[CLIENT_SECRET_KEY] ?: ""
        )
    }

    suspend fun saveCredentials(clientId: String, clientSecret: String) {
        context.dataStore.edit { preferences ->
            preferences[CLIENT_ID_KEY] = clientId
            preferences[CLIENT_SECRET_KEY] = clientSecret
        }
    }

    suspend fun clearCredentials() {
        context.dataStore.edit { preferences ->
            preferences.remove(CLIENT_ID_KEY)
            preferences.remove(CLIENT_SECRET_KEY)
        }
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long) {
        val expiryTime = System.currentTimeMillis() + expiresIn * 1000
        context.dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = accessToken
            preferences[REFRESH_TOKEN_KEY] = refreshToken
            preferences[TOKEN_EXPIRY_KEY] = expiryTime
        }
    }

    fun isTokenExpired(): Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            val expiry = preferences[TOKEN_EXPIRY_KEY]
            expiry != null && System.currentTimeMillis() >= expiry
        }

    suspend fun clearToken() {
        context.dataStore.edit { preferences ->
            preferences.remove(ACCESS_TOKEN_KEY)
            preferences.remove(REFRESH_TOKEN_KEY)
            preferences.remove(TOKEN_EXPIRY_KEY)
        }
    }

    /**
     * Proactively refreshes the token if it's close to expiry (within 5 minutes)
     * Returns true if the token is (still) valid, false otherwise
     */
    suspend fun refreshTokenIfNeeded(clientId: String, clientSecret: String): Boolean {
        val expiry = context.dataStore.data.first()[TOKEN_EXPIRY_KEY] ?: return false

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
        val refreshToken = context.dataStore.data.first()[REFRESH_TOKEN_KEY]
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

            // Save the new tokens
            val expiryTime = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000)
            context.dataStore.edit { preferences ->
                preferences[ACCESS_TOKEN_KEY] = tokenResponse.accessToken
                preferences[REFRESH_TOKEN_KEY] = tokenResponse.refreshToken ?: refreshToken
                preferences[TOKEN_EXPIRY_KEY] = expiryTime
            }
            true
        } catch (e: Exception) {
            clearToken()
            false
        }
    }

    /**
     * One-time migration away from the removed multi-account storage.
     *
     * If a session was stored under the previously selected account, fold its
     * token and any bundled OAuth credentials into the single canonical keys,
     * then drop every legacy entry. Safe to call repeatedly.
     */
    suspend fun migrateFromMultiAccountStorage() {
        val snapshot = context.dataStore.data.first()
        val hasLegacyKeys = snapshot.asMap().keys.any { key ->
            key.name == LEGACY_CURRENT_ACCOUNT_KEY.name ||
                LEGACY_PREFIXES.any { key.name.startsWith(it) }
        }
        if (!hasLegacyKeys) return

        val accountId = snapshot[LEGACY_CURRENT_ACCOUNT_KEY]
        val legacyAccess = accountId?.let { snapshot[legacyStringKey("access_token_", it)] }
        val legacyRefresh = accountId?.let { snapshot[legacyStringKey("refresh_token_", it)] }
        val legacyExpiry = accountId?.let { snapshot[legacyExpiryKey(it)] }
        val legacyClientId = accountId?.let { snapshot[legacyStringKey("client_id_", it)] }
        val legacyClientSecret = accountId?.let { snapshot[legacyStringKey("client_secret_", it)] }

        val access = snapshot[ACCESS_TOKEN_KEY] ?: legacyAccess
        val refresh = snapshot[REFRESH_TOKEN_KEY] ?: legacyRefresh
        val expiry = snapshot[TOKEN_EXPIRY_KEY] ?: legacyExpiry
        val clientId = snapshot[CLIENT_ID_KEY] ?: legacyClientId
        val clientSecret = snapshot[CLIENT_SECRET_KEY] ?: legacyClientSecret

        context.dataStore.edit { preferences ->
            // Drop only the legacy entries, preserving the canonical keys.
            preferences.asMap().keys
                .filter { key ->
                    key.name == LEGACY_CURRENT_ACCOUNT_KEY.name ||
                        LEGACY_PREFIXES.any { key.name.startsWith(it) }
                }
                .forEach { preferences.remove(it) }

            if (!access.isNullOrEmpty()) {
                preferences[ACCESS_TOKEN_KEY] = access
                if (!refresh.isNullOrEmpty()) preferences[REFRESH_TOKEN_KEY] = refresh
                if (expiry != null) preferences[TOKEN_EXPIRY_KEY] = expiry
            }
            if (!clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()) {
                preferences[CLIENT_ID_KEY] = clientId
                preferences[CLIENT_SECRET_KEY] = clientSecret
            }
        }
    }
}
