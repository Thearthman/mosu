package com.mosu.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "beatmaps")
data class BeatmapEntity(
    @PrimaryKey
    val id: Long,
    val title: String,
    val artist: String,
    val creator: String,
    val audioPath: String, // Absolute path to the .mp3/.ogg
    val coverPath: String, // Absolute path to the .jpg
    val downloadedAt: Long = System.currentTimeMillis()
)

