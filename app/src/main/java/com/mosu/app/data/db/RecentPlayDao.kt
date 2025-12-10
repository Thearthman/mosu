package com.mosu.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface RecentPlayDao {

    @Query("SELECT * FROM recent_plays ORDER BY playedAt DESC LIMIT 100")
    suspend fun getRecentPlays(): List<RecentPlayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RecentPlayEntity>)

    @Query("DELETE FROM recent_plays")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<RecentPlayEntity>) {
        clearAll()
        insertAll(items.take(100))
    }
}

