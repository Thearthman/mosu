package com.mosu.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_plays")
data class RecentPlayEntity(
    @PrimaryKey(autoGenerate = true)
    val uid: Long = 0,
    val scoreId: Long? = null,
    val beatmapSetId: Long,
    val title: String,
    val artist: String,
    val creator: String,
    val coverUrl: String? = null,
    val genreId: Int? = null,
    val playedAt: Long
)

