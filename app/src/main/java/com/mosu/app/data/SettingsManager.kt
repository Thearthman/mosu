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
import com.mosu.app.domain.skin.CustomSkinCatalogStore
import com.mosu.app.domain.skin.CustomSkinRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) : CustomSkinCatalogStore {
    
    companion object {
        // Legacy OAuth credential keys. Credentials are now owned by
        // [TokenManager]; these are read once by [drainLegacyCredentials] to
        // re-home any existing values and can be deleted afterwards.
        private val LEGACY_CLIENT_ID_KEY = stringPreferencesKey("osu_client_id")
        private val LEGACY_CLIENT_SECRET_KEY = stringPreferencesKey("osu_client_secret")
        private val PLAYED_FILTER_MODE_KEY = stringPreferencesKey("played_filter_mode") // "url" or "most_played"
        private val LANGUAGE_KEY = stringPreferencesKey("language") // "en", "zh-CN", "zh-TW"
        private val DEFAULT_SEARCH_VIEW_KEY = stringPreferencesKey("default_search_view") // played/recent/favorite/most_played/all/any
        private val ONLY_LEADERBOARD_KEY = booleanPreferencesKey("only_leaderboard_enabled")
        private val RECENT_PLAY_HISTORY_KEY = booleanPreferencesKey("recent_play_history_enabled")
        private val INFO_COVER_KEY = booleanPreferencesKey("info_cover_enabled")
        private val GENRE_BAR_ENABLED_KEY = booleanPreferencesKey("genre_bar_enabled")
        private val RENDERER_ENABLED_KEY = booleanPreferencesKey("renderer_enabled")
        private const val RENDERER_DIFFICULTY_KEY_PREFIX = "renderer_difficulty_"
        private val HITSOUND_ENABLED_KEY = booleanPreferencesKey("hitsound_enabled")
        private val HITSOUND_VOLUME_KEY = intPreferencesKey("hitsound_volume")
        private val HITSOUND_LATENCY_MS_KEY = intPreferencesKey("hitsound_latency_ms")
        private val SHUFFLE_MODE_KEY = booleanPreferencesKey("shuffle_mode_enabled")
        private val REPEAT_MODE_KEY = intPreferencesKey("repeat_mode")
        private val SCORE_MODE_FILTER_KEY = stringPreferencesKey("score_mode_filter") // "osu", "taiko", "fruits", "mania", "all"
        private val SEARCH_MODE_FILTER_KEY = stringPreferencesKey("search_mode_filter") // "osu", "taiko", "fruits", "mania", "all"
        private val PREFERRED_MIRROR_KEY = stringPreferencesKey("preferred_mirror") // "nerinyan", "sayobot", or "catboy"
        private val API_SOURCE_KEY_OLD = stringPreferencesKey("api_source") // Deprecated
        private val REGION_CHECKED_KEY = booleanPreferencesKey("region_checked")
        private val DETECTED_REGION_KEY = stringPreferencesKey("detected_region")
        private val DETECTED_REGION_SOURCE_KEY = stringPreferencesKey("detected_region_source")
        private val REGION_CHECKED_AT_KEY = longPreferencesKey("region_checked_at")
        private val MIRROR_MANUAL_OVERRIDE_KEY = booleanPreferencesKey("mirror_manual_override")
        private val CUSTOM_SKINS_KEY = stringPreferencesKey("custom_skins")
        private val ACTIVE_CUSTOM_SKIN_ID_KEY = stringPreferencesKey("active_custom_skin_id")
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

    val recentPlayHistoryEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[RECENT_PLAY_HISTORY_KEY] ?: false
        }

    val infoCoverEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[INFO_COVER_KEY] ?: true
        }

    val genreBarEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[GENRE_BAR_ENABLED_KEY] ?: true
        }

    val rendererEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[RENDERER_ENABLED_KEY] ?: true
        }

    val hitsoundEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[HITSOUND_ENABLED_KEY] ?: true
        }

    val hitsoundVolume: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            (preferences[HITSOUND_VOLUME_KEY] ?: 100).coerceIn(0, 100)
        }

    val hitsoundLatencyMs: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            (preferences[HITSOUND_LATENCY_MS_KEY] ?: 0).coerceIn(-200, 200)
        }

    val customSkins: Flow<List<CustomSkinRecord>> = context.settingsDataStore.data
        .map { preferences ->
            decodeCustomSkins(preferences[CUSTOM_SKINS_KEY])
        }

    val activeCustomSkinId: Flow<String?> = context.settingsDataStore.data
        .map { preferences ->
            preferences[ACTIVE_CUSTOM_SKIN_ID_KEY]?.takeIf { it.isNotBlank() }
        }

    val shuffleModeEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[SHUFFLE_MODE_KEY] ?: false
        }

    val repeatMode: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            preferences[REPEAT_MODE_KEY] ?: Player.REPEAT_MODE_OFF
        }

    val scoreModeFilter: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[SCORE_MODE_FILTER_KEY] ?: "osu"
        }

    val searchModeFilter: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[SEARCH_MODE_FILTER_KEY] ?: "osu"
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

    suspend fun saveRecentPlayHistoryEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[RECENT_PLAY_HISTORY_KEY] = enabled
        }
    }

    suspend fun saveInfoCoverEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[INFO_COVER_KEY] = enabled
        }
    }

    suspend fun saveGenreBarEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[GENRE_BAR_ENABLED_KEY] = enabled
        }
    }

    suspend fun saveRendererEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[RENDERER_ENABLED_KEY] = enabled
        }
    }

    suspend fun saveHitsoundEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[HITSOUND_ENABLED_KEY] = enabled
        }
    }

    suspend fun saveHitsoundVolume(volume: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[HITSOUND_VOLUME_KEY] = volume.coerceIn(0, 100)
        }
    }

    suspend fun saveHitsoundLatencyMs(latencyMs: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[HITSOUND_LATENCY_MS_KEY] = latencyMs.coerceIn(-200, 200)
        }
    }

    override suspend fun readCustomSkins(): List<CustomSkinRecord> =
        decodeCustomSkins(context.settingsDataStore.data.first()[CUSTOM_SKINS_KEY])

    override suspend fun writeCustomSkins(skins: List<CustomSkinRecord>) {
        context.settingsDataStore.edit { preferences ->
            preferences[CUSTOM_SKINS_KEY] = encodeCustomSkins(skins)
        }
    }

    override suspend fun readActiveCustomSkinId(): String? =
        context.settingsDataStore.data.first()[ACTIVE_CUSTOM_SKIN_ID_KEY]
            ?.takeIf { it.isNotBlank() }

    override suspend fun writeActiveCustomSkinId(id: String?) {
        context.settingsDataStore.edit { preferences ->
            if (id.isNullOrBlank()) {
                preferences.remove(ACTIVE_CUSTOM_SKIN_ID_KEY)
            } else {
                preferences[ACTIVE_CUSTOM_SKIN_ID_KEY] = id
            }
        }
    }

    suspend fun getSelectedDifficulty(songKey: String): String? {
        if (songKey.isBlank()) return null
        return context.settingsDataStore.data.first()[
            stringPreferencesKey(RENDERER_DIFFICULTY_KEY_PREFIX + songKey)
        ]
    }

    /**
     * Observes the selected renderer difficulty for one audio group. The
     * service-side hitsound scheduler needs the same difficulty choice as the
     * visual renderer while remaining independent of player UI composition.
     */
    fun selectedDifficulty(songKey: String): Flow<String?> {
        if (songKey.isBlank()) return flowOf(null)
        val preferenceKey = stringPreferencesKey(RENDERER_DIFFICULTY_KEY_PREFIX + songKey)
        return context.settingsDataStore.data.map { preferences ->
            preferences[preferenceKey]
        }
    }

    suspend fun saveSelectedDifficulty(songKey: String, selection: String) {
        if (songKey.isBlank() || selection.isBlank()) return
        context.settingsDataStore.edit { preferences ->
            preferences[stringPreferencesKey(RENDERER_DIFFICULTY_KEY_PREFIX + songKey)] =
                selection
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

    suspend fun saveScoreModeFilter(mode: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[SCORE_MODE_FILTER_KEY] = mode
        }
    }

    suspend fun saveSearchModeFilter(mode: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[SEARCH_MODE_FILTER_KEY] = mode
        }
    }

    /**
     * One-time migration helper: returns the OAuth credentials that used to be
     * stored in Settings and removes them, so they can be re-homed in
     * [TokenManager]. Returns null when there is nothing stored.
     */
    suspend fun drainLegacyCredentials(): Pair<String, String>? {
        val snapshot = context.settingsDataStore.data.first()
        val clientId = snapshot[LEGACY_CLIENT_ID_KEY]
        val clientSecret = snapshot[LEGACY_CLIENT_SECRET_KEY]
        if (clientId.isNullOrBlank() && clientSecret.isNullOrBlank()) return null

        context.settingsDataStore.edit { preferences ->
            preferences.remove(LEGACY_CLIENT_ID_KEY)
            preferences.remove(LEGACY_CLIENT_SECRET_KEY)
        }

        return if (!clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()) {
            clientId to clientSecret
        } else {
            null
        }
    }

    private fun decodeCustomSkins(raw: String?): List<CustomSkinRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    val name = item.optString("name").trim()
                    if (id.isEmpty() || name.isEmpty()) continue
                    add(
                        CustomSkinRecord(
                            id = id,
                            name = name,
                            author = item.optString("author").trim()
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeCustomSkins(skins: List<CustomSkinRecord>): String {
        val array = JSONArray()
        skins.forEach { skin ->
            array.put(
                JSONObject()
                    .put("id", skin.id)
                    .put("name", skin.name)
                    .put("author", skin.author)
            )
        }
        return array.toString()
    }
}
