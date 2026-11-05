package com.mosu.app.ui.player

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.mosu.app.data.SettingsManager
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.domain.rating.BeatmapStarRatingCatalog
import com.mosu.app.domain.rating.BeatmapStarRatingService
import com.mosu.app.domain.rating.DifficultyRatingQuery
import com.mosu.app.domain.render.PreparedRender
import com.mosu.app.domain.render.RenderAudioFrame
import com.mosu.app.domain.render.RenderCatalog
import com.mosu.app.domain.render.RenderCatalogLoader
import com.mosu.app.domain.render.RenderDifficultyKey
import com.mosu.app.domain.render.RenderSkinSelection
import com.mosu.app.domain.render.RendererRegistry
import com.mosu.app.domain.render.audio.BassRenderAudioSink
import com.mosu.app.domain.skin.CustomSkinLibrary
import com.mosu.app.domain.skin.CustomSkinRecord
import com.mosu.app.player.MusicController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class RenderPreviewData(
    val mediaId: Long,
    val catalog: RenderCatalog,
    val selectedDifficultyKey: RenderDifficultyKey,
    val starRatings: BeatmapStarRatingCatalog = BeatmapStarRatingCatalog.Empty,
    val preparedRender: PreparedRender?,
    val preparationAttempted: Boolean = false
)

/**
 * Owns renderer preparation and hitsound scheduling independently of player
 * visibility. Both Full Player orientations consume this one session.
 */
