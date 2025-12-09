package com.mosu.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BeatmapDao {
    @Query("SELECT * FROM beatmaps ORDER BY downloadedAt DESC")
    fun getAllBeatmaps(): Flow<List<BeatmapEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBeatmap(beatmap: BeatmapEntity)

    @Query("DELETE FROM beatmaps WHERE id = :id")
    suspend fun deleteBeatmap(id: Long)
}

