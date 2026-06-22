package com.mosu.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun observeTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE setId = :setId")
    suspend fun getTask(setId: Long): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE status IN (:statuses)")
    suspend fun getTasksByStatus(statuses: List<String>): List<DownloadTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: DownloadTaskEntity)

    @Query("""
        UPDATE download_tasks
        SET status = :status,
            progress = :progress,
            statusMessage = :statusMessage,
            errorMessage = :errorMessage,
            updatedAt = :updatedAt
        WHERE setId = :setId
    """)
    suspend fun updateTaskState(
        setId: Long,
        status: String,
        progress: Int,
        statusMessage: String,
        errorMessage: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM download_tasks WHERE setId = :setId")
    suspend fun deleteTask(setId: Long)

    @Query("DELETE FROM download_tasks WHERE status = :status AND updatedAt < :olderThan")
    suspend fun deleteTasksWithStatusOlderThan(status: String, olderThan: Long)
}
