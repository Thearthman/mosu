package com.mosu.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mosu.app.data.api.RetrofitClient
import com.mosu.app.data.api.model.OsuUserCompact
import com.mosu.app.data.model.Account
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.accountDataStore: DataStore<Preferences> by preferencesDataStore(name = "accounts")

class AccountManager(
    private val context: Context,
    private val tokenManager: TokenManager
) {
    private val gson = Gson()

    companion object {
        private fun userInfoKey(accountId: String) = stringPreferencesKey("user_info_$accountId")
    }

    // Get all logged-in accounts (with user info)
    val accounts: Flow<List<Account>> = context.accountDataStore.data
        .map { preferences ->
            val loggedInAccountIds = preferences.asMap().keys
                .filter { it.name.startsWith("user_info_") }
                .map { it.name.removePrefix("user_info_") }

            loggedInAccountIds.mapNotNull { accountId ->
                val (clientId, clientSecret) = tokenManager.getAccountCredentials(accountId)
                if (clientId != null && clientSecret != null) {
                    val userInfoJson = preferences[userInfoKey(accountId)]
                    val userInfo = userInfoJson?.let { gson.fromJson(it, OsuUserCompact::class.java) }

                    Account(
                        id = accountId,
                        clientId = clientId,
                        clientSecret = clientSecret,
                        accessToken = tokenManager.getCurrentAccessToken(),
                        refreshToken = null, // Don't expose refresh tokens
                        tokenExpiry = null,   // Don't expose expiry
                        userInfo = userInfo
                    )
                } else null
            }
        }

    // Get current account
    val currentAccount: Flow<Account?> = tokenManager.currentAccountId
        .map { accountId ->
            accountId?.let { getAccount(it).first() }
        }

    // Get specific account
    fun getAccount(accountId: String): Flow<Account?> = context.accountDataStore.data
        .map { preferences ->
            val (clientId, clientSecret) = tokenManager.getAccountCredentials(accountId)
            if (clientId != null && clientSecret != null) {
                val userInfoJson = preferences[userInfoKey(accountId)]
                val userInfo = userInfoJson?.let { gson.fromJson(it, OsuUserCompact::class.java) }

                Account(
                    id = accountId,
                    clientId = clientId,
                    clientSecret = clientSecret,
                    accessToken = null, // Don't expose tokens
                    refreshToken = null,
                    tokenExpiry = null,
                    userInfo = userInfo
                )
            } else null
        }

    // Create new account
    suspend fun createAccount(accountId: String, clientId: String, clientSecret: String) {
        tokenManager.saveAccountCredentials(accountId, clientId, clientSecret)
    }

    // Switch to account
    suspend fun switchToAccount(accountId: String) {
        tokenManager.setCurrentAccount(accountId)
    }

    // Save user info for account
    suspend fun saveUserInfo(accountId: String, userInfo: OsuUserCompact) {
        val userInfoJson = gson.toJson(userInfo)
        context.accountDataStore.edit { preferences ->
            preferences[userInfoKey(accountId)] = userInfoJson
        }
    }
    
    // Get cached user info for account
    suspend fun getCachedUserInfo(accountId: String): OsuUserCompact? {
        val preferences = context.accountDataStore.data.first()
        val userInfoJson = preferences[userInfoKey(accountId)]
        return userInfoJson?.let { gson.fromJson(it, OsuUserCompact::class.java) }
    }
    // Delete account
    suspend fun deleteAccount(accountId: String) {
        context.accountDataStore.edit { preferences ->
            preferences.remove(userInfoKey(accountId))
        }
        tokenManager.deleteAccount(accountId)
    }

    // Login to account (OAuth flow)
    suspend fun loginToAccount(accountId: String, authCode: String, redirectUri: String): Boolean {
        return try {
            tokenManager.setCurrentAccount(accountId)
            val (clientId, clientSecret) = tokenManager.getAccountCredentials(accountId)

            if (clientId == null || clientSecret == null) {
                return false
            }

            val tokenResponse = RetrofitClient.api.getToken(
                clientId = clientId,
                clientSecret = clientSecret,
                code = authCode,
                grantType = "authorization_code",
                redirectUri = redirectUri
            )

            tokenManager.saveTokens(
                tokenResponse.accessToken,
                tokenResponse.refreshToken ?: "",
                tokenResponse.expiresIn
            )

            // Fetch and save user info
            val userInfo = RetrofitClient.api.getMe("")
            saveUserInfo(accountId, userInfo)

            true
        } catch (e: Exception) {
            false
        }
    }

    // Logout from current account
    suspend fun logoutFromCurrentAccount() {
        tokenManager.clearCurrentAccountToken()
        val currentAccountId = tokenManager.getCurrentAccountId()
        if (currentAccountId != null) {
            context.accountDataStore.edit { preferences ->
                preferences.remove(userInfoKey(currentAccountId))
            }
        }
    }
}
