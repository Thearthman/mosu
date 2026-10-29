package com.mosu.app.domain.render

import java.util.Locale

/**
 * Selects the difficulty group belonging to the currently playing audio. Maps
 * are grouped by normalized `.osu` AudioFilename, never by `isAlbum` or
 * difficulty-name heuristics.
 */
object RenderDifficultyMatcher {
    fun selectCatalog(
        maps: List<RenderDifficulty>,
        beatmapSetId: Long,
        defaultDifficultyName: String,
        supportedModes: Set<RenderMode>,
        playedAudioFilename: String? = null
    ): RenderCatalog? {
        if (supportedModes.isEmpty()) return null

        val setMaps = maps
            .filter { it.beatmapSetId == beatmapSetId }
            .distinctBy { it.path }
            .sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { difficulty: RenderDifficulty ->
                    difficulty.difficultyName
                }
                    .thenBy { it.mode.osuMode }
                    .thenBy { it.path }
            )
        if (setMaps.isEmpty()) return null

        // A library row may represent a non-standard difficulty. Resolve that
        // row to its audio group before dropping unsupported rulesets so a
        // mixed-mode set cannot make the renderable catalog ambiguous.
        val entityAudioFilename = setMaps
            .asSequence()
            .filter { it.difficultyName == defaultDifficultyName }
            .map { OsuFileHeaderReader.normalizeAudioFilename(it.audioFilename) }
            .distinct()
            .singleOrNull()

        val renderableMaps = setMaps.filter { it.mode in supportedModes }
        if (renderableMaps.isEmpty()) return null

        val groups = renderableMaps
            .groupBy { OsuFileHeaderReader.normalizeAudioFilename(it.audioFilename) }
            .toSortedMap()
        val playedAudio = playedAudioFilename
            ?.takeIf { it.isNotBlank() }
            ?.let(OsuFileHeaderReader::normalizeAudioFilename)
        if (entityAudioFilename != null && entityAudioFilename !in groups) return null
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
                compareBy(String.CASE_INSENSITIVE_ORDER) { difficulty: RenderDifficulty ->
                    difficulty.difficultyName
                }
                    .thenBy { it.mode.osuMode }
                    .thenBy { it.path }
            )
            .distinctBy { it.mode to normalizeDifficultyName(it.difficultyName) }
        if (difficulties.isEmpty()) return null

        return RenderCatalog(
            beatmapSetId = beatmapSetId,
            audioFilename = selectedAudioFilename,
            difficulties = difficulties,
            defaultDifficultyKey = difficulties.firstOrNull {
                it.difficultyName == defaultDifficultyName
            }?.key ?: difficulties.first().key
        )
    }

    private fun normalizeDifficultyName(value: String): String =
        value.trim().lowercase(Locale.ROOT)
}
