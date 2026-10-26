package com.mosu.app.data.db

import androidx.room.Entity

/**
 * API-sourced star rating for one standard-mode difficulty.
 *
 * Ratings are kept separate from [BeatmapEntity] because a downloaded set may
 * contain several difficulties that share one track row (and therefore one
 * audio file). [beatmapId] is the stable per-difficulty identity.
 */
@Entity(
    tableName = "beatmap_star_ratings",
    primaryKeys = ["beatmapSetId", "beatmapId"]
)
data class BeatmapStarRatingEntity(
    val beatmapSetId: Long,
    val beatmapId: Long,
    val starRating: Float
)