@Stable
class RenderPlaybackSession internal constructor(
    context: Context,
    db: AppDatabase,
    private val settingsManager: SettingsManager,
    private val customSkinLibrary: CustomSkinLibrary,
    private val musicController: MusicController,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val registry = RendererRegistry.default()
    private val catalogLoader = RenderCatalogLoader(appContext, db, registry.supportedModes)
    private val ratingService = BeatmapStarRatingService(db)
    private val audioSink = BassRenderAudioSink(appContext)
    private val lifecycleMutex = Mutex()
    private val audioLock = Any()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cleanupQueue = Channel<CleanupCommand>(Channel.UNLIMITED)

    init {
        cleanupScope.launch {
            for (command in cleanupQueue) {
                when (command) {
                    is CleanupCommand.Close -> lifecycleMutex.withLock {
                        synchronized(audioLock) {
                            command.prepared.close()
                        }
                    }

                    CleanupCommand.ReleaseSink -> lifecycleMutex.withLock {
                        audioSink.release()
                    }
                }
                if (command === CleanupCommand.ReleaseSink) break
            }
            cleanupScope.cancel()
        }
    }

    var previewData by mutableStateOf<RenderPreviewData?>(null)
        private set

    var catalogUnavailableMediaId by mutableStateOf<Long?>(null)
        private set

    var rendererEnabled by mutableStateOf(false)
        private set

    var hitsoundEnabled by mutableStateOf(false)
        private set

    var skinSelection by mutableStateOf(RenderSkinSelection.Bundled)
        private set

    var skinSelectionReady by mutableStateOf(false)
        private set

    var preferencesLoaded by mutableStateOf(false)
        private set

    private var hitsoundVolume = 100
    private var hitsoundLatencyMs = 0
    private var audioGeneration = 0L
    @Volatile
    private var audioReadyRender: PreparedRender? = null
    private var released = false

    val isFeatureEnabled: Boolean
        get() = rendererEnabled || hitsoundEnabled

    val previewUnavailable: Boolean
        get() {
            val data = previewData ?: return true
            return when {
                !rendererEnabled -> true
                data.mediaId != currentMediaId -> true
                data.preparedRender == null -> data.preparationAttempted
                else -> false
            }
        }

    val previewAspectRatio: Float
        get() {
            val data = previewData ?: return PREVIEW_ASPECT_RATIO
            val difficulty = data.catalog.difficulty(data.selectedDifficultyKey)
                ?: return PREVIEW_ASPECT_RATIO
            return registry.rendererFor(difficulty.mode)?.preferredAspectRatio
                ?: PREVIEW_ASPECT_RATIO
        }

    private val currentMediaId: Long?
        get() = musicController.currentMediaId.value

    fun updatePreferences(
        loaded: Boolean,
        renderer: Boolean,
        hitsound: Boolean,
        volume: Int,
        latencyMs: Int
    ) {
        if (released) return
        preferencesLoaded = loaded
        val hitsoundChanged = this.hitsoundEnabled != hitsound
        val latencyChanged = this.hitsoundLatencyMs != latencyMs.coerceIn(-200, 200)
        rendererEnabled = renderer
        hitsoundEnabled = hitsound
        hitsoundVolume = volume.coerceIn(0, 100)
        hitsoundLatencyMs = latencyMs.coerceIn(-200, 200)
        audioSink.setMasterVolume(hitsoundVolume)
        if (hitsoundChanged || latencyChanged) {
            audioGeneration++
            if (!hitsound) {
                audioReadyRender = null
                audioSink.stopAll()
            }
        }
    }

    /**
     * Invalidates any renderer prepared with the previous skin and switches the
     * native sample cache. Preparation and preload both re-check this value
     * before publishing, so a slow old-skin load cannot become current.
     */
    suspend fun updateSkinSelection(selection: RenderSkinSelection) {
        if (released) return
        Log.d(
            SKIN_TRACE_TAG,
            "updateSkinSelection old=${skinSelection.id}/${skinSelection.name} " +
                "new=${selection.id}/${selection.name} dir=${selection.directory}"
        )
        if (skinSelection != selection) {
            skinSelection = selection
            skinSelectionReady = false
            audioReadyRender = null
            audioGeneration++
            audioSink.stopAll()

            val current = previewData
            if (current != null) {
                previewData = current.copy(
                    preparedRender = null,
                    preparationAttempted = false
                )
                enqueuePreparedClose(current.preparedRender)
            }
        }

        lifecycleMutex.withLock {
            if (released || skinSelection != selection) return
            withContext(Dispatchers.IO) {
                audioSink.setSkinSelection(selection)
            }
            if (!released && skinSelection == selection) {
                skinSelectionReady = true
                Log.d(SKIN_TRACE_TAG, "skinSelectionReady id=${selection.id}")
            }
        }
    }

    /**
     * Reconciles the renderer with the persisted active skin immediately after
     * an import. DataStore normally drives this through [rememberRenderPlaybackSession],
     * but doing it here closes the race between the import write and the player
     * session's next preference collection.
     */
    suspend fun refreshSkinSelection() {
        if (released) return
        val preferences = CustomSkinPreferences(
            skins = settingsManager.readCustomSkins(),
            activeId = settingsManager.readActiveCustomSkinId()
        )
        Log.d(
            SKIN_TRACE_TAG,
            "refreshSkinSelection activeId=${preferences.activeId} " +
                "records=${preferences.skins.map { it.id }}"
        )
        updateSkinSelection(resolveActiveSkinSelection(preferences, customSkinLibrary))
        if (
            released ||
            !preferencesLoaded ||
            !isFeatureEnabled ||
            previewData == null
        ) {
            return
        }
        prepareSelectedRenderer()
        preloadSelectedRendererAudio()
    }

    suspend fun loadCatalog() {
        if (released) return
        val mediaId = currentMediaId
        if (!preferencesLoaded || !isFeatureEnabled || mediaId == null) {
            if (mediaId == null) clearPreview()
            return
        }

        val existing = previewData?.takeIf { it.mediaId == mediaId }
        if (previewData?.mediaId != mediaId) clearPreview()
        catalogUnavailableMediaId = null

        val catalog = existing?.catalog
            ?: withContext(Dispatchers.IO) { catalogLoader.loadCatalog(mediaId) }
            ?: run {
                if (!released && currentMediaId == mediaId) {
                    catalogUnavailableMediaId = mediaId
                }
                return
            }
        if (released || currentMediaId != mediaId) return
        val cachedRatings = ratingService.getCachedRatings(catalog.beatmapSetId)
        if (released) return
        val selectedDifficultyKey = existing?.selectedDifficultyKey
            ?: catalog.resolveSavedDifficulty(
                settingsManager.getSelectedDifficulty(catalog.selectionKey)
            )
        previewData = RenderPreviewData(
            mediaId = mediaId,
            catalog = catalog,
            selectedDifficultyKey = selectedDifficultyKey,
            starRatings = cachedRatings,
            preparedRender = existing?.preparedRender,
            preparationAttempted = existing?.preparationAttempted ?: false
        )

        val requiredDifficulties = catalog.difficulties.map { difficulty ->
            DifficultyRatingQuery(
                beatmapId = difficulty.beatmapId,
                mode = difficulty.mode.osuMode,
                difficultyName = difficulty.difficultyName
            )
        }
        if (requiredDifficulties.any { !cachedRatings.hasRatingFor(it) }) {
            val refreshed = ratingService.refreshMissingRatings(
                beatmapSetId = catalog.beatmapSetId,
                requiredDifficulties = requiredDifficulties
            )
            if (released) return
            val latest = previewData
            if (
                latest?.mediaId == mediaId &&
                latest.catalog.beatmapSetId == catalog.beatmapSetId
            ) {
                previewData = latest.copy(starRatings = refreshed)
            }
        }
    }

    suspend fun prepareSelectedRenderer() {
        lifecycleMutex.withLock {
            if (released) return
            val data = previewData ?: return
            if (
                !preferencesLoaded ||
                !skinSelectionReady ||
                !isFeatureEnabled ||
                data.preparedRender != null
            ) {
                return
            }
            val difficulty = data.catalog.difficulty(data.selectedDifficultyKey) ?: return
            val selectedSkin = skinSelection

            val prepared = registry.prepare(appContext, difficulty, selectedSkin)
            Log.d(
                SKIN_TRACE_TAG,
                "prepareSelectedRenderer skin=${selectedSkin.id}/${selectedSkin.name} " +
                    "prepared=${prepared != null}"
            )
            var published = false
            try {
                if (released) return
                val latest = previewData
                if (
                    latest?.mediaId == data.mediaId &&
                    latest.selectedDifficultyKey == data.selectedDifficultyKey &&
                    skinSelection == selectedSkin &&
                    skinSelectionReady &&
                    isFeatureEnabled
                ) {
                    replacePreparedLocked(prepared)
                    previewData = latest.copy(
                        preparedRender = prepared,
                        preparationAttempted = true
                    )
                    published = true
                }
            } finally {
                if (!published) {
                    synchronized(audioLock) {
                        prepared?.close()
                    }
                }
            }
        }
    }

    suspend fun preloadSelectedRendererAudio() {
        lifecycleMutex.withLock {
            if (released || !hitsoundEnabled || !skinSelectionReady) return
            val data = previewData ?: return
            val prepared = data.preparedRender ?: return
            if (data.mediaId != currentMediaId || audioReadyRender === prepared) return
            val selectedSkin = skinSelection

            audioReadyRender = null
            Log.d(
                SKIN_TRACE_TAG,
                "preloadSelectedRendererAudio begin skin=${selectedSkin.id}/${selectedSkin.name}"
            )
            withContext(Dispatchers.IO) {
                if (!released && skinSelection == selectedSkin) {
                    prepared.preloadAudio(audioSink)
                }
            }
            if (
                !released &&
                hitsoundEnabled &&
                skinSelectionReady &&
                skinSelection == selectedSkin &&
                previewData?.preparedRender === prepared &&
                previewData?.mediaId == currentMediaId
            ) {
                audioReadyRender = prepared
                Log.d(
                    SKIN_TRACE_TAG,
                    "preloadSelectedRendererAudio ready skin=${selectedSkin.id}/${selectedSkin.name}"
                )
            }
        }
    }

    fun updateRendererEnabled(enabled: Boolean) {
        if (released) return
        rendererEnabled = enabled
        scope.launch { settingsManager.saveRendererEnabled(enabled) }
    }

    fun updateHitsoundEnabled(enabled: Boolean) {
        if (released || hitsoundEnabled == enabled) return
        hitsoundEnabled = enabled
        audioGeneration++
        if (!enabled) {
            audioReadyRender = null
            audioSink.stopAll()
        }
        scope.launch { settingsManager.saveHitsoundEnabled(enabled) }
    }

    fun selectDifficulty(difficultyKey: RenderDifficultyKey) {
        if (released) return
        val current = previewData ?: return
        if (current.mediaId != currentMediaId) return
        previewData = current.copy(
            selectedDifficultyKey = difficultyKey,
            preparedRender = null,
            preparationAttempted = false
        )
        detachAndDisposePrepared(current.preparedRender)
        scope.launch {
            settingsManager.saveSelectedDifficulty(
                songKey = current.catalog.selectionKey,
                selection = difficultyKey.encode()
            )
        }
    }

    fun updateAudio() {
        synchronized(audioLock) {
            if (released) return
            val data = previewData ?: return
            val prepared = data.preparedRender ?: return
            if (
                !hitsoundEnabled ||
                data.mediaId != currentMediaId ||
                audioReadyRender !== prepared
            ) {
                audioSink.stopAll()
                return
            }
            audioSink.setMasterVolume(hitsoundVolume)
            val combinedEpoch = musicController.playbackEpoch.value * 1_000_000L + audioGeneration
            prepared.updateAudio(
                frame = RenderAudioFrame(
                    mapTimeMs = musicController.currentPositionMs().toDouble(),
                    clockEpoch = combinedEpoch,
                    isPlaying = musicController.isPlaying.value,
                    latencyMs = hitsoundLatencyMs
                ),
                sink = audioSink
            )
        }
    }

    fun stopAudio() {
        audioSink.stopAll()
    }

    fun release() {
        if (released) return
        released = true
        audioReadyRender = null
        val prepared = previewData?.preparedRender
        previewData = null
        catalogUnavailableMediaId = null
        audioSink.stopAll()
        enqueuePreparedClose(prepared)
        cleanupQueue.trySend(CleanupCommand.ReleaseSink)
    }

    private fun clearPreview() {
        val prepared = previewData?.preparedRender
        previewData = null
        catalogUnavailableMediaId = null
        detachAndDisposePrepared(prepared)
    }

    private fun replacePreparedLocked(prepared: PreparedRender?) {
        val previous = previewData?.preparedRender
        if (previous === prepared) return
        audioReadyRender = null
        audioSink.stopAll()
        audioGeneration++
        synchronized(audioLock) {
            previous?.close()
        }
    }

    private fun detachAndDisposePrepared(prepared: PreparedRender?) {
        if (prepared == null) return
        audioReadyRender = null
        audioSink.stopAll()
        audioGeneration++
        enqueuePreparedClose(prepared)
    }

    private fun enqueuePreparedClose(prepared: PreparedRender?) {
        if (prepared != null) {
            cleanupQueue.trySend(CleanupCommand.Close(prepared))
        }
    }

    private sealed interface CleanupCommand {
        data class Close(val prepared: PreparedRender) : CleanupCommand
        data object ReleaseSink : CleanupCommand
    }

    private companion object {
        const val SKIN_TRACE_TAG = "MosuSkinTrace"
    }
}

