package com.mosu.app.domain.model

import com.mosu.app.data.api.model.BeatmapsetCompact

fun BeatmapsetCompact.totalBeatmapPlayCount(): Int {
    return beatmaps.sumOf { it.playCount }
}

fun List<BeatmapsetCompact>.sortedByTotalBeatmapPlayCountDescending(): List<BeatmapsetCompact> {
    return sortedByDescending { it.totalBeatmapPlayCount() }
}
