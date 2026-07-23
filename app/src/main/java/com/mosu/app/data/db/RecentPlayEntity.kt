package com.mosu.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recent_plays",
    indices = [Index(value = ["scoreId"], unique = true)]
)
data class RecentPlayEntity(
    @PrimaryKey(autoGenerate = true)
    val uid: Long = 0,
    val scoreId: Long? = null,
    val beatmapId: Long? = null,
    val beatmapSetId: Long,
    val title: String,
    val artist: String,
    val creator: String,
    val difficultyName: String? = null,
    val mode: String? = null,
    val coverUrl: String? = null,
    val genreId: Int? = null,
    val rank: String? = null,
    val pp: Float? = null,
    val accuracy: Float? = null,
    val maxCombo: Int? = null,
    val passed: Boolean? = null,
    val starRating: Float? = null,
    val lengthSeconds: Int? = null,
    val hitLengthSeconds: Int? = null,
    val bpm: Float? = null,
    val ar: Float? = null,
    val cs: Float? = null,
    val drain: Float? = null,
    val playedAt: Long
)
