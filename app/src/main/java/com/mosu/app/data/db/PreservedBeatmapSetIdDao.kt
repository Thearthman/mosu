package com.mosu.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PreservedBeatmapSetIdDao {
    @Query("SELECT * FROM preserved_beatmap_set_ids ORDER BY preservedAt DESC")
    fun getAllPreservedSetIds(): Flow<List<PreservedBeatmapSetIdEntity>>

    @Query("SELECT COUNT(*) FROM preserved_beatmap_set_ids")
    fun getPreservedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPreservedSetId(entity: PreservedBeatmapSetIdEntity)

    @Query("DELETE FROM preserved_beatmap_set_ids WHERE beatmapSetId = :setId")
    suspend fun deletePreservedSetId(setId: Long)

    @Query("DELETE FROM preserved_beatmap_set_ids")
    suspend fun clearAllPreservedSetIds()
}
