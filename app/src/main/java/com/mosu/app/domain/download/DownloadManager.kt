package com.mosu.app.domain.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

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
    private val downloadService: BeatmapDownloadService,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    companion object {
        @Volatile
        private var INSTANCE: DownloadManager? = null

        fun getInstance(context: Context, downloadService: BeatmapDownloadService): DownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadManager(context.applicationContext, downloadService).also { INSTANCE = it }
            }
        }
        
        fun getInstanceOnly(): DownloadManager? = INSTANCE
    }

    private val _tasks = MutableStateFlow<Map<Long, DownloadTask>>(emptyMap())
    val tasks: StateFlow<Map<Long, DownloadTask>> = _tasks.asStateFlow()

    private val queue = Channel<Long>(Channel.UNLIMITED)
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    
    private val MAX_PARALLEL_DOWNLOADS = 3

    init {
        // Start workers
        repeat(MAX_PARALLEL_DOWNLOADS) {
            scope.launch {
                for (setId in queue) {
                    processDownload(setId)
                }
            }
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
        val currentTasks = _tasks.value
        if (currentTasks.containsKey(setId)) {
            val task = currentTasks[setId]!!
            if (task.status != DownloadStatus.Error && task.status != DownloadStatus.Success) {
                // Task already in progress or queued
                return
            }
        }

        // Start Foreground Service
        val serviceIntent = android.content.Intent(context, DownloadService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        val newTask = DownloadTask(
            setId = setId,
            title = title,
            artist = artist,
            creator = creator,
            genreId = genreId,
            coverUrl = coverUrl,
            status = DownloadStatus.Queued,
            statusMessage = "Queued"
        )

        updateTask(newTask)
        scope.launch {
            queue.send(setId)
        }
    }

    fun cancel(setId: Long) {
        activeJobs[setId]?.cancel()
        activeJobs.remove(setId)
        
        val task = _tasks.value[setId]
        if (task != null && task.status != DownloadStatus.Success) {
            _tasks.value = _tasks.value - setId
        }
    }

    fun retry(setId: Long, accessToken: String?) {
        val task = _tasks.value[setId] ?: return
        enqueue(
            setId = task.setId,
            title = task.title,
            artist = task.artist,
            creator = task.creator,
            accessToken = accessToken,
            genreId = task.genreId,
            coverUrl = task.coverUrl
        )
    }

    private suspend fun processDownload(setId: Long) {
        val task = _tasks.value[setId] ?: return
        
        coroutineScope {
            val job = launch {
                try {
                    downloadService.downloadBeatmap(
                        beatmapSetId = setId,
                        title = task.title,
                        artist = task.artist,
                        creator = task.creator,
                        genreId = task.genreId,
                        coversListUrl = task.coverUrl
                    ).collect { state ->
                        handleDownloadState(setId, state)
                    }
                } catch (e: CancellationException) {
                    Log.d("DownloadManager", "Download $setId cancelled")
                    throw e
                } catch (e: Exception) {
                    Log.e("DownloadManager", "Download $setId failed", e)
                    updateTask(task.copy(
                        status = DownloadStatus.Error,
                        errorMessage = e.message ?: "Unknown error"
                    ))
                } finally {
                    activeJobs.remove(setId)
                }
            }

            activeJobs[setId] = job
            job.join()
        }
    }

    private fun handleDownloadState(setId: Long, state: UnifiedDownloadState) {
        val currentTask = _tasks.value[setId] ?: return
        when (state) {
            is UnifiedDownloadState.Progress -> {
                val newStatus = if (state.status.contains("Extracting", ignoreCase = true)) {
                    DownloadStatus.Extracting
                } else {
                    DownloadStatus.Downloading
                }
                updateTask(currentTask.copy(
                    status = newStatus,
                    progress = state.progress,
                    statusMessage = state.status
                ))
            }
            is UnifiedDownloadState.Success -> {
                updateTask(currentTask.copy(
                    status = DownloadStatus.Success,
                    progress = 100,
                    statusMessage = "Done"
                ))
                // Clear success tasks after some time to keep UI clean
                scope.launch {
                    delay(5000)
                    if (_tasks.value[setId]?.status == DownloadStatus.Success) {
                        _tasks.value = _tasks.value - setId
                    }
                }
            }
            is UnifiedDownloadState.Error -> {
                updateTask(currentTask.copy(
                    status = DownloadStatus.Error,
                    errorMessage = state.message,
                    statusMessage = "Failed"
                ))
                // Show error toast
                scope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, state.message, android.widget.Toast.LENGTH_LONG).show()
                }
            }
            is UnifiedDownloadState.Notification -> {
                // Handle notification (e.g. "Trying alternatives")
                updateTask(currentTask.copy(
                    statusMessage = state.message
                ))
                // Show toast for global visibility
                scope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, state.message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateTask(task: DownloadTask) {
        _tasks.value = _tasks.value + (task.setId to task)
    }
}
