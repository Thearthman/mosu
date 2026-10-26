package com.mosu.app.domain.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OsuDifficultyMatcherTest {
    @Test
    fun selectsGroupContainingTheEntityDifficulty() {
        val maps = listOf(
            map("a/easy.osu", "Easy", "audio-a.mp3"),
            map("a/hard.osu", "Hard", "audio-a.mp3"),
            map("b/insane.osu", "Insane", "audio-b.mp3")
        )

        val catalog = OsuDifficultyMatcher.selectCatalog(
            maps = maps,
            beatmapSetId = 42,
            defaultDifficultyName = "Hard",
            playedAudioFilename = "sanitized-a.mp3"
        )

        assertEquals("audio-a.mp3", catalog?.audioFilename)
        assertEquals("Hard", catalog?.defaultDifficultyName)
        assertEquals(listOf("Easy", "Hard"), catalog?.difficulties?.map { it.difficultyName })
    }

    @Test
    fun soleAudioGroupIsUsedWhenMediaStoreNameCannotMatchAudioFilename() {
        val maps = listOf(
            map("hard.osu", "Hard", "original name.mp3"),
            map("insane.osu", "Insane", "original name.mp3")
        )

        val catalog = OsuDifficultyMatcher.selectCatalog(
            maps = maps,
            beatmapSetId = 42,
            defaultDifficultyName = "Insane",
            playedAudioFilename = "original_name.mp3"
        )

        assertEquals("original name.mp3", catalog?.audioFilename)
        assertEquals("Insane", catalog?.defaultDifficultyName)
        assertEquals(2, catalog?.difficulties?.size)
    }

    @Test
    fun ambiguousGroupsWithoutAStableMatchAreRejected() {
        val maps = listOf(
            map("a/hard.osu", "Hard", "a.mp3"),
            map("a/insane.osu", "Insane", "a.mp3"),
            map("b/hard.osu", "Hard", "b.mp3"),
            map("b/insane.osu", "Insane", "b.mp3")
        )

        assertNull(
            OsuDifficultyMatcher.selectCatalog(
                maps = maps,
                beatmapSetId = 42,
                defaultDifficultyName = "Missing",
                playedAudioFilename = "sanitized.mp3"
            )
        )
    }

    @Test
    fun normalizedPlayedAudioFilenameDisambiguatesGroups() {
        val maps = listOf(
            map("a/hard.osu", "Hard", "folder/A.mp3"),
            map("b/hard.osu", "Hard", "folder/B.mp3")
        )

        val catalog = OsuDifficultyMatcher.selectCatalog(
            maps = maps,
            beatmapSetId = 42,
            defaultDifficultyName = "Missing",
            playedAudioFilename = "\\FOLDER\\a.MP3"
        )

        assertEquals("folder/a.mp3", catalog?.audioFilename)
        assertEquals(listOf("Hard"), catalog?.difficulties?.map { it.difficultyName })
    }

    @Test
    fun mapsFromOtherBeatmapSetsAreIgnored() {
        val maps = listOf(
            map("good.osu", "Hard", "audio.mp3", beatmapSetId = 42),
            map("bad.osu", "Hard", "audio.mp3", beatmapSetId = 99)
        )

        val catalog = OsuDifficultyMatcher.selectCatalog(
            maps = maps,
            beatmapSetId = 42,
            defaultDifficultyName = "Hard"
        )

        assertEquals(listOf("good.osu"), catalog?.difficulties?.map { it.path })
        assertTrue(catalog?.difficulties?.all { it.beatmapSetId == 42L } == true)
    }

    @Test
    fun savedDifficultyIsRestoredWhenItBelongsToTheCatalog() {
        val catalog = requireNotNull(
            OsuDifficultyMatcher.selectCatalog(
                maps = listOf(
                    map("easy.osu", "Easy", "audio.mp3"),
                    map("insane.osu", "Insane", "audio.mp3")
                ),
                beatmapSetId = 42,
                defaultDifficultyName = "Easy"
            )
        )

        assertEquals("Insane", catalog.resolveSavedDifficulty("Insane"))
    }

    @Test
    fun unavailableSavedDifficultyFallsBackToTheEntityDifficulty() {
        val catalog = requireNotNull(
            OsuDifficultyMatcher.selectCatalog(
                maps = listOf(
                    map("easy.osu", "Easy", "audio.mp3"),
                    map("hard.osu", "Hard", "audio.mp3")
                ),
                beatmapSetId = 42,
                defaultDifficultyName = "Hard"
            )
        )

        assertEquals("Hard", catalog.resolveSavedDifficulty("Deleted difficulty"))
        assertEquals("Hard", catalog.resolveSavedDifficulty(null))
    }

    @Test
    fun selectionKeyDistinguishesAudioGroupsWithinTheSameSet() {
        val first = catalog(audio = "a.mp3")
        val second = catalog(audio = "b.mp3")

        assertTrue(first.selectionKey != second.selectionKey)
        assertEquals("42|a.mp3", first.selectionKey)
    }

    @Test
    fun catalogSelectionKeepsPerDifficultyBeatmapId() {
        val catalog = requireNotNull(
            OsuDifficultyMatcher.selectCatalog(
                maps = listOf(
                    map("easy.osu", "Easy", "audio.mp3", beatmapId = 101),
                    map("hard.osu", "Hard", "audio.mp3", beatmapId = 102)
                ),
                beatmapSetId = 42,
                defaultDifficultyName = "Easy"
            )
        )

        assertEquals(listOf(101L, 102L), catalog.difficulties.map { it.beatmapId })
    }

    private fun catalog(audio: String): OsuPreviewCatalog = requireNotNull(
        OsuDifficultyMatcher.selectCatalog(
            maps = listOf(map("$audio.osu", "Hard", audio)),
            beatmapSetId = 42,
            defaultDifficultyName = "Hard"
        )
    )

    private fun map(
        path: String,
        difficulty: String,
        audio: String,
        beatmapSetId: Long = 42,
        beatmapId: Long? = null
    ) = OsuDifficultyMap(
        path = path,
        difficultyName = difficulty,
        beatmapSetId = beatmapSetId,
        audioFilename = audio,
        beatmapId = beatmapId
    )
}
