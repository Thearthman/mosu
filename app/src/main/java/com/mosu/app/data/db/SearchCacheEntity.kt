package com.mosu.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_cache")
data class SearchCacheEntity(
    @PrimaryKey
    val queryKey: String, // e.g., "played_genre_10"
    val resultsJson: String, // BeatmapsetCompact list, or top-play payload with BestScore rows
    val cursorString: String? = null, // Cache the cursor for pagination
    val cachedAt: Long = System.currentTimeMillis()
)
