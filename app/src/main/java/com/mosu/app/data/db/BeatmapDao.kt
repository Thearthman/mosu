package com.mosu.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BeatmapDao {
    @Query("SELECT * FROM beatmaps ORDER BY downloadedAt DESC")
    fun getAllBeatmaps(): Flow<List<BeatmapEntity>>
    
    @Query("SELECT * FROM beatmaps WHERE beatmapSetId = :setId")
    suspend fun getTracksForSet(setId: Long): List<BeatmapEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBeatmap(beatmap: BeatmapEntity)

    @Delete
    suspend fun deleteBeatmap(beatmap: BeatmapEntity)

    @Query("DELETE FROM beatmaps WHERE beatmapSetId = :setId")
    suspend fun deleteBeatmapSet(setId: Long)
}
