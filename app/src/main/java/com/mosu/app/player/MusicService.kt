package com.mosu.app.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mosu.app.data.SettingsManager
import com.mosu.app.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MusicService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var hitsoundCoordinator: HitsoundPlaybackCoordinator? = null
    private var noisyAudioReceiverRegistered = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var hitsoundLoopJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            hitsoundCoordinator?.onMediaItemChanged(mediaItem?.mediaId?.toLongOrNull())
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            hitsoundCoordinator?.onPlaybackStateChanged(
                isPlaying = isPlaying,
                positionMs = player?.currentPosition ?: 0L
            )
            if (isPlaying) {
                startHitsoundLoop()
            } else {
                stopHitsoundLoop()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            hitsoundCoordinator?.onPositionDiscontinuity(newPosition.positionMs)
        }
    }

    private val sessionCallback = object : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val commands = SessionCommands.Builder()
                .add(SessionCommand.COMMAND_CODE_SESSION_SET_RATING)
                .add(PlaybackPositionProtocol.COMMAND)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != PlaybackPositionProtocol.ACTION) {
                return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
                )
            }

            val currentPlayer = player
                ?: return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE)
                )
            val extras = Bundle().apply {
                putLong(PlaybackPositionProtocol.KEY_POSITION_MS, currentPlayer.currentPosition)
                putBoolean(PlaybackPositionProtocol.KEY_IS_PLAYING, currentPlayer.isPlaying)
                putFloat(
                    PlaybackPositionProtocol.KEY_SPEED,
                    currentPlayer.playbackParameters.speed
                )
                putString(
                    PlaybackPositionProtocol.KEY_MEDIA_ID,
                    currentPlayer.currentMediaItem?.mediaId
                )
            }
            return Futures.immediateFuture(
                SessionResult(SessionResult.RESULT_SUCCESS, extras)
            )
        }
    }

    private val noisyAudioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                player?.pause()
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        
        // 1. Initialize Player
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            addListener(playerListener)
        }
        val currentPlayer = player!!
        hitsoundCoordinator = HitsoundPlaybackCoordinator(
            context = this,
            db = AppDatabase.getDatabase(this),
            settingsManager = SettingsManager(this)
        ).also { coordinator ->
            coordinator.onMediaItemChanged(currentPlayer.currentMediaItem?.mediaId?.toLongOrNull())
            coordinator.onPlaybackStateChanged(
                isPlaying = currentPlayer.isPlaying,
                positionMs = currentPlayer.currentPosition
            )
            if (currentPlayer.isPlaying) {
                startHitsoundLoop()
            }
        }
        registerNoisyAudioReceiver()
        
        // 2. Initialize MediaSession
        mediaSession = MediaSession.Builder(this, player!!)
            .setCallback(sessionCallback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        stopHitsoundLoop()
        serviceScope.cancel()
        hitsoundCoordinator?.release()
        hitsoundCoordinator = null
        unregisterNoisyAudioReceiver()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = player
        if (player != null && !player.playWhenReady) {
            stopSelf()
        }
    }

    private fun registerNoisyAudioReceiver() {
        if (noisyAudioReceiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            noisyAudioReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        noisyAudioReceiverRegistered = true
    }

    private fun unregisterNoisyAudioReceiver() {
        if (!noisyAudioReceiverRegistered) return
        unregisterReceiver(noisyAudioReceiver)
        noisyAudioReceiverRegistered = false
    }

    private fun startHitsoundLoop() {
        if (hitsoundLoopJob?.isActive == true) return
        hitsoundLoopJob = serviceScope.launch {
            while (isActive) {
                val currentPlayer = player ?: break
                if (!currentPlayer.isPlaying) break
                val audioReady = runCatching {
                    hitsoundCoordinator?.updateAudio(currentPlayer.currentPosition) == true
                }.getOrElse { error ->
                    android.util.Log.w(
                        "MusicService",
                        "Hitsound frame update failed",
                        error
                    )
                    false
                }
                delay(
                    if (audioReady) {
                        HITSOUND_FRAME_INTERVAL_MS
                    } else {
                        HITSOUND_IDLE_INTERVAL_MS
                    }
                )
            }
        }
    }

    private fun stopHitsoundLoop() {
        hitsoundLoopJob?.cancel()
        hitsoundLoopJob = null
    }

    private companion object {
        // Finer than the old 60 Hz Compose frame loop, but far below the
        // previously proposed 500 Hz. Tune this independently after profiling.
        const val HITSOUND_FRAME_INTERVAL_MS = 10L
        const val HITSOUND_IDLE_INTERVAL_MS = 100L
    }
}

internal object PlaybackPositionProtocol {
    const val ACTION = "com.mosu.app.PLAYBACK_POSITION"
    const val KEY_POSITION_MS = "positionMs"
    const val KEY_IS_PLAYING = "isPlaying"
    const val KEY_SPEED = "speed"
    const val KEY_MEDIA_ID = "mediaId"

    val COMMAND = SessionCommand(ACTION, Bundle.EMPTY)
}
