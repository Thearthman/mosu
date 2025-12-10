package com.mosu.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
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
        private val SEARCH_ANY_KEY = booleanPreferencesKey("search_any_enabled")
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

    val searchAnyEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[SEARCH_ANY_KEY] ?: false
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

    suspend fun saveSearchAnyEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SEARCH_ANY_KEY] = enabled
        }
    }

    suspend fun clearCredentials() {
        context.settingsDataStore.edit { preferences ->
            preferences.remove(CLIENT_ID_KEY)
            preferences.remove(CLIENT_SECRET_KEY)
        }
    }
}

