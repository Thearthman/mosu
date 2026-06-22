package com.mosu.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.media3.common.Player
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    
    companion object {
        private val CLIENT_ID_KEY = stringPreferencesKey("osu_client_id")
        private val CLIENT_SECRET_KEY = stringPreferencesKey("osu_client_secret")
        private val PLAYED_FILTER_MODE_KEY = stringPreferencesKey("played_filter_mode") // "url" or "most_played"
        private val LANGUAGE_KEY = stringPreferencesKey("language") // "en", "zh-CN", "zh-TW"
        private val DEFAULT_SEARCH_VIEW_KEY = stringPreferencesKey("default_search_view") // played/recent/favorite/most_played/all/any
        private val ONLY_LEADERBOARD_KEY = booleanPreferencesKey("only_leaderboard_enabled")
        private val INFO_COVER_KEY = booleanPreferencesKey("info_cover_enabled")
        private val SHUFFLE_MODE_KEY = booleanPreferencesKey("shuffle_mode_enabled")
        private val REPEAT_MODE_KEY = intPreferencesKey("repeat_mode")
        private val PREFERRED_MIRROR_KEY = stringPreferencesKey("preferred_mirror") // "nerinyan" or "sayobot"
        private val API_SOURCE_KEY_OLD = stringPreferencesKey("api_source") // Deprecated
        private val REGION_CHECKED_KEY = booleanPreferencesKey("region_checked")
        private val DETECTED_REGION_KEY = stringPreferencesKey("detected_region")
        private val DETECTED_REGION_SOURCE_KEY = stringPreferencesKey("detected_region_source")
        private val REGION_CHECKED_AT_KEY = longPreferencesKey("region_checked_at")
        private val MIRROR_MANUAL_OVERRIDE_KEY = booleanPreferencesKey("mirror_manual_override")
    }

    val detectedRegion: Flow<String?> = context.settingsDataStore.data
        .map { preferences ->
            preferences[DETECTED_REGION_KEY]
        }

    val detectedRegionSource: Flow<String?> = context.settingsDataStore.data
        .map { preferences ->
            preferences[DETECTED_REGION_SOURCE_KEY]
        }

    val regionCheckedAt: Flow<Long> = context.settingsDataStore.data
        .map { preferences ->
            preferences[REGION_CHECKED_AT_KEY] ?: 0L
        }

    suspend fun setDetectedRegion(region: String, source: String = "unknown") {
        context.settingsDataStore.edit { preferences ->
            preferences[DETECTED_REGION_KEY] = region
            preferences[DETECTED_REGION_SOURCE_KEY] = source
            preferences[REGION_CHECKED_KEY] = true
            preferences[REGION_CHECKED_AT_KEY] = System.currentTimeMillis()
        }
    }

    val preferredMirror: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            // Migration: if old API_SOURCE_KEY exists and is "osu", map to "nerinyan"
            // If new PREFERRED_MIRROR_KEY exists, use it. Otherwise default to "nerinyan"
            val oldVal = preferences[API_SOURCE_KEY_OLD]
            val newVal = preferences[PREFERRED_MIRROR_KEY]
            
            when {
                newVal != null -> newVal
                oldVal == "osu" -> "nerinyan"
                oldVal == "sayobot" -> "sayobot"
                else -> "nerinyan"
            }
        }

    val mirrorManuallySelected: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[MIRROR_MANUAL_OVERRIDE_KEY] ?: false
        }

    suspend fun setPreferredMirror(mirror: String, manual: Boolean = true) {
        context.settingsDataStore.edit { preferences ->
            preferences[PREFERRED_MIRROR_KEY] = mirror
            preferences[MIRROR_MANUAL_OVERRIDE_KEY] = manual
        }
    }

    val regionChecked: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[REGION_CHECKED_KEY] ?: false
        }

    suspend fun setRegionChecked(checked: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[REGION_CHECKED_KEY] = checked
            if (checked) {
                preferences[REGION_CHECKED_AT_KEY] = System.currentTimeMillis()
            }
        }
    }

    val clientId: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[CLIENT_ID_KEY] ?: ""
        }
    
    val clientSecret: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[CLIENT_SECRET_KEY] ?: ""
        }
    
    val playedFilterMode: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[PLAYED_FILTER_MODE_KEY] ?: "url" // Default to URL-based filtering
        }

    val language: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[LANGUAGE_KEY] ?: "en"
        }

    val defaultSearchView: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[DEFAULT_SEARCH_VIEW_KEY] ?: "played"
        }

    val onlyLeaderboardEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[ONLY_LEADERBOARD_KEY] ?: true
        }

    val infoCoverEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[INFO_COVER_KEY] ?: true
        }

    val shuffleModeEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[SHUFFLE_MODE_KEY] ?: false
        }

    val repeatMode: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            preferences[REPEAT_MODE_KEY] ?: Player.REPEAT_MODE_OFF
        }

    suspend fun saveCredentials(clientId: String, clientSecret: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[CLIENT_ID_KEY] = clientId
            preferences[CLIENT_SECRET_KEY] = clientSecret
        }
    }
    
    suspend fun savePlayedFilterMode(mode: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[PLAYED_FILTER_MODE_KEY] = mode
        }
    }

    suspend fun saveLanguage(language: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = language
        }
    }

    suspend fun saveDefaultSearchView(view: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[DEFAULT_SEARCH_VIEW_KEY] = view
        }
    }

    suspend fun saveOnlyLeaderboardEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[ONLY_LEADERBOARD_KEY] = enabled
        }
    }

    suspend fun saveInfoCoverEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[INFO_COVER_KEY] = enabled
        }
    }

    suspend fun saveShuffleModeEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SHUFFLE_MODE_KEY] = enabled
        }
    }

    suspend fun saveRepeatMode(mode: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[REPEAT_MODE_KEY] = mode
        }
    }

    suspend fun clearCredentials() {
        context.settingsDataStore.edit { preferences ->
            preferences.remove(CLIENT_ID_KEY)
            preferences.remove(CLIENT_SECRET_KEY)
        }
    }
}
