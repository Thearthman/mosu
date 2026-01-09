package com.mosu.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BeatmapDao {
    @Query("SELECT * FROM beatmaps ORDER BY downloadedAt DESC")
    fun getAllBeatmaps(): Flow<List<BeatmapEntity>>

    @Query("SELECT * FROM beatmaps WHERE beatmapSetId = :setId")
    suspend fun getTracksForSet(setId: Long): List<BeatmapEntity>

    @Query("SELECT * FROM beatmaps WHERE LOWER(TRIM(title)) = LOWER(TRIM(:title)) AND LOWER(TRIM(artist)) = LOWER(TRIM(:artist))")
    suspend fun getTracksByTitleArtist(title: String, artist: String): List<BeatmapEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBeatmap(beatmap: BeatmapEntity)

    @Delete
    suspend fun deleteBeatmap(beatmap: BeatmapEntity)

    @Query("DELETE FROM beatmaps WHERE beatmapSetId = :setId")
    suspend fun deleteBeatmapSet(setId: Long)

    @Query("SELECT audioPath, coverPath FROM beatmaps")
    suspend fun getAllFilePaths(): List<FilePathTuple>

}

// Helper data class for file paths
data class FilePathTuple(
    val audioPath: String,
    val coverPath: String
)
