package com.mosu.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "preserved_beatmap_set_ids")
data class PreservedBeatmapSetIdEntity(
    @PrimaryKey
    val beatmapSetId: Long,
    val preservedAt: Long = System.currentTimeMillis()
)
