package com.mosu.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.mosu.app.data.api.model.OsuUserCompact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.accountDataStore: DataStore<Preferences> by preferencesDataStore(name = "accounts")

/**
 * Caches the osu! profile of the single signed-in account.
 *
 * Mosu supports exactly one account; the OAuth session itself lives in
 * [TokenManager], while this only mirrors the last fetched [OsuUserCompact] so
 * the UI can render immediately on launch.
 */
class AccountManager(private val context: Context) {
    private val gson = Gson()

    companion object {
        private val USER_INFO_KEY = stringPreferencesKey("user_info")
        private const val LEGACY_USER_INFO_PREFIX = "user_info_"
    }

    val currentUserInfo: Flow<OsuUserCompact?> = context.accountDataStore.data
        .map { preferences -> preferences[USER_INFO_KEY]?.let { decode(it) } }

    suspend fun saveUserInfo(userInfo: OsuUserCompact) {
        context.accountDataStore.edit { preferences ->
            preferences[USER_INFO_KEY] = gson.toJson(userInfo)
        }
    }

    suspend fun getCachedUserInfo(): OsuUserCompact? {
        val preferences = context.accountDataStore.data.first()
        return preferences[USER_INFO_KEY]?.let { decode(it) }
    }

    suspend fun clearUserInfo() {
        context.accountDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    /**
     * Copies a cached multi-account profile into the single-account key and
     * drops the legacy per-account entries. Safe to call repeatedly.
     */
    suspend fun migrateLegacyUserInfo() {
        val snapshot = context.accountDataStore.data.first()
        val legacyEntry = snapshot.asMap().entries
            .firstOrNull { it.key.name.startsWith(LEGACY_USER_INFO_PREFIX) && it.value is String }
        val hasLegacyKeys = snapshot.asMap().keys.any { it.name.startsWith(LEGACY_USER_INFO_PREFIX) }
        if (!hasLegacyKeys) return

        val chosen = snapshot[USER_INFO_KEY] ?: (legacyEntry?.value as? String)
        context.accountDataStore.edit { preferences ->
            preferences.clear()
            if (!chosen.isNullOrEmpty()) preferences[USER_INFO_KEY] = chosen
        }
    }

    private fun decode(json: String): OsuUserCompact? =
        runCatching { gson.fromJson(json, OsuUserCompact::class.java) }.getOrNull()
}