/**
 * Keeps catalog preparation and the hitsound scheduler alive above both Full
 * Player orientations. Only drawing remains conditional on visibility.
 */
@Composable
fun rememberRenderPlaybackSession(
    context: Context,
    db: AppDatabase,
    settingsManager: SettingsManager,
    customSkinLibrary: CustomSkinLibrary,
    musicController: MusicController
): RenderPlaybackSession {
    val scope = rememberCoroutineScope()
    val session = remember(context.applicationContext, db, settingsManager, musicController) {
        RenderPlaybackSession(
            context = context,
            db = db,
            settingsManager = settingsManager,
            customSkinLibrary = customSkinLibrary,
            musicController = musicController,
            scope = scope
        )
    }
    DisposableEffect(session) {
        onDispose { session.release() }
    }
    val rendererPreference by settingsManager.rendererEnabled.collectAsState(initial = null)
    val hitsoundPreference by settingsManager.hitsoundEnabled.collectAsState(initial = null)
    val hitsoundVolume by settingsManager.hitsoundVolume.collectAsState(initial = 100)
    val hitsoundLatency by settingsManager.hitsoundLatencyMs.collectAsState(initial = 0)
    val currentMediaId by musicController.currentMediaId.collectAsState()
    val skinPreferences by produceState<CustomSkinPreferences?>(
        initialValue = null,
        key1 = settingsManager
    ) {
        combine(
            settingsManager.customSkins,
            settingsManager.activeCustomSkinId
        ) { skins, activeId ->
            CustomSkinPreferences(skins, activeId)
        }.collect { value = it }
    }
    val activeSkinSelection = remember(skinPreferences, customSkinLibrary) {
        resolveActiveSkinSelection(skinPreferences, customSkinLibrary)
    }
    val preferencesLoaded = rendererPreference != null &&
        hitsoundPreference != null &&
        skinPreferences != null

    LaunchedEffect(session, activeSkinSelection) {
        session.updateSkinSelection(activeSkinSelection)
    }

    LaunchedEffect(
        preferencesLoaded,
        rendererPreference,
        hitsoundPreference,
        hitsoundVolume,
        hitsoundLatency
    ) {
        if (preferencesLoaded) {
            session.updatePreferences(
                loaded = true,
                renderer = rendererPreference == true,
                hitsound = hitsoundPreference == true,
                volume = hitsoundVolume,
                latencyMs = hitsoundLatency
            )
        }
    }

    LaunchedEffect(
        session,
        preferencesLoaded,
        session.isFeatureEnabled,
        currentMediaId,
        hitsoundVolume,
        hitsoundLatency
    ) {
        if (!preferencesLoaded) return@LaunchedEffect
        session.loadCatalog()
    }

    LaunchedEffect(
        session,
        preferencesLoaded,
        session.isFeatureEnabled,
        session.previewData?.mediaId,
        session.previewData?.selectedDifficultyKey,
        session.skinSelection,
        session.skinSelectionReady,
        currentMediaId
    ) {
        if (!preferencesLoaded) return@LaunchedEffect
        session.prepareSelectedRenderer()
    }

    LaunchedEffect(
        session,
        preferencesLoaded,
        session.hitsoundEnabled,
        session.previewData?.mediaId,
        session.previewData?.selectedDifficultyKey,
        session.previewData?.preparedRender,
        session.skinSelection,
        session.skinSelectionReady,
        currentMediaId
    ) {
        if (!preferencesLoaded) return@LaunchedEffect
        session.preloadSelectedRendererAudio()
    }

    DisposableEffect(session, preferencesLoaded, session.isFeatureEnabled, currentMediaId) {
        if (preferencesLoaded && session.isFeatureEnabled && currentMediaId != null) {
            musicController.startPositionSampling()
        }
        onDispose {
            musicController.stopPositionSampling()
        }
    }

    LaunchedEffect(session, preferencesLoaded, session.hitsoundEnabled, currentMediaId) {
        if (!preferencesLoaded || !session.hitsoundEnabled || currentMediaId == null) {
            session.stopAudio()
            return@LaunchedEffect
        }
        while (isActive) {
            withFrameNanos { session.updateAudio() }
        }
    }

    return session
}

private data class CustomSkinPreferences(
    val skins: List<CustomSkinRecord>,
    val activeId: String?
)

private fun resolveActiveSkinSelection(
    preferences: CustomSkinPreferences?,
    customSkinLibrary: CustomSkinLibrary
): RenderSkinSelection {
    val activeId = preferences?.activeId ?: return RenderSkinSelection.Bundled
    val record = preferences.skins.firstOrNull { it.id == activeId }
        ?: return RenderSkinSelection.Bundled
    val directory = customSkinLibrary.directoryFor(record.id)
    if (!directory.isDirectory) return RenderSkinSelection.Bundled
    return RenderSkinSelection(
        id = record.id,
        name = record.name,
        directory = directory
    )
}
