package com.mosu.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.mosu.app.data.db.BeatmapEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class MusicController(context: Context) {
    
    private var controllerFuture: ListenableFuture<MediaController>
    private val controller: MediaController?
        get() = if (controllerFuture.isDone) controllerFuture.get() else null

    private val _nowPlaying = MutableStateFlow<MediaMetadata?>(null)
    val nowPlaying = _nowPlaying.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    init {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            val controller = controllerFuture.get()
            controller.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    _nowPlaying.value = mediaItem?.mediaMetadata
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }
            })
            // Initialize state
            _nowPlaying.value = controller.currentMediaItem?.mediaMetadata
            _isPlaying.value = controller.isPlaying
        }, MoreExecutors.directExecutor())
    }

    fun playSong(beatmap: BeatmapEntity) {
        val controller = this.controller ?: return
        
        val file = File(beatmap.audioPath)
        if (!file.exists()) return

        val metadata = MediaMetadata.Builder()
            .setTitle(beatmap.title)
            .setArtist(beatmap.artist)
            .setArtworkUri(Uri.fromFile(File(beatmap.coverPath)))
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setMediaId(beatmap.uid.toString())
            .setMediaMetadata(metadata)
            .build()

        controller.setMediaItem(mediaItem)
        controller.prepare()
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

    fun release() {
        MediaController.releaseFuture(controllerFuture)
    }
}

