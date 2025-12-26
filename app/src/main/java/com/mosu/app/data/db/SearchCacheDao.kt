package com.mosu.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SearchCacheDao {
    @Query("SELECT * FROM search_cache WHERE queryKey = :key")
    suspend fun getCachedResult(key: String): SearchCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: SearchCacheEntity)

    @Query("DELETE FROM search_cache WHERE cachedAt < :expiryTime")
    suspend fun clearExpired(expiryTime: Long)

    @Query("DELETE FROM search_cache WHERE queryKey NOT IN (SELECT queryKey FROM search_cache ORDER BY cachedAt DESC LIMIT :maxEntries)")
    suspend fun limitCacheSize(maxEntries: Int)
}

