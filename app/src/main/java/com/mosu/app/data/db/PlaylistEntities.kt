package com.mosu.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Import for PlaylistTrackWithStatus
import com.mosu.app.data.db.BeatmapEntity

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "beatmapSetId"],
    indices = [Index(value = ["playlistId"]), Index(value = ["beatmapSetId"])]
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val beatmapSetId: Long, // Changed from beatmapUid to beatmapSetId for stability across restores
    val addedAt: Long = System.currentTimeMillis(),
    val isDownloaded: Boolean? = true // Track if the beatmap set is currently downloaded (nullable for migration compatibility)
)

data class PlaylistTrackCount(
    val playlistId: Long,
    val count: Int
)

// Represents a track in a playlist, which may or may not be downloaded
data class PlaylistTrackWithStatus(
    val playlistId: Long,
    val beatmapUid: Long,
    val addedAt: Long,
    val isDownloaded: Boolean,
    val beatmapEntity: BeatmapEntity? = null // Null if not downloaded
)

// Result of joining playlist_tracks with beatmaps (LEFT JOIN, so beatmap may be null)
data class PlaylistTrackWithBeatmap(
    val playlistId: Long,
    val beatmapSetId: Long, // Changed from beatmapUid
    val addedAt: Long,
    val isDownloaded: Boolean?,
    val uid: Long?, // From beatmap
    val title: String?, // From beatmap
    val artist: String?, // From beatmap
    val creator: String?, // From beatmap
    val difficultyName: String?, // From beatmap
    val audioPath: String?, // From beatmap
    val coverPath: String?, // From beatmap
    val downloadedAt: Long?, // From beatmap
    val genreId: Int? // From beatmap
) {
    fun toBeatmapEntity(): BeatmapEntity? {
        // Determine if downloaded based on whether beatmap data exists (from LEFT JOIN)
        // If beatmap fields are not null, the beatmap exists and is downloaded
        val isActuallyDownloaded = uid != null && beatmapSetId != null && title != null &&
            artist != null && difficultyName != null && audioPath != null && coverPath != null

        return if (isActuallyDownloaded) {
            BeatmapEntity(
                uid = uid!!,
                beatmapSetId = beatmapSetId!!,
                title = title!!,
                artist = artist!!,
                creator = creator ?: "",
                difficultyName = difficultyName!!,
                audioPath = audioPath!!,
                coverPath = coverPath!!,
                downloadedAt = downloadedAt ?: System.currentTimeMillis(),
                genreId = genreId
            )
        } else null
    }
}

