package com.mosu.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

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

    private val _playbackMod = MutableStateFlow(PlaybackMod.NONE)
    val playbackMod = _playbackMod.asStateFlow()

    init {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            val controller = controllerFuture.get()
            controller.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    _nowPlaying.value = mediaItem?.mediaMetadata
                    _duration.value = controller.duration.coerceAtLeast(0L)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
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
                }
            })
            // Initialize state
            _nowPlaying.value = controller.currentMediaItem?.mediaMetadata
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
                    _currentPosition.value = controller.currentPosition
                }
                delay(300) // Update 2 times per second to reduce graphics load
            }
        }
    }

    fun playSong(selectedBeatmap: BeatmapEntity, playlist: List<BeatmapEntity> = listOf(selectedBeatmap)) {
        val controller = this.controller ?: return
        
        // Convert playlist to MediaItems
        // Group by beatmapSetId to identify songs that are part of beatmapsets vs standalone
        val beatmapSetsInPlaylist = playlist.groupBy { it.beatmapSetId }

        val mediaItems = playlist.map { beatmap ->
            val file = File(beatmap.audioPath)

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
                .setArtworkUri(Uri.fromFile(File(beatmap.coverPath)))
                .build()

            MediaItem.Builder()
                .setUri(Uri.fromFile(file))
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
            controller.pause()
        } else {
            controller.play()
        }
    }
    
    fun pause() {
        controller?.pause()
    }
    
    fun skipToNext() {
        controller?.seekToNext()
    }

    fun skipToPrevious() {
        controller?.seekToPrevious()
    }
    
    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _currentPosition.value = positionMs
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
        controller.playbackParameters = PlaybackParameters(mod.speed, mod.pitch)
    }

    fun release() {
        MediaController.releaseFuture(controllerFuture)
    }
}

enum class PlaybackMod(val speed: Float, val pitch: Float, val label: String) {
    NONE(1f, 1f, "No Mod"),
    DOUBLE_TIME(1.5f, 1f, "DT"),
    NIGHT_CORE(1.5f, 1.5f, "NC")
}
