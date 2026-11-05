package com.mosu.app.player

import android.content.Context
import android.util.Log
import com.mosu.app.data.SettingsManager
import com.mosu.app.data.db.AppDatabase
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
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns hitsound preparation, BASS sample playback, and cue scheduling inside
 * the media service. The player UI can disappear while this coordinator keeps
 * following ExoPlayer's authoritative position.
 */
class HitsoundPlaybackCoordinator(
    context: Context,
    db: AppDatabase,
    private val settingsManager: SettingsManager
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val registry = RendererRegistry.default()
    private val catalogLoader = RenderCatalogLoader(appContext, db, registry.supportedModes)
    private val customSkinLibrary = CustomSkinLibrary(
        rootDirectory = File(appContext.filesDir, "custom_skins"),
        catalog = settingsManager
    )
    private val sink = BassRenderAudioSink(appContext)
    private val lifecycleMutex = Mutex()

    private var configuration = HitsoundConfiguration()
    private var configurationReady = false
    private var released = false

    private var mediaId: Long? = null
    private var positionMs = 0L
    private var isPlaying = false
    private var clockEpoch = 0L

    private var catalog: RenderCatalog? = null
    private var selectedDifficultyKey: RenderDifficultyKey? = null
    private var activeRender: PreparedRender? = null

    private var prepareRequest = 0L
    private var difficultyJob: Job? = null
    private var observedDifficultyKey: String? = null

    init {
        scope.launch {
            combine(
                settingsManager.hitsoundEnabled,
                settingsManager.hitsoundVolume,
                settingsManager.hitsoundLatencyMs,
                settingsManager.customSkins,
                settingsManager.activeCustomSkinId
            ) { enabled, volume, latencyMs, skins, activeSkinId ->
                HitsoundConfiguration(
                    enabled = enabled,
                    volume = volume,
                    latencyMs = latencyMs,
                    skinSelection = resolveSkinSelection(skins, activeSkinId)
                )
            }.collect(::applyConfiguration)
        }
    }

    /**
     * Called from the player callback on the service main thread.
     */
    fun onMediaItemChanged(mediaId: Long?) {
        if (released || this.mediaId == mediaId) return
        this.mediaId = mediaId
        positionMs = 0L
        catalog = null
        selectedDifficultyKey = null
        observedDifficultyKey = null
        difficultyJob?.cancel()
        difficultyJob = null
        retireActiveRender()
        invalidateAudioClock()
        requestPrepare()
    }

    /**
     * Called from the player callback on the service main thread.
     */
    fun onPlaybackStateChanged(isPlaying: Boolean, positionMs: Long) {
        if (released) return
        this.positionMs = positionMs.coerceAtLeast(0L)
        if (this.isPlaying == isPlaying) return
        this.isPlaying = isPlaying
        if (!isPlaying) {
            invalidateAudioClock()
        }
    }

    /**
     * Called for seeks and other timeline discontinuities so no stale cue or
     * loop can survive across the jump.
     */
    fun onPositionDiscontinuity(positionMs: Long) {
        if (released) return
        this.positionMs = positionMs.coerceAtLeast(0L)
        invalidateAudioClock()
    }

    /**
     * Advances the renderer-owned scheduler using ExoPlayer's position.
     *
     * @return true once hitsound audio is ready and frames need the tight
     * playback interval; false lets the service idle between preparation
     * attempts without a 100 Hz wake-up.
     */
    fun updateAudio(positionMs: Long): Boolean {
        this.positionMs = positionMs.coerceAtLeast(0L)
        if (released || !configuration.enabled || !isPlaying) return false
        val prepared = activeRender ?: return false
        prepared.updateAudio(
            frame = RenderAudioFrame(
                mapTimeMs = this.positionMs.toDouble(),
                clockEpoch = clockEpoch,
                isPlaying = true,
                latencyMs = configuration.latencyMs
            ),
            sink = sink
        )
        return true
    }

    fun release() {
        if (released) return
        released = true
        difficultyJob?.cancel()
        difficultyJob = null
        val prepared = activeRender
        activeRender = null
        sink.stopAll()

        scope.launch(Dispatchers.IO) {
            lifecycleMutex.withLock {
                prepared?.close()
            }
            sink.release()
            scope.cancel()
        }
    }

    private fun applyConfiguration(next: HitsoundConfiguration) {
        if (released) return
        val previous = configuration
        val wasReady = configurationReady
        configuration = next
        configurationReady = true

        if (!wasReady || previous.volume != next.volume) {
            sink.setMasterVolume(next.volume)
        }
        if (!wasReady) {
            requestPrepare()
            return
        }

        if (previous.latencyMs != next.latencyMs) {
            invalidateAudioClock()
        }
        if (previous.enabled != next.enabled) {
            invalidateAudioClock()
            if (next.enabled && activeRender == null) {
                requestPrepare()
            }
        }
        if (previous.skinSelection != next.skinSelection) {
            retireActiveRender()
            invalidateAudioClock()
            requestPrepare()
        }
    }

    private fun requestPrepare() {
        if (
            released ||
            !configurationReady ||
            !configuration.enabled ||
            mediaId == null
        ) {
            return
        }

        val request = ++prepareRequest
        scope.launch {
            try {
                prepare(request)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Hitsound renderer preparation failed", error)
            }
        }
    }

    private suspend fun prepare(request: Long) {
        val targetMediaId = mediaId ?: return
        if (!isCurrentRequest(request, targetMediaId)) return

        lifecycleMutex.withLock {
            if (!isCurrentRequest(request, targetMediaId)) return

            val loadedCatalog = catalog
                ?.takeIf { it.beatmapSetId != 0L }
                ?: withContext(Dispatchers.IO) {
                    catalogLoader.loadCatalog(targetMediaId)
                }
            if (!isCurrentRequest(request, targetMediaId)) return
            val targetCatalog = loadedCatalog ?: return
            catalog = targetCatalog

            val savedDifficulty = settingsManager.getSelectedDifficulty(
                targetCatalog.selectionKey
            )
            val targetDifficultyKey = targetCatalog.resolveSavedDifficulty(savedDifficulty)
            selectedDifficultyKey = targetDifficultyKey
            observeSelectedDifficulty(targetMediaId, targetCatalog)

            val difficulty = targetCatalog.difficulty(targetDifficultyKey) ?: return
            val targetSkin = configuration.skinSelection
            val prepared = registry.prepare(appContext, difficulty, targetSkin) ?: return
            var published = false
            try {
                if (!isCurrentRequest(request, targetMediaId, targetSkin, targetDifficultyKey)) {
                    return
                }
                withContext(Dispatchers.IO) {
                    sink.setSkinSelection(targetSkin)
                    prepared.preloadAudio(sink)
                }
                if (!isCurrentRequest(request, targetMediaId, targetSkin, targetDifficultyKey)) {
                    return
                }
                activeRender = prepared
                published = true
                sink.setMasterVolume(configuration.volume)
            } finally {
                if (!published) {
                    prepared.close()
                }
            }
        }
    }

    private fun observeSelectedDifficulty(
        targetMediaId: Long,
        targetCatalog: RenderCatalog
    ) {
        val observationKey = "$targetMediaId|${targetCatalog.selectionKey}"
        if (observedDifficultyKey == observationKey) return

        observedDifficultyKey = observationKey
        difficultyJob?.cancel()
        difficultyJob = scope.launch {
            settingsManager.selectedDifficulty(targetCatalog.selectionKey)
                .distinctUntilChanged()
                .collect { savedDifficulty ->
                    if (
                        released ||
                        mediaId != targetMediaId ||
                        catalog !== targetCatalog
                    ) {
                        return@collect
                    }
                    val resolved = targetCatalog.resolveSavedDifficulty(savedDifficulty)
                    if (resolved == selectedDifficultyKey) return@collect
                    selectedDifficultyKey = resolved
                    retireActiveRender()
                    invalidateAudioClock()
                    requestPrepare()
                }
        }
    }

    private fun isCurrentRequest(
        request: Long,
        targetMediaId: Long,
        targetSkin: RenderSkinSelection = configuration.skinSelection,
        targetDifficultyKey: RenderDifficultyKey? = selectedDifficultyKey
    ): Boolean {
        return !released &&
            request == prepareRequest &&
            configuration.enabled &&
            mediaId == targetMediaId &&
            configuration.skinSelection == targetSkin &&
            selectedDifficultyKey == targetDifficultyKey
    }

    private fun retireActiveRender() {
        val prepared = activeRender ?: return
        activeRender = null
        // Enter the mutex before returning to the caller. Any prepare request
        // created immediately afterwards therefore queues behind this close,
        // and the retiring scheduler cannot stop the new render's first cues.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            lifecycleMutex.withLock {
                withContext(Dispatchers.IO) {
                    prepared.close()
                }
            }
        }
    }

    private fun invalidateAudioClock() {
        clockEpoch++
        sink.stopAll()
    }

    private fun resolveSkinSelection(
        skins: List<CustomSkinRecord>,
        activeId: String?
    ): RenderSkinSelection {
        val id = activeId ?: return RenderSkinSelection.Bundled
        val record = skins.firstOrNull { it.id == id } ?: return RenderSkinSelection.Bundled
        val directory = customSkinLibrary.directoryFor(record.id)
        if (!directory.isDirectory) return RenderSkinSelection.Bundled
        return RenderSkinSelection(
            id = record.id,
            name = record.name,
            directory = directory
        )
    }

    private data class HitsoundConfiguration(
        val enabled: Boolean = false,
        val volume: Int = 100,
        val latencyMs: Int = 0,
        val skinSelection: RenderSkinSelection = RenderSkinSelection.Bundled
    )

    private companion object {
        const val TAG = "HitsoundCoordinator"
    }
}
