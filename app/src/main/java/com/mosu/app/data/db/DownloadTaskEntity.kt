package com.mosu.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey
    val setId: Long,
    // title/artist/creator/genreId/coverUrl are non-authoritative *hints* used only
    // to search for / display a queued download. The metadata that actually gets
    // persisted to the library is derived from the downloaded artifact (the .osu
    // inside the .osz) with set-level genre resolved from the API by setId.
    // Never treat these as the source of truth for a track's metadata.
    val title: String,
    val artist: String,
    val creator: String,
    val accessToken: String?,
    val genreId: Int?,
    val coverUrl: String?,
    val status: String,
    val progress: Int = 0,
    val statusMessage: String = "",
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
