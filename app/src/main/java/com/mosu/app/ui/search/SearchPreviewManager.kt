package com.mosu.app.ui.search

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.mosu.app.data.api.model.BeatmapsetCompact
import com.mosu.app.player.MusicController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SearchPreviewManager(
    private val context: Context,
    private val musicController: MusicController
) {
    private var exoPlayer: ExoPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null
    
    var previewingId by mutableStateOf<Long?>(null)
        private set
        
    var progress by mutableFloatStateOf(0f)
        private set

    private fun getPlayer(): ExoPlayer {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            stop()
                        }
                    }
                    
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        stop()
                    }
                })
            }
        }
        return exoPlayer!!
    }

    fun playPreview(beatmapset: BeatmapsetCompact, preferredMirror: String) {
        // Stop current preview if any
        stop()
        
        // Pause global music
        musicController.pause()
        
        val url = if (preferredMirror.lowercase() == "sayobot") {
            "https://a.sayobot.cn/preview/${beatmapset.id}.mp3"
        } else {
            "https://b.ppy.sh/preview/${beatmapset.id}.mp3"
        }
        
        previewingId = beatmapset.id
        progress = 0f
        
        val player = getPlayer()
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
        
        startProgressPolling()
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val player = exoPlayer
                if (player != null && player.isPlaying) {
                    val duration = player.duration
                    if (duration > 0) {
                        progress = player.currentPosition.toFloat() / duration
                    }
                }
                delay(50)
            }
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        exoPlayer?.stop()
        previewingId = null
        progress = 0f
    }

    fun release() {
        stop()
        exoPlayer?.release()
        exoPlayer = null
    }
}
