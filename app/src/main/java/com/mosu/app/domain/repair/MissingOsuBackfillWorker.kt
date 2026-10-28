package com.mosu.app.domain.repair

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
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.mosu.app.MainActivity
import com.mosu.app.R
import com.mosu.app.data.SettingsManager
import com.mosu.app.data.db.AppDatabase
import org.json.JSONObject
import kotlinx.coroutines.CancellationException

class MissingOsuBackfillWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val stateStore = MissingOsuBackfillStateStore(applicationContext)
        var lastPublishedProgress: MissingOsuBackfillProgress? = null
        return try {
            ensureNotificationChannel()
            setForeground(createForegroundInfo(null))

            val service = MissingOsuBackfillService(
                context = applicationContext,
                db = AppDatabase.getDatabase(applicationContext),
                settingsManager = SettingsManager(applicationContext)
            )
            val result = service.repairAll { progress ->
                if (progress != lastPublishedProgress) {
                    lastPublishedProgress = progress
                    setProgress(MissingOsuBackfillWorkData.progressToData(progress))
                    setForeground(createForegroundInfo(progress))
                }
            }
            stateStore.saveOutcome(
                runState = MissingOsuBackfillRunState.Completed,
                result = result,
                errorMessage = null
            )
            Result.success(MissingOsuBackfillWorkData.resultToData(result))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message
                ?.takeIf { it.isNotBlank() }
                ?: e::class.java.simpleName
            stateStore.saveOutcome(
                runState = MissingOsuBackfillRunState.Failed,
                result = null,
                errorMessage = message
            )
            Result.failure(MissingOsuBackfillWorkData.failureToData(message))
        }
    }

    private fun createForegroundInfo(progress: MissingOsuBackfillProgress?): ForegroundInfo {
        val notification = createNotification(progress)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(progress: MissingOsuBackfillProgress?): Notification {
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val cancelIntent = Intent(
            applicationContext,
            MissingOsuBackfillCancelReceiver::class.java
        ).setAction(MissingOsuBackfillCancelReceiver.ACTION_CANCEL)
        val cancelPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            1,
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.mosu_icon_foreground)
            .setContentTitle(
                applicationContext.getString(R.string.missing_osu_notification_title)
            )
            .setContentText(notificationMessage(progress))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(
                0,
                applicationContext.getString(R.string.missing_osu_notification_cancel),
                cancelPendingIntent
            )

        val overallProgress = progress
            ?.takeIf {
                it.phase == MissingOsuBackfillPhase.Downloading ||
                    it.phase == MissingOsuBackfillPhase.Extracting ||
                    it.phase == MissingOsuBackfillPhase.Completed
            }
            ?.takeIf { it.total > 0 }
            ?.let { (it.processed * 100 / it.total).coerceIn(0, 100) }
        if (overallProgress != null) {
            builder.setProgress(100, overallProgress, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun notificationMessage(progress: MissingOsuBackfillProgress?): String {
        if (progress == null) {
            return applicationContext.getString(R.string.missing_osu_notification_starting)
        }
        return when (progress.phase) {
            MissingOsuBackfillPhase.Scanning -> {
                if (progress.total > 0) {
                    applicationContext.getString(
                        R.string.profile_debug_missing_osu_scan_progress,
                        progress.processed,
                        progress.total
                    )
                } else {
                    applicationContext.getString(
                        R.string.profile_debug_missing_osu_scanning
                    )
                }
            }
            MissingOsuBackfillPhase.Downloading -> buildString {
                append(
                    applicationContext.getString(
                        R.string.profile_debug_missing_osu_repair_progress,
                        progress.processed,
                        progress.total
                    )
                )
                progress.currentSetId?.let {
                    append(" · ")
                    append(
                        applicationContext.getString(
                            R.string.profile_debug_missing_osu_current_set,
                            it
                        )
                    )
                }
                if (progress.downloadProgress >= 0) {
                    append(" · ")
                    append(
                        applicationContext.getString(
                            R.string.profile_debug_missing_osu_download_progress,
                            progress.downloadProgress
                        )
                    )
                }
            }
            MissingOsuBackfillPhase.Extracting -> buildString {
                append(
                    applicationContext.getString(
                        R.string.profile_debug_missing_osu_extracting
                    )
                )
                progress.currentSetId?.let {
                    append(" · ")
                    append(
                        applicationContext.getString(
                            R.string.profile_debug_missing_osu_current_set,
                            it
                        )
                    )
                }
            }
            MissingOsuBackfillPhase.Completed ->
                applicationContext.getString(R.string.profile_debug_missing_osu_completed)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.missing_osu_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val WORK_NAME = "missing-osu-backfill"
        const val CHANNEL_ID = "missing_osu_backfill_channel"
        const val NOTIFICATION_ID = 3
    }
}

/**
 * WorkManager only persists primitive values. Keeping the encoding here makes
 * progress survive both navigation and process restarts without a schema change.
 */
internal object MissingOsuBackfillWorkData {
    private const val KEY_PHASE = "phase"
    private const val KEY_PROCESSED = "processed"
    private const val KEY_TOTAL = "total"
    private const val KEY_CURRENT_SET_ID = "current_set_id"
    private const val KEY_DOWNLOAD_PROGRESS = "download_progress"
    private const val KEY_REPAIRED = "repaired"
    private const val KEY_SKIPPED = "skipped"
    private const val KEY_FAILURE_REASONS = "failure_reasons"
    const val KEY_ERROR = "error"

    fun progressToData(progress: MissingOsuBackfillProgress): Data {
        return Data.Builder()
            .putString(KEY_PHASE, progress.phase.name)
            .putInt(KEY_PROCESSED, progress.processed)
            .putInt(KEY_TOTAL, progress.total)
            .putLong(KEY_CURRENT_SET_ID, progress.currentSetId ?: -1L)
            .putInt(KEY_DOWNLOAD_PROGRESS, progress.downloadProgress)
            .build()
    }

    fun progressFromData(data: Data): MissingOsuBackfillProgress? {
        val phase = data.getString(KEY_PHASE)
            ?.let { runCatching { MissingOsuBackfillPhase.valueOf(it) }.getOrNull() }
            ?: return null
        return MissingOsuBackfillProgress(
            phase = phase,
            processed = data.getInt(KEY_PROCESSED, 0),
            total = data.getInt(KEY_TOTAL, 0),
            currentSetId = data.getLong(KEY_CURRENT_SET_ID, -1L)
                .takeIf { it > 0L },
            downloadProgress = data.getInt(KEY_DOWNLOAD_PROGRESS, -1)
        )
    }

    fun resultToData(result: MissingOsuBackfillResult): Data {
        return Data.Builder()
            .putInt(KEY_TOTAL, result.total)
            .putInt(KEY_REPAIRED, result.repaired)
            .putInt(KEY_SKIPPED, result.skipped)
            .putString(KEY_FAILURE_REASONS, failureReasonsToJson(result.failureReasons))
            .build()
    }

    fun resultFromData(data: Data): MissingOsuBackfillResult? {
        if (!data.keyValueMap.containsKey(KEY_TOTAL)) return null
        return MissingOsuBackfillResult(
            total = data.getInt(KEY_TOTAL, 0),
            repaired = data.getInt(KEY_REPAIRED, 0),
            skipped = data.getInt(KEY_SKIPPED, 0),
            failureReasons = failureReasonsFromJson(
                data.getString(KEY_FAILURE_REASONS)
            )
        )
    }

    fun failureToData(message: String): Data {
        return Data.Builder()
            .putString(KEY_ERROR, message)
            .build()
    }

    private fun failureReasonsToJson(reasons: Map<String, Int>): String {
        val json = JSONObject()
        reasons.forEach { (reason, count) -> json.put(reason, count) }
        return json.toString()
    }

    private fun failureReasonsFromJson(value: String?): Map<String, Int> {
        if (value.isNullOrBlank()) return emptyMap()
        return runCatching {
            val json = JSONObject(value)
            buildMap {
                json.keys().forEach { key ->
                    put(key, json.optInt(key))
                }
            }
        }.getOrDefault(emptyMap())
    }
}
