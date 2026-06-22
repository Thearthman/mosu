package com.mosu.app.domain.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.DownloadTaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DownloadStatus {
    Queued,
    Downloading,
    Extracting,
    Success,
    Error
}

data class DownloadTask(
    val setId: Long,
    val title: String,
    val artist: String,
    val status: DownloadStatus,
    val progress: Int = 0,
    val statusMessage: String = "",
    val creator: String = "",
    val genreId: Int? = null,
    val coverUrl: String? = null,
    val errorMessage: String? = null
)

class DownloadManager(
    private val context: Context,
    private val db: AppDatabase,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    companion object {
        private const val WORK_PREFIX = "beatmap-download-"
        private const val SUCCESS_RETENTION_MS = 5_000L

        @Volatile
        private var INSTANCE: DownloadManager? = null

        fun getInstance(context: Context, db: AppDatabase): DownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadManager(context.applicationContext, db).also { INSTANCE = it }
            }
        }
    }

    private val taskDao = db.downloadTaskDao()
    private val workManager = WorkManager.getInstance(context)

    val tasks: StateFlow<Map<Long, DownloadTask>> = taskDao.observeTasks()
        .map { entities -> entities.associate { it.setId to it.toDownloadTask() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    init {
        scope.launch {
            cleanupOldSuccesses()
            resumeInterruptedTasks()
        }
    }

    fun enqueue(
        setId: Long,
        title: String,
        artist: String,
        creator: String,
        accessToken: String?,
        genreId: Int? = null,
        coverUrl: String? = null
    ) {
        scope.launch {
            val existing = taskDao.getTask(setId)
            val existingStatus = existing?.status?.let {
                runCatching { DownloadStatus.valueOf(it) }.getOrNull()
            }
            if (existingStatus == DownloadStatus.Queued ||
                existingStatus == DownloadStatus.Downloading ||
                existingStatus == DownloadStatus.Extracting
            ) {
                return@launch
            }

            taskDao.upsertTask(
                DownloadTaskEntity(
                    setId = setId,
                    title = title,
                    artist = artist,
                    creator = creator,
                    accessToken = accessToken,
                    genreId = genreId,
                    coverUrl = coverUrl,
                    status = DownloadStatus.Queued.name,
                    progress = 0,
                    statusMessage = "Queued",
                    createdAt = existing?.createdAt ?: System.currentTimeMillis()
                )
            )
            enqueueWorker(setId, ExistingWorkPolicy.REPLACE)
        }
    }

    fun cancel(setId: Long) {
        scope.launch {
            workManager.cancelUniqueWork(workName(setId))
            taskDao.deleteTask(setId)
        }
    }

    fun retry(setId: Long, accessToken: String?) {
        scope.launch {
            val task = taskDao.getTask(setId) ?: return@launch
            taskDao.upsertTask(
                task.copy(
                    accessToken = accessToken,
                    status = DownloadStatus.Queued.name,
                    progress = 0,
                    statusMessage = "Queued",
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
            enqueueWorker(setId, ExistingWorkPolicy.REPLACE)
        }
    }

    private suspend fun cleanupOldSuccesses() {
        taskDao.deleteTasksWithStatusOlderThan(
            status = DownloadStatus.Success.name,
            olderThan = System.currentTimeMillis() - SUCCESS_RETENTION_MS
        )
    }

    private suspend fun resumeInterruptedTasks() {
        val activeStatuses = listOf(
            DownloadStatus.Queued.name,
            DownloadStatus.Downloading.name,
            DownloadStatus.Extracting.name
        )
        taskDao.getTasksByStatus(activeStatuses).forEach { task ->
            taskDao.updateTaskState(
                setId = task.setId,
                status = DownloadStatus.Queued.name,
                progress = task.progress,
                statusMessage = "Queued",
                errorMessage = null
            )
            enqueueWorker(task.setId, ExistingWorkPolicy.KEEP)
        }
    }

    private fun enqueueWorker(setId: Long, policy: ExistingWorkPolicy) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<BeatmapDownloadWorker>()
            .setInputData(
                Data.Builder()
                    .putLong(BeatmapDownloadWorker.KEY_SET_ID, setId)
                    .build()
            )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(workName(setId), policy, request)
    }

    private fun workName(setId: Long): String = "$WORK_PREFIX$setId"
}
