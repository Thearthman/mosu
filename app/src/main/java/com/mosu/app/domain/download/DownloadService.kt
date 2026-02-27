package com.mosu.app.domain.download

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mosu.app.MainActivity
import com.mosu.app.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isStarted = false

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID = 2
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isStarted) {
            startForegroundService()
            isStarted = true
            observeTasks()
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = createNotification("Initializing downloads...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(message: String, progress: Int = -1): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.mosu_icon_foreground)
            .setContentTitle("Downloading Beatmaps")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)

        if (progress >= 0) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun updateNotification(message: String, progress: Int = -1) {
        val notification = createNotification(message, progress)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun observeTasks() {
        scope.launch {
            DownloadManager.getInstanceOnly()?.tasks?.collectLatest { tasks ->
                val activeTasks = tasks.values.filter { 
                    it.status == DownloadStatus.Downloading || 
                    it.status == DownloadStatus.Queued || 
                    it.status == DownloadStatus.Extracting 
                }

                if (activeTasks.isEmpty()) {
                    isStarted = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collectLatest
                }

                if (activeTasks.size == 1) {
                    val task = activeTasks.first()
                    updateNotification("${task.title} - ${task.statusMessage}", task.progress)
                } else {
                    val progress = if (activeTasks.isNotEmpty()) {
                        activeTasks.sumOf { it.progress } / activeTasks.size
                    } else 0
                    updateNotification("Downloading ${activeTasks.size} beatmaps", progress)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
