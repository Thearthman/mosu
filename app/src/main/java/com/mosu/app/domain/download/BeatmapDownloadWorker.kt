package com.mosu.app.domain.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.mosu.app.MainActivity
import com.mosu.app.R
import com.mosu.app.data.SettingsManager
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.repository.OsuRepository
import kotlinx.coroutines.flow.collect

class BeatmapDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ensureNotificationChannel()

        val setId = inputData.getLong(KEY_SET_ID, -1L)
        if (setId <= 0L) return Result.failure()

        val db = AppDatabase.getDatabase(applicationContext)
        val taskDao = db.downloadTaskDao()
        val task = taskDao.getTask(setId) ?: return Result.failure()

        setForeground(createForegroundInfo(task.title, task.statusMessage, task.progress))

        val settingsManager = SettingsManager(applicationContext)
        val downloader = BeatmapDownloader(applicationContext, settingsManager)
        val extractor = ZipExtractor(applicationContext)
        val coverDownloadService = CoverDownloadService(applicationContext)
        val repository = OsuRepository(db.searchCacheDao())
        val downloadService = BeatmapDownloadService(
            context = applicationContext,
            downloader = downloader,
            extractor = extractor,
            coverDownloadService = coverDownloadService,
            db = db,
            repository = repository
        )

        return try {
            taskDao.updateTaskState(
                setId = setId,
                status = DownloadStatus.Downloading.name,
                progress = task.progress,
                statusMessage = task.statusMessage.ifBlank { "Starting..." },
                errorMessage = null
            )

            var failedMessage: String? = null
            downloadService.downloadBeatmap(
                beatmapSetId = setId,
                accessToken = task.accessToken,
                title = task.title,
                artist = task.artist,
                creator = task.creator,
                genreId = task.genreId,
                coversListUrl = task.coverUrl
            ).collect { state ->
                when (state) {
                    is UnifiedDownloadState.Progress -> {
                        val status = if (state.status.contains("Extracting", ignoreCase = true) ||
                            state.status.contains("Registering", ignoreCase = true)
                        ) {
                            DownloadStatus.Extracting
                        } else {
                            DownloadStatus.Downloading
                        }
                        taskDao.updateTaskState(
                            setId = setId,
                            status = status.name,
                            progress = state.progress,
                            statusMessage = state.status,
                            errorMessage = null
                        )
                        setForeground(createForegroundInfo(task.title, state.status, state.progress))
                    }
                    is UnifiedDownloadState.Notification -> {
                        val current = taskDao.getTask(setId)
                        taskDao.updateTaskState(
                            setId = setId,
                            status = current?.status ?: DownloadStatus.Downloading.name,
                            progress = current?.progress ?: 0,
                            statusMessage = state.message,
                            errorMessage = null
                        )
                    }
                    is UnifiedDownloadState.Success -> {
                        taskDao.updateTaskState(
                            setId = setId,
                            status = DownloadStatus.Success.name,
                            progress = 100,
                            statusMessage = "Done",
                            errorMessage = null
                        )
                    }
                    is UnifiedDownloadState.Error -> {
                        failedMessage = state.message
                        taskDao.updateTaskState(
                            setId = setId,
                            status = DownloadStatus.Error.name,
                            progress = taskDao.getTask(setId)?.progress ?: 0,
                            statusMessage = "Failed",
                            errorMessage = state.message
                        )
                    }
                }
            }

            if (failedMessage == null) Result.success() else Result.failure()
        } catch (e: Exception) {
            taskDao.updateTaskState(
                setId = setId,
                status = DownloadStatus.Error.name,
                progress = taskDao.getTask(setId)?.progress ?: 0,
                statusMessage = "Failed",
                errorMessage = e.message ?: "Unknown error"
            )
            Result.failure()
        }
    }

    private fun createForegroundInfo(title: String, message: String, progress: Int): ForegroundInfo {
        val notification = createNotification(title, message, progress)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(title: String, message: String, progress: Int): Notification {
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.mosu_icon_foreground)
            .setContentTitle(title.ifBlank { "Downloading Beatmap" })
            .setContentText(message.ifBlank { "Downloading..." })
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)

        if (progress in 0..100) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val KEY_SET_ID = "set_id"
        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID = 2
    }
}
