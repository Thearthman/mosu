package com.mosu.app.domain.render

/**
 * Selects the preview difficulty group belonging to the currently playing
 * audio. Maps are grouped by their normalized `.osu` AudioFilename, never by
 * `isAlbum` or difficulty-name heuristics.
 */
object OsuDifficultyMatcher {
    fun selectCatalog(
        maps: List<OsuDifficultyMap>,
        beatmapSetId: Long,
        defaultDifficultyName: String,
        playedAudioFilename: String? = null
    ): OsuPreviewCatalog? {
        val validMaps = maps
            .filter { it.beatmapSetId == beatmapSetId }
            .distinctBy { it.path }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.difficultyName })
        if (validMaps.isEmpty()) return null

        val groups = validMaps
            .groupBy { OsuBeatmapParser.normalizeAudioFilename(it.audioFilename) }
            .toSortedMap()
        val playedAudio = playedAudioFilename
            ?.takeIf { it.isNotBlank() }
            ?.let(OsuBeatmapParser::normalizeAudioFilename)

        val exactDifficultyGroups = groups.filterValues { difficulties ->
            difficulties.any { it.difficultyName == defaultDifficultyName }
        }
        val selected = when {
            exactDifficultyGroups.size == 1 -> exactDifficultyGroups.entries.single()
            exactDifficultyGroups.size > 1 -> {
                val audioMatches = exactDifficultyGroups.filterKeys { it == playedAudio }
                audioMatches.entries.singleOrNull()
            }
            playedAudio != null && groups.containsKey(playedAudio) -> {
                groups.entries.first { it.key == playedAudio }
            }
            groups.size == 1 -> groups.entries.single()
            else -> null
        } ?: return null

        val difficulties = selected.value
            .distinctBy { it.path }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.difficultyName })
        if (difficulties.isEmpty()) return null

        return OsuPreviewCatalog(
            beatmapSetId = beatmapSetId,
            audioFilename = selected.key,
            difficulties = difficulties,
            defaultDifficultyName = difficulties.firstOrNull {
                it.difficultyName == defaultDifficultyName
            }?.difficultyName ?: difficulties.first().difficultyName
        )
    }
}
