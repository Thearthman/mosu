package com.mosu.app.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.data.SettingsManager
import com.mosu.app.utils.MediaPathUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

class MusicController(
    context: Context,
    private val settingsManager: SettingsManager
) {
    
    private var controllerFuture: ListenableFuture<MediaController>
    private val controller: MediaController?
        get() = if (controllerFuture.isDone) controllerFuture.get() else null

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _nowPlaying = MutableStateFlow<MediaMetadata?>(null)
    val nowPlaying = _nowPlaying.asStateFlow()

    private val _currentMediaId = MutableStateFlow<Long?>(null)
    val currentMediaId = _currentMediaId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled = _shuffleModeEnabled.asStateFlow()

    private val _playbackMod = PlaybackModSession.selection
    val playbackMod = _playbackMod.asStateFlow()

    private var positionSamplingJob: Job? = null
    private var positionRequestInFlight = false
    private var positionRequestToken = 0L
    private var playbackClockEpoch = 0L
    private var playbackClockAnchor: PlaybackClockAnchor? = null
    private var lastPositionSample: PositionSample? = null
    private var awaitingClockAdvance = false

    init {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            val controller = controllerFuture.get()
            controller.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    _nowPlaying.value = mediaItem?.mediaMetadata
                    _currentMediaId.value = mediaItem?.mediaId?.toLongOrNull()
                    playbackClockEpoch++
                    playbackClockAnchor = null
                    lastPositionSample = null
                    awaitingClockAdvance = true
                    _currentPosition.value = 0L
                    _duration.value = controller.duration.coerceAtLeast(0L)
                    requestPositionSampleIfActive()
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    val positionMs = newPosition.positionMs.coerceAtLeast(0L)
                    playbackClockEpoch++
                    lastPositionSample = null
                    awaitingClockAdvance = true
                    playbackClockAnchor = PlaybackClockAnchor(
                        positionMs = positionMs.toDouble(),
                        atElapsedRealtimeMs = SystemClock.elapsedRealtime().toDouble(),
                        speed = controller.playbackParameters.speed,
                        isPlaying = false
                    )
                    _currentPosition.value = positionMs
                    requestPositionSampleIfActive()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playbackClockAnchor = PlaybackClockAnchor(
                        positionMs = currentPositionMs().toDouble(),
                        atElapsedRealtimeMs = SystemClock.elapsedRealtime().toDouble(),
                        speed = controller.playbackParameters.speed,
                        isPlaying = isPlaying && !awaitingClockAdvance
                    )
                    _isPlaying.value = isPlaying
                    requestPositionSampleIfActive()
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _repeatMode.value = repeatMode
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _shuffleModeEnabled.value = shuffleModeEnabled
                }
                
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        _duration.value = controller.duration.coerceAtLeast(0L)
                    }
                    requestPositionSampleIfActive()
                }
            })
            // Initialize state
            _nowPlaying.value = controller.currentMediaItem?.mediaMetadata
            _currentMediaId.value = controller.currentMediaItem?.mediaId?.toLongOrNull()
            _isPlaying.value = controller.isPlaying
            _repeatMode.value = controller.repeatMode
            _shuffleModeEnabled.value = controller.shuffleModeEnabled
            _duration.value = controller.duration.coerceAtLeast(0L)
            applyPlaybackMod(_playbackMod.value)

            // Restore saved play mode settings
            scope.launch {
                settingsManager.shuffleModeEnabled.collect { enabled ->
                    controller.shuffleModeEnabled = enabled
                }
            }
            scope.launch {
                settingsManager.repeatMode.collect { mode ->
                    controller.repeatMode = mode
                }
            }
            
            startProgressUpdater()
        }, MoreExecutors.directExecutor())
    }

    private fun startProgressUpdater() {
        scope.launch {
            while (isActive) {
                val controller = this@MusicController.controller
                if (controller != null) {
                    _currentPosition.value = currentPositionMs()
                }
                delay(300) // changed back to 300ms for smoother look
            }
        }
    }

    /**
     * Starts polling the player-side ExoPlayer clock while the renderer is visible.
     *
     * MediaController.currentPosition is only an estimate refreshed by the session every
     * three seconds. Polling it from the frame loop therefore stalls and then jumps. The
     * custom command reads ExoPlayer.currentPosition in the service, where the audio clock
     * actually lives, and returns the service-side completion timestamp with it.
     */
    fun startPositionSampling() {
        if (positionSamplingJob?.isActive == true) return
        playbackClockEpoch++
        positionRequestInFlight = false
        positionRequestToken++
        lastPositionSample = null
        awaitingClockAdvance = false
        positionSamplingJob = scope.launch {
            while (isActive) {
                requestPositionSample()
                delay(POSITION_SAMPLE_INTERVAL_MS)
            }
        }
    }

    fun stopPositionSampling() {
        positionSamplingJob?.cancel()
        positionSamplingJob = null
        positionRequestInFlight = false
        positionRequestToken++
        playbackClockEpoch++
        lastPositionSample = null
        awaitingClockAdvance = false
    }

    private fun requestPositionSampleIfActive() {
        if (positionSamplingJob?.isActive == true) {
            requestPositionSample()
        }
    }

    private fun requestPositionSample() {
        val controller = this.controller ?: return
        if (positionRequestInFlight) return

        val mediaId = _currentMediaId.value ?: return
        val epoch = playbackClockEpoch
        val token = ++positionRequestToken
        positionRequestInFlight = true

        val future = runCatching {
            controller.sendCustomCommand(PlaybackPositionProtocol.COMMAND, Bundle.EMPTY)
        }.getOrElse {
            positionRequestInFlight = false
            return
        }

        future.addListener({
            val result = runCatching { future.get() }.getOrNull()
            scope.launch {
                if (token == positionRequestToken) {
                    positionRequestInFlight = false
                }
                if (result != null) {
                    applyPositionSample(result, epoch, mediaId)
                }
            }
        }, MoreExecutors.directExecutor())
    }

    private fun applyPositionSample(
        result: SessionResult,
        requestedEpoch: Long,
        requestedMediaId: Long
    ) {
        if (
            result.resultCode != SessionResult.RESULT_SUCCESS ||
            requestedEpoch != playbackClockEpoch ||
            requestedMediaId != _currentMediaId.value
        ) {
            return
        }

        val sampleMediaId = result.extras
            .getString(PlaybackPositionProtocol.KEY_MEDIA_ID)
            ?.toLongOrNull()
        if (sampleMediaId != requestedMediaId) return

        val samplePositionMs = result.extras
            .getLong(PlaybackPositionProtocol.KEY_POSITION_MS)
            .coerceAtLeast(0L)
        val sampleSpeed = result.extras
            .getFloat(PlaybackPositionProtocol.KEY_SPEED, 1f)
            .coerceAtLeast(0f)
        val previousSample = lastPositionSample
        val advancedSincePreviousSample = previousSample == null ||
            previousSample.hasAdvancedTo(samplePositionMs, sampleSpeed, result.completionTimeMs)
        // After a seek or while buffering, Media3 can report "playing" while
        // ExoPlayer.currentPosition is still stationary. Do not extrapolate through
        // that gap: the next real sample would pull the preview backward.
        if (awaitingClockAdvance && previousSample != null && advancedSincePreviousSample) {
            awaitingClockAdvance = false
        }
        lastPositionSample = PositionSample(samplePositionMs, result.completionTimeMs)

        playbackClockAnchor = PlaybackClockAnchor(
            positionMs = samplePositionMs.toDouble(),
            atElapsedRealtimeMs = result.completionTimeMs.toDouble(),
            speed = sampleSpeed,
            isPlaying = result.extras.getBoolean(PlaybackPositionProtocol.KEY_IS_PLAYING) &&
                !awaitingClockAdvance &&
                advancedSincePreviousSample
        )
    }

    fun playSong(selectedBeatmap: BeatmapEntity, playlist: List<BeatmapEntity> = listOf(selectedBeatmap)) {
        val controller = this.controller ?: return
        
        // Convert playlist to MediaItems
        // Group by beatmapSetId to identify songs that are part of beatmapsets vs standalone
        val beatmapSetsInPlaylist = playlist.groupBy { it.beatmapSetId }

        val mediaItems = playlist.mapNotNull { beatmap ->
            val audioUri = MediaPathUtils.asUri(beatmap.audioPath) ?: return@mapNotNull null
            val artworkUri = MediaPathUtils.asUri(beatmap.coverPath)
            // Check if this beatmap is part of a multi-difficulty beatmapset in the current playlist
            val isPartOfBeatmapset = beatmapSetsInPlaylist[beatmap.beatmapSetId]?.size ?: 0 > 1

            val (displayTitle, displayArtist) = if (isPartOfBeatmapset) {
                // Song is part of a beatmapset: show difficulty as title, beatmapset title as artist
                beatmap.difficultyName to beatmap.title
            } else {
                // Standalone song: use normal title/artist
                beatmap.title to beatmap.artist
            }

            val metadata = MediaMetadata.Builder()
                .setTitle(displayTitle)
                .setArtist(displayArtist)
                .apply {
                    artworkUri?.let { setArtworkUri(it) }
                }
                .build()

            MediaItem.Builder()
                .setUri(audioUri)
                .setMediaId(beatmap.uid.toString())
                .setMediaMetadata(metadata)
                .build()
        }

        // Find the index of the selected song
        val startIndex = mediaItems.indexOfFirst { it.mediaId == selectedBeatmap.uid.toString() }.coerceAtLeast(0)

        controller.setMediaItems(mediaItems, startIndex, 0)
        controller.prepare()
        applyPlaybackMod(_playbackMod.value)
        controller.play()
    }
    
    fun togglePlayPause() {
        val controller = this.controller ?: return
        if (controller.isPlaying) {
            pause()
        } else {
            playbackClockAnchor = PlaybackClockAnchor(
                positionMs = currentPositionMs().toDouble(),
                atElapsedRealtimeMs = SystemClock.elapsedRealtime().toDouble(),
                speed = controller.playbackParameters.speed,
                isPlaying = !awaitingClockAdvance
            )
            controller.play()
            requestPositionSampleIfActive()
        }
    }
    
    fun pause() {
        val controller = this.controller ?: return
        playbackClockAnchor = PlaybackClockAnchor(
            positionMs = currentPositionMs().toDouble(),
            atElapsedRealtimeMs = SystemClock.elapsedRealtime().toDouble(),
            speed = controller.playbackParameters.speed,
            isPlaying = false
        )
        controller.pause()
        requestPositionSampleIfActive()
    }
    
    fun skipToNext() {
        controller?.seekToNext()
    }

    fun skipToPrevious() {
        controller?.seekToPrevious()
    }
    
    fun seekTo(positionMs: Long) {
        val targetMs = positionMs.coerceAtLeast(0L)
        val controller = this.controller ?: return
        playbackClockEpoch++
        lastPositionSample = null
        awaitingClockAdvance = true
        playbackClockAnchor = PlaybackClockAnchor(
            positionMs = targetMs.toDouble(),
            atElapsedRealtimeMs = SystemClock.elapsedRealtime().toDouble(),
            speed = controller.playbackParameters.speed,
            isPlaying = false
        )
        _currentPosition.value = targetMs
        controller.seekTo(targetMs)
        requestPositionSampleIfActive()
    }

    /**
     * Playback position for frame-driven views.
     *
     * Between service-side samples this advances from the most recent real ExoPlayer
     * position using the monotonic clock. Each new sample rebases the projection, so
     * seeks, buffering, pause/resume, and playback-speed changes are reflected without
     * a delayed correction.
     */
    fun currentPositionMs(): Long {
        val anchor = playbackClockAnchor
            ?: return controller?.currentPosition?.coerceAtLeast(0L)
                ?: _currentPosition.value
        val elapsedMs = (
            SystemClock.elapsedRealtimeNanos() / NANOS_PER_MILLISECOND -
                anchor.atElapsedRealtimeMs
            )
        val positionMs = anchor.positionMs +
            if (anchor.isPlaying) elapsedMs * anchor.speed else 0.0
        return positionMs.roundToLong().coerceAtLeast(0L)
    }

    fun toggleShuffleMode() {
        val controller = this.controller ?: return
        val newEnabled = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = newEnabled
        scope.launch {
            settingsManager.saveShuffleModeEnabled(newEnabled)
        }
    }

    fun setShuffleMode(enabled: Boolean) {
        val controller = this.controller ?: return
        controller.shuffleModeEnabled = enabled
        scope.launch {
            settingsManager.saveShuffleModeEnabled(enabled)
        }
    }
    
    fun toggleRepeatMode() {
        val controller = this.controller ?: return
        val newMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = newMode
        scope.launch {
            settingsManager.saveRepeatMode(newMode)
        }
    }

    fun setRepeatMode(mode: Int) {
        val controller = this.controller ?: return
        controller.repeatMode = mode
        scope.launch {
            settingsManager.saveRepeatMode(mode)
        }
    }

    fun setPlaybackMod(mod: PlaybackMod) {
        _playbackMod.value = mod
        applyPlaybackMod(mod)
    }

    private fun applyPlaybackMod(mod: PlaybackMod) {
        val controller = this.controller ?: return
        playbackClockAnchor = PlaybackClockAnchor(
            positionMs = currentPositionMs().toDouble(),
            atElapsedRealtimeMs = SystemClock.elapsedRealtime().toDouble(),
            speed = mod.speed,
            isPlaying = false
        )
        lastPositionSample = null
        awaitingClockAdvance = true
        controller.playbackParameters = PlaybackParameters(mod.speed, mod.pitch)
        requestPositionSampleIfActive()
    }

    fun release() {
        stopPositionSampling()
        MediaController.releaseFuture(controllerFuture)
    }

    private companion object {
        const val POSITION_SAMPLE_INTERVAL_MS = 50L
        const val NANOS_PER_MILLISECOND = 1_000_000.0
    }

    private data class PlaybackClockAnchor(
        val positionMs: Double,
        val atElapsedRealtimeMs: Double,
        val speed: Float,
        val isPlaying: Boolean
    )

    private data class PositionSample(
        val positionMs: Long,
        val completionTimeMs: Long
    ) {
        fun hasAdvancedTo(
            nextPositionMs: Long,
            speed: Float,
            nextCompletionTimeMs: Long
        ): Boolean = hasPlaybackClockAdvanced(
            previousPositionMs = positionMs,
            previousCompletionTimeMs = completionTimeMs,
            nextPositionMs = nextPositionMs,
            nextCompletionTimeMs = nextCompletionTimeMs,
            speed = speed
        )
    }
}

