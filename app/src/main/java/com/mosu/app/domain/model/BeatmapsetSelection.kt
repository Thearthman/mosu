package com.mosu.app.domain.model

import com.mosu.app.data.api.model.BeatmapsetCompact

fun BeatmapsetCompact.totalBeatmapPlayCount(): Long {
    return beatmaps.orEmpty().sumOf { it.playCount.toLong() }
}

fun List<BeatmapsetCompact>.sortedByTotalBeatmapPlayCountDescending(): List<BeatmapsetCompact> {
    return sortedByDescending { it.totalBeatmapPlayCount() }
}

fun List<BeatmapsetCompact>.mostPlayedExactTitleArtist(
    title: String,
    artist: String
): BeatmapsetCompact? {
    val targetTitle = title.trim().lowercase()
    val targetArtist = artist.trim().lowercase()
    return asSequence()
        .filter {
            it.title.trim().lowercase() == targetTitle &&
                it.artist.trim().lowercase() == targetArtist
        }
        .maxByOrNull { it.totalBeatmapPlayCount() }
}

internal fun shouldReplaceDedupedCandidate(
    existing: BeatmapsetCompact,
    candidate: BeatmapsetCompact,
    downloadedBeatmapSetIds: Set<Long>
): Boolean {
    val candidateDownloaded = candidate.id in downloadedBeatmapSetIds
    val existingDownloaded = existing.id in downloadedBeatmapSetIds
    return when {
        candidateDownloaded != existingDownloaded -> candidateDownloaded
        candidateDownloaded -> false
        else -> candidate.totalBeatmapPlayCount() > existing.totalBeatmapPlayCount()
    }
}
