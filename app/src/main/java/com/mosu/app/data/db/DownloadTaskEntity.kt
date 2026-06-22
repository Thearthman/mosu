package com.mosu.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey
    val setId: Long,
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
