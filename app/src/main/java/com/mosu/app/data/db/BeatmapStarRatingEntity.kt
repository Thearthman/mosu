package com.mosu.app.data.db

import androidx.room.Entity

/**
 * API-sourced star rating for one difficulty, regardless of osu! ruleset.
 *
 * Ratings are kept separate from [BeatmapEntity] because a downloaded set may
 * contain several difficulties that share one track row (and therefore one
 * audio file). [beatmapId] is the stable per-difficulty identity when the
 * source provides it. Legacy maps may omit their beatmap id, so the version
 * name and ruleset are retained as a unique-name fallback.
 */
@Entity(
    tableName = "beatmap_star_ratings",
    primaryKeys = ["beatmapSetId", "beatmapId"]
)
data class BeatmapStarRatingEntity(
    val beatmapSetId: Long,
    val beatmapId: Long,
    val starRating: Float,
    val difficultyName: String? = null,
    val mode: Int? = null
)
