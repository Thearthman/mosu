package com.mosu.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface RecentPlayDao {

    @Query("SELECT * FROM recent_plays ORDER BY playedAt DESC LIMIT 500")
    suspend fun getRecentPlays(): List<RecentPlayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RecentPlayEntity>)

    @Query("DELETE FROM recent_plays")
    suspend fun clearAll()

    @Query("DELETE FROM recent_plays WHERE playedAt < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long)

    @Query("SELECT COUNT(*) FROM recent_plays")
    suspend fun count(): Int

    @Query("SELECT playedAt FROM recent_plays ORDER BY playedAt DESC LIMIT 1")
    suspend fun getNewestPlayTime(): Long?

    @Transaction
    suspend fun replaceAll(items: List<RecentPlayEntity>) {
        clearAll()
        insertAll(items.take(500))
    }

    @Transaction
    suspend fun mergeNewPlays(newItems: List<RecentPlayEntity>) {
        // Insert new items (REPLACE on conflict handles duplicates by scoreId)
        insertAll(newItems)
        
        // Keep only the most recent 500 plays
        val count = count()
        if (count > 500) {
            // Get the 500th newest timestamp
            val all = getRecentPlays() // Already sorted DESC, limit 500
            if (all.size >= 500) {
                val cutoff = all.last().playedAt
                deleteOlderThan(cutoff)
            }
        }
    }
}

