package com.mosu.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BeatmapStarRatingDao {
    @Query("SELECT * FROM beatmap_star_ratings WHERE beatmapSetId = :setId")
    suspend fun getForSet(setId: Long): List<BeatmapStarRatingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(ratings: List<BeatmapStarRatingEntity>)

    @Query("DELETE FROM beatmap_star_ratings WHERE beatmapSetId = :setId")
    suspend fun deleteForSet(setId: Long)

    @Query("DELETE FROM beatmap_star_ratings")
    suspend fun deleteAll()
}
