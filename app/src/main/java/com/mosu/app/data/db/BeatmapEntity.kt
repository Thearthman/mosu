package com.mosu.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "beatmaps",
    indices = [Index(value = ["beatmapSetId"])]
)
data class BeatmapEntity(
    @PrimaryKey(autoGenerate = true)
    val uid: Long = 0,
    val beatmapSetId: Long,
    val title: String,
    val artist: String,
    val creator: String,
    val difficultyName: String,
    val audioPath: String,
    val coverPath: String,
    val downloadedAt: Long = System.currentTimeMillis(),
    val genreId: Int? = null, // Store genre for filtering in Library
    val isAlbum: Boolean = false // Whether this track belongs to an album (multiple audio files in set)
)
