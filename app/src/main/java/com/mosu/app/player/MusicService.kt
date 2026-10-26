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

class MusicService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var noisyAudioReceiverRegistered = false

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
}

internal object PlaybackPositionProtocol {
    const val ACTION = "com.mosu.app.PLAYBACK_POSITION"
    const val KEY_POSITION_MS = "positionMs"
    const val KEY_IS_PLAYING = "isPlaying"
    const val KEY_SPEED = "speed"
    const val KEY_MEDIA_ID = "mediaId"

    val COMMAND = SessionCommand(ACTION, Bundle.EMPTY)
}