internal fun hasPlaybackClockAdvanced(
    previousPositionMs: Long,
    previousCompletionTimeMs: Long,
    nextPositionMs: Long,
    nextCompletionTimeMs: Long,
    speed: Float
): Boolean {
    val elapsedMs = nextCompletionTimeMs - previousCompletionTimeMs
    if (elapsedMs <= 0L) return false
    val minimumProgressMs = maxOf(
        MIN_CLOCK_PROGRESS_MS,
        elapsedMs * speed * MIN_PROGRESS_FRACTION
    )
    return (nextPositionMs - previousPositionMs).toDouble() >= minimumProgressMs
}

private const val MIN_CLOCK_PROGRESS_MS = 4.0
private const val MIN_PROGRESS_FRACTION = 0.25

/**
 * Process-lifetime playback-mod selection. It intentionally is not persisted to
 * disk: reopening the player within the same app session keeps DT/NC selected,
 * while a fresh process starts at No Mod.
 */
private object PlaybackModSession {
    val selection = MutableStateFlow(PlaybackMod.NONE)
}

enum class PlaybackMod(val speed: Float, val pitch: Float, val label: String) {
    NONE(1f, 1f, "No Mod"),
    DOUBLE_TIME(1.5f, 1f, "DT"),
    NIGHT_CORE(1.5f, 1.5f, "NC")
}
