package com.mosu.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY updatedAt DESC LIMIT :limit")
    fun getRecentSearches(limit: Int = 12): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearch(search: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearch(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearches()

    @Query("DELETE FROM search_history WHERE query NOT IN (SELECT query FROM search_history ORDER BY updatedAt DESC LIMIT :maxEntries)")
    suspend fun trimTo(maxEntries: Int)
}
