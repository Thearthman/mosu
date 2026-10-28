package com.mosu.app.domain.render

import java.util.Locale

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
        val setMaps = maps
            .filter { it.beatmapSetId == beatmapSetId }
            .distinctBy { it.path }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.difficultyName })
        if (setMaps.isEmpty()) return null

        // A library row may represent a non-standard difficulty. Resolve that
        // row to its audio group before dropping unsupported rulesets so a
        // mixed-mode set cannot make the standard catalog ambiguous.
        val entityAudioFilename = setMaps
            .asSequence()
            .filter { it.difficultyName == defaultDifficultyName }
            .map { OsuBeatmapParser.normalizeAudioFilename(it.audioFilename) }
            .distinct()
            .singleOrNull()

        val standardMaps = setMaps.filter { it.mode == 0 }
        if (standardMaps.isEmpty()) return null

        val groups = standardMaps
            .groupBy { OsuBeatmapParser.normalizeAudioFilename(it.audioFilename) }
            .toSortedMap()
        val playedAudio = playedAudioFilename
            ?.takeIf { it.isNotBlank() }
            ?.let(OsuBeatmapParser::normalizeAudioFilename)
        val entityAudio = entityAudioFilename?.let { filename ->
            groups[filename]?.let { filename to it }
        }
        val playedAudioSelection = playedAudio?.let { filename ->
            groups[filename]?.let { filename to it }
        }

        val exactDifficultyGroups = groups.filterValues { difficulties ->
            difficulties.any { it.difficultyName == defaultDifficultyName }
        }
        val selected = when {
            entityAudio != null -> entityAudio
            exactDifficultyGroups.size == 1 -> exactDifficultyGroups.entries.single().toPair()
            exactDifficultyGroups.size > 1 -> {
                val audioMatches = exactDifficultyGroups.filterKeys { it == playedAudio }
                audioMatches.entries.singleOrNull()?.toPair()
            }
            playedAudioSelection != null -> playedAudioSelection
            groups.size == 1 -> groups.entries.single().toPair()
            else -> null
        } ?: return null

        val (selectedAudioFilename, selectedMaps) = selected
        val difficulties = selectedMaps
            .sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { difficulty: OsuDifficultyMap ->
                    difficulty.difficultyName
                }.thenBy { it.path }
            )
            .distinctBy { it.mode to normalizeDifficultyName(it.difficultyName) }
        if (difficulties.isEmpty()) return null

        return OsuPreviewCatalog(
            beatmapSetId = beatmapSetId,
            audioFilename = selectedAudioFilename,
            difficulties = difficulties,
            defaultDifficultyName = difficulties.firstOrNull {
                it.difficultyName == defaultDifficultyName
            }?.difficultyName ?: difficulties.first().difficultyName
        )
    }

    private fun normalizeDifficultyName(value: String): String =
        value.trim().lowercase(Locale.ROOT)
}
