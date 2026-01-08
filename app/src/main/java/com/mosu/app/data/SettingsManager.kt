package com.mosu.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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

