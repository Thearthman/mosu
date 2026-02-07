package com.mosu.app.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mosu.app.data.api.model.BeatmapsetCompact
import com.mosu.app.data.api.model.Covers

/**
 * Unified data model representing a BeatmapSet (Album/Set)
 */
data class BeatmapSetData(
    val id: Long,
    val title: String,
    val artist: String,
    val creator: String? = null,
    val coverPath: String? = null, // Local file path
    val coverUrl: String? = null,  // Remote URL
    val tracks: List<BeatmapTrackData> = emptyList(),
    val isExpandable: Boolean = false,
    val isDownloaded: Boolean = false,
    val downloadProgress: Int? = null, // 0-100
    
    // Metadata slots
    val ranking: Int? = null,      // e.g. #1
    val playCount: Int? = null,    // e.g. 123
    val lastPlayed: String? = null, // e.g. "2h ago"
    val genreId: Int? = null
) {
    val trackCount: Int
        get() = tracks.size

    fun toCompact(): BeatmapsetCompact = BeatmapsetCompact(
        id = id,
        title = title,
        artist = artist,
        creator = creator ?: "",
        covers = Covers(coverUrl ?: "", coverUrl ?: ""),
        genreId = genreId,
        status = "unknown",
        beatmaps = emptyList()
    )
}

/**
 * Data model for individual tracks within a set
 */
data class BeatmapTrackData(
    val id: Long, // Unique ID (uid or beatmapId)
    val difficultyName: String,
    val title: String? = null,
    val artist: String? = null,
    val audioPath: String? = null,
    val isDownloaded: Boolean = true
)

/**
 * Unified actions interface for BeatmapSetList
 */
data class BeatmapSetActions(
    val onClick: (BeatmapSetData) -> Unit = {},
    val onLongClick: (BeatmapSetData) -> Unit = {},
    val onSecondaryAction: ((BeatmapSetData) -> Unit)? = null, // e.g. Download, Add
    val onTrackPlay: ((BeatmapTrackData) -> Unit)? = null,
    val onTrackSwipeLeft: ((BeatmapTrackData) -> Unit)? = null,
    val onTrackSwipeRight: ((BeatmapTrackData) -> Unit)? = null,
    
    // Swipe Actions
    val onSwipeLeft: ((BeatmapSetData) -> Unit)? = null,
    val onSwipeRight: ((BeatmapSetData) -> Unit)? = null,
    
    // Icons for Swipe Actions
    val swipeLeftIcon: ImageVector? = null,
    val swipeRightIcon: ImageVector? = null
)

/**
 * Configuration for BeatmapSetList
 */
data class BeatmapSetListConfig(
    val showDividers: Boolean = true,
    val showScrollbar: Boolean = false,
    val expandedIds: Set<Long> = emptySet(),
    val onExpansionChanged: (Long, Boolean) -> Unit = { _, _ -> },
    val contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp)
)
