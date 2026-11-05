package com.mosu.app.ui.components

import com.mosu.app.data.api.model.BestScore
import com.mosu.app.data.db.RecentPlayEntity

internal fun Float.toDisplayAccuracyPercent(): Float = this * 100f

internal fun RecentPlayEntity.toBeatmapSetData(): BeatmapSetData {
    return BeatmapSetData(
        id = beatmapSetId,
        title = title,
        artist = artist,
        creator = creator,
        coverUrl = coverUrl,
        genreId = genreId,
        score = BeatmapSetScoreData(
            grade = rank,
            mode = mode,
            difficultyName = difficultyName,
            pp = pp,
            accuracy = accuracy,
            starRating = starRating,
            lengthSeconds = lengthSeconds
        )
    )
}

internal fun BestScore.toBeatmapSetScoreData(): BeatmapSetScoreData {
    return BeatmapSetScoreData(
        grade = rank,
        mode = beatmap?.mode ?: mode,
        difficultyName = beatmap?.version,
        pp = pp,
        accuracy = accuracy,
        starRating = beatmap?.difficultyRating,
        lengthSeconds = beatmap?.totalLength
    )
}
