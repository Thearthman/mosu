package com.mosu.app.domain.repair

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class MissingOsuBackfillRunState {
    Idle,
    Queued,
    Running,
    Completed,
    Failed,
    Cancelled
}

data class MissingOsuBackfillUiState(
    val runState: MissingOsuBackfillRunState = MissingOsuBackfillRunState.Idle,
    val progress: MissingOsuBackfillProgress? = null,
    val result: MissingOsuBackfillResult? = null,
    val errorMessage: String? = null
) {
    val isActive: Boolean
        get() = runState == MissingOsuBackfillRunState.Queued ||
            runState == MissingOsuBackfillRunState.Running
}

/**
 * Owns the unique repair job. WorkManager is the durable source of truth while
 * the job is running; the small outcome snapshot keeps the last result visible
 * even if WorkManager later prunes the finished work.
 */
class MissingOsuBackfillManager private constructor(context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: MissingOsuBackfillManager? = null

        fun getInstance(context: Context): MissingOsuBackfillManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MissingOsuBackfillManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val stateStore = MissingOsuBackfillStateStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val startMutex = Mutex()
    private val _state = MutableStateFlow(
        stateStore.loadOutcome()?.toUiState() ?: MissingOsuBackfillUiState()
    )
    val state: StateFlow<MissingOsuBackfillUiState> = _state.asStateFlow()

    private var observationJob: Job? = null

    init {
        scope.launch {
            startMutex.withLock {
                restoreWork()
            }
        }
    }

    fun start() {
        scope.launch {
            startMutex.withLock {
                val existingWork = runCatching {
                    workManager.getWorkInfosForUniqueWork(
                        MissingOsuBackfillWorker.WORK_NAME
                    ).await()
                }.getOrDefault(emptyList())

                val activeWork = existingWork.firstOrNull { it.state.isActive }
                if (activeWork != null) {
                    stateStore.saveWorkId(activeWork.id)
                    observeWork(activeWork.id)
                    return@withLock
                }

                stateStore.clearOutcome()
                val request = OneTimeWorkRequestBuilder<MissingOsuBackfillWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
                stateStore.saveWorkId(request.id)
                _state.value = MissingOsuBackfillUiState(
                    runState = MissingOsuBackfillRunState.Queued,
                    progress = MissingOsuBackfillProgress(
                        phase = MissingOsuBackfillPhase.Scanning,
                        processed = 0,
                        total = 0
                    )
                )
                val enqueueError = runCatching {
                    workManager.enqueueUniqueWork(
                        MissingOsuBackfillWorker.WORK_NAME,
                        ExistingWorkPolicy.KEEP,
                        request
                    ).await()
                }.exceptionOrNull()
                if (enqueueError != null) {
                    val message = enqueueError.message
                        ?.takeIf { it.isNotBlank() }
                        ?: enqueueError::class.java.simpleName
                    _state.value = MissingOsuBackfillUiState(
                        runState = MissingOsuBackfillRunState.Failed,
                        errorMessage = message
                    )
                    stateStore.saveOutcome(
                        runState = MissingOsuBackfillRunState.Failed,
                        result = null,
                        errorMessage = message
                    )
                    return@withLock
                }
                // Observe after enqueue; the flow can still emit null while the
                // WorkManager database transaction becomes visible.
                observeWork(request.id)
            }
        }
    }

    private suspend fun restoreWork() {
        val savedWorkId = stateStore.loadWorkId()
        val existingWork = runCatching {
            workManager.getWorkInfosForUniqueWork(
                MissingOsuBackfillWorker.WORK_NAME
            ).await()
        }.getOrDefault(emptyList())
        val work = existingWork.firstOrNull { it.id == savedWorkId && it.state.isActive }
            ?: existingWork.firstOrNull { it.state.isActive }
            ?: existingWork.firstOrNull { it.id == savedWorkId }
            ?: existingWork.firstOrNull()
            ?: return
        stateStore.saveWorkId(work.id)
        observeWork(work.id)
    }

    private fun observeWork(workId: UUID) {
        observationJob?.cancel()
        observationJob = scope.launch {
            try {
                workManager.getWorkInfoByIdFlow(workId).collect { info ->
                    if (info == null) return@collect
                    val uiState = info.toUiState(stateStore)
                    _state.value = uiState
                    if (!uiState.isActive) {
                        stateStore.saveOutcome(
                            runState = uiState.runState,
                            result = uiState.result,
                            errorMessage = uiState.errorMessage
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_state.value.isActive) {
                    val message = e.message ?: e::class.java.simpleName
                    _state.value = MissingOsuBackfillUiState(
                        runState = MissingOsuBackfillRunState.Failed,
                        progress = _state.value.progress,
                        errorMessage = message
                    )
                    stateStore.saveOutcome(
                        runState = MissingOsuBackfillRunState.Failed,
                        result = null,
                        errorMessage = message
                    )
                }
            }
        }
    }
}

private val WorkInfo.State.isActive: Boolean
    get() = this == WorkInfo.State.ENQUEUED ||
        this == WorkInfo.State.RUNNING ||
        this == WorkInfo.State.BLOCKED

private fun WorkInfo.toUiState(
    stateStore: MissingOsuBackfillStateStore
): MissingOsuBackfillUiState {
    val storedProgress = MissingOsuBackfillWorkData.progressFromData(progress)
    return when (state) {
        WorkInfo.State.ENQUEUED,
        WorkInfo.State.BLOCKED -> MissingOsuBackfillUiState(
            runState = MissingOsuBackfillRunState.Queued,
            progress = storedProgress
        )
        WorkInfo.State.RUNNING -> MissingOsuBackfillUiState(
            runState = MissingOsuBackfillRunState.Running,
            progress = storedProgress ?: MissingOsuBackfillProgress(
                phase = MissingOsuBackfillPhase.Scanning,
                processed = 0,
                total = 0
            )
        )
        WorkInfo.State.SUCCEEDED -> {
            val result = MissingOsuBackfillWorkData.resultFromData(outputData)
                ?: stateStore.loadOutcome()?.result
            val total = result?.total ?: storedProgress?.total ?: 0
            MissingOsuBackfillUiState(
                runState = MissingOsuBackfillRunState.Completed,
                progress = MissingOsuBackfillProgress(
                    phase = MissingOsuBackfillPhase.Completed,
                    processed = total,
                    total = total
                ),
                result = result
            )
        }
        WorkInfo.State.FAILED -> MissingOsuBackfillUiState(
            runState = MissingOsuBackfillRunState.Failed,
            progress = storedProgress,
            result = MissingOsuBackfillWorkData.resultFromData(outputData),
            errorMessage = outputData.getString(MissingOsuBackfillWorkData.KEY_ERROR)
        )
        WorkInfo.State.CANCELLED -> MissingOsuBackfillUiState(
            runState = MissingOsuBackfillRunState.Cancelled,
            progress = storedProgress
        )
    }
}

internal data class MissingOsuBackfillOutcome(
    val runState: MissingOsuBackfillRunState,
    val result: MissingOsuBackfillResult?,
    val errorMessage: String?
) {
    fun toUiState(): MissingOsuBackfillUiState {
        val total = result?.total ?: 0
        return MissingOsuBackfillUiState(
            runState = runState,
            progress = MissingOsuBackfillProgress(
                phase = if (runState == MissingOsuBackfillRunState.Completed) {
                    MissingOsuBackfillPhase.Completed
                } else {
                    MissingOsuBackfillPhase.Scanning
                },
                processed = total,
                total = total
            ),
            result = result,
            errorMessage = errorMessage
        )
    }
}

internal class MissingOsuBackfillStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun saveWorkId(workId: UUID) {
        preferences.edit().putString(KEY_WORK_ID, workId.toString()).apply()
    }

    fun loadWorkId(): UUID? {
        return preferences.getString(KEY_WORK_ID, null)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }

    fun saveOutcome(
        runState: MissingOsuBackfillRunState,
        result: MissingOsuBackfillResult?,
        errorMessage: String?
    ) {
        preferences.edit()
            .putBoolean(KEY_HAS_OUTCOME, true)
            .putString(KEY_RUN_STATE, runState.name)
            .putInt(KEY_TOTAL, result?.total ?: 0)
            .putInt(KEY_REPAIRED, result?.repaired ?: 0)
            .putInt(KEY_SKIPPED, result?.skipped ?: 0)
            .putString(
                KEY_FAILURE_REASONS,
                result?.failureReasons.orEmpty().entries.joinToString("\u001F") {
                    "${it.key}\u001E${it.value}"
                }
            )
            .putString(KEY_ERROR, errorMessage)
            .apply()
    }

    fun loadOutcome(): MissingOsuBackfillOutcome? {
        if (!preferences.getBoolean(KEY_HAS_OUTCOME, false)) return null
        val errorMessage = preferences.getString(KEY_ERROR, null)
        val runState = preferences.getString(KEY_RUN_STATE, null)
            ?.let { runCatching { MissingOsuBackfillRunState.valueOf(it) }.getOrNull() }
            ?: if (errorMessage == null) {
                MissingOsuBackfillRunState.Completed
            } else {
                MissingOsuBackfillRunState.Failed
            }
        val result = if (runState == MissingOsuBackfillRunState.Completed) {
            MissingOsuBackfillResult(
                total = preferences.getInt(KEY_TOTAL, 0),
                repaired = preferences.getInt(KEY_REPAIRED, 0),
                skipped = preferences.getInt(KEY_SKIPPED, 0),
                failureReasons = preferences.getString(KEY_FAILURE_REASONS, null)
                    .orEmpty()
                    .split('\u001F')
                    .filter { it.isNotBlank() }
                    .mapNotNull { entry ->
                        val parts = entry.split('\u001E', limit = 2)
                        val reason = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                        val count = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        reason to count
                    }
                    .toMap()
            )
        } else {
            null
        }
        return MissingOsuBackfillOutcome(
            runState = runState,
            result = result,
            errorMessage = errorMessage
        )
    }

    fun clearOutcome() {
        preferences.edit()
            .remove(KEY_HAS_OUTCOME)
            .remove(KEY_RUN_STATE)
            .remove(KEY_TOTAL)
            .remove(KEY_REPAIRED)
            .remove(KEY_SKIPPED)
            .remove(KEY_FAILURE_REASONS)
            .remove(KEY_ERROR)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "missing_osu_backfill"
        private const val KEY_WORK_ID = "work_id"
        private const val KEY_HAS_OUTCOME = "has_outcome"
        private const val KEY_RUN_STATE = "outcome_run_state"
        private const val KEY_TOTAL = "outcome_total"
        private const val KEY_REPAIRED = "outcome_repaired"
        private const val KEY_SKIPPED = "outcome_skipped"
        private const val KEY_FAILURE_REASONS = "outcome_failure_reasons"
        private const val KEY_ERROR = "outcome_error"
    }
}
