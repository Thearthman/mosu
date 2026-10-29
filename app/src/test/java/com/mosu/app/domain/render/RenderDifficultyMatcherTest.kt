package com.mosu.app.domain.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderDifficultyMatcherTest {
    @Test
    fun selectsGroupContainingTheEntityDifficulty() {
        val maps = listOf(
            map("a/easy.osu", "Easy", "audio-a.mp3"),
            map("a/hard.osu", "Hard", "audio-a.mp3"),
            map("b/insane.osu", "Insane", "audio-b.mp3")
        )

        val catalog = select(maps, defaultDifficultyName = "Hard")

        assertEquals("audio-a.mp3", catalog?.audioFilename)
        assertEquals("Hard", catalog?.defaultDifficultyKey?.difficultyName)
        assertEquals(listOf("Easy", "Hard"), catalog?.difficulties?.map { it.difficultyName })
    }

    @Test
    fun soleAudioGroupIsUsedWhenMediaStoreNameCannotMatchAudioFilename() {
        val maps = listOf(
            map("hard.osu", "Hard", "original name.mp3"),
            map("insane.osu", "Insane", "original name.mp3")
        )

        val catalog = select(
            maps = maps,
            defaultDifficultyName = "Insane",
            playedAudioFilename = "original_name.mp3"
        )

        assertEquals("original name.mp3", catalog?.audioFilename)
        assertEquals("Insane", catalog?.defaultDifficultyKey?.difficultyName)
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
            select(
                maps = maps,
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

        val catalog = select(
            maps = maps,
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

        val catalog = select(maps, defaultDifficultyName = "Hard")

        assertEquals(listOf("good.osu"), catalog?.difficulties?.map { it.path })
        assertTrue(catalog?.difficulties?.all { it.beatmapSetId == 42L } == true)
    }

    @Test
    fun selectionKeyDistinguishesAudioGroupsWithinTheSameSet() {
        val first = requireNotNull(select(listOf(map("a.osu", "Hard", "a.mp3")), "Hard"))
        val second = requireNotNull(select(listOf(map("b.osu", "Hard", "b.mp3")), "Hard"))

        assertTrue(first.selectionKey != second.selectionKey)
        assertEquals("42|a.mp3", first.selectionKey)
    }

    @Test
    fun catalogSelectionKeepsPerDifficultyBeatmapId() {
        val catalog = requireNotNull(
            select(
                maps = listOf(
                    map("easy.osu", "Easy", "audio.mp3", beatmapId = 101),
                    map("hard.osu", "Hard", "audio.mp3", beatmapId = 102)
                ),
                defaultDifficultyName = "Easy"
            )
        )

        assertEquals(listOf(101L, 102L), catalog.difficulties.map { it.beatmapId })
    }

    @Test
    fun mixedRulesetsStillExposeTheRenderableDifficultiesForThePlayingAudio() {
        val catalog = requireNotNull(
            select(
                maps = listOf(
                    map("catch.osu", "Overdose", "audio-a.mp3", mode = RenderMode.CATCH),
                    map("standard-easy.osu", "Easy", "audio-a.mp3"),
                    map("standard-hard.osu", "Hard", "audio-a.mp3"),
                    map("standard-other-audio.osu", "Insane", "audio-b.mp3")
                ),
                defaultDifficultyName = "Overdose",
                playedAudioFilename = "42_Overdose_song.mp3"
            )
        )

        assertEquals("audio-a.mp3", catalog.audioFilename)
        assertEquals(listOf("Easy", "Hard"), catalog.difficulties.map { it.difficultyName })
        assertTrue(catalog.difficulties.all { it.mode == RenderMode.OSU })
        assertEquals("Easy", catalog.defaultDifficultyKey.difficultyName)
    }

    @Test
    fun entityDifficultyAudioWinsOverMatchingNameInAnotherAudioGroup() {
        val catalog = requireNotNull(
            select(
                maps = listOf(
                    map("taiko.osu", "Muzukashii", "audio-a.mp3", mode = RenderMode.TAIKO),
                    map("standard-a.osu", "Easy", "audio-a.mp3"),
                    map("standard-b.osu", "Oni", "audio-b.mp3"),
                    map("standard-b-hard.osu", "Hard", "audio-b.mp3")
                ),
                defaultDifficultyName = "Muzukashii",
                playedAudioFilename = "42_Oni_song.mp3"
            )
        )

        assertEquals("audio-a.mp3", catalog.audioFilename)
        assertEquals(listOf("Easy"), catalog.difficulties.map { it.difficultyName })
    }

    @Test
    fun unsupportedEntityAudioDoesNotFallBackToAnotherAudioGroup() {
        assertNull(
            select(
                maps = listOf(
                    map("taiko.osu", "Oni", "audio-a.mp3", mode = RenderMode.TAIKO),
                    map("standard.osu", "Hard", "audio-b.mp3")
                ),
                defaultDifficultyName = "Oni",
                playedAudioFilename = "42_Oni_song.mp3"
            )
        )
    }

    @Test
    fun legacyFreestylerMapsExcludeUnsupportedRulesetsAndCollapseDuplicateEasyFiles() {
        val catalog = requireNotNull(
            select(
                maps = listOf(
                    map("maps/Easy.osu", "Easy", "freestyler.mp3", beatmapSetId = 35629),
                    map("backup/Easy.osu", "Easy", "freestyler.mp3", beatmapSetId = 35629),
                    map("maps/Easy-copy.osu", "easy", "freestyler.mp3", beatmapSetId = 35629),
                    map("maps/Gladi's Hard.osu", "Gladi's Hard", "freestyler.mp3", beatmapSetId = 35629),
                    map("maps/Insane.osu", "Insane", "freestyler.mp3", beatmapSetId = 35629),
                    map("maps/Normal.osu", "Normal", "freestyler.mp3", beatmapSetId = 35629),
                    map(
                        "maps/Sp3's Taiko.osu",
                        "Sp3's Taiko",
                        "freestyler.mp3",
                        beatmapSetId = 35629,
                        mode = RenderMode.TAIKO
                    )
                ),
                defaultDifficultyName = "Easy",
                beatmapSetId = 35629
            )
        )

        assertEquals(
            listOf("Easy", "Gladi's Hard", "Insane", "Normal"),
            catalog.difficulties.map { it.difficultyName }
        )
        assertTrue(catalog.difficulties.all { it.mode == RenderMode.OSU })
        assertEquals(
            1,
            catalog.difficulties.count { it.difficultyName.equals("Easy", ignoreCase = true) }
        )
    }

    @Test
    fun unsupportedOnlySetDoesNotProduceARenderCatalog() {
        assertNull(
            select(
                maps = listOf(
                    map("mania.osu", "4K", "audio.mp3", mode = RenderMode.MANIA),
                    map("catch.osu", "Overdose", "audio.mp3", mode = RenderMode.CATCH)
                ),
                defaultDifficultyName = "Overdose"
            )
        )
    }

    @Test
    fun duplicateDifficultyNamesAcrossRegisteredModesArePreserved() {
        val catalog = requireNotNull(
            select(
                maps = listOf(
                    map("standard.osu", "Hard", "audio.mp3"),
                    map("taiko.osu", "hard", "audio.mp3", mode = RenderMode.TAIKO)
                ),
                defaultDifficultyName = "Hard",
                supportedModes = setOf(RenderMode.OSU, RenderMode.TAIKO)
            )
        )

        assertEquals(
            listOf(RenderMode.OSU, RenderMode.TAIKO),
            catalog.difficulties.map { it.mode }
        )
        assertEquals(RenderDifficultyKey(RenderMode.OSU, "Hard"), catalog.defaultDifficultyKey)
    }

    @Test
    fun supportedModeFilteringHappensAfterAudioGroupSelection() {
        val catalog = requireNotNull(
            select(
                maps = listOf(
                    map("catch-a.osu", "Overdose", "audio-a.mp3", mode = RenderMode.CATCH),
                    map("taiko-a.osu", "Inner Oni", "audio-a.mp3", mode = RenderMode.TAIKO),
                    map("standard-b.osu", "Hard", "audio-b.mp3")
                ),
                defaultDifficultyName = "Overdose",
                supportedModes = setOf(RenderMode.TAIKO)
            )
        )

        assertEquals("audio-a.mp3", catalog.audioFilename)
        assertEquals(listOf(RenderMode.TAIKO), catalog.difficulties.map { it.mode })
    }

    private fun select(
        maps: List<RenderDifficulty>,
        defaultDifficultyName: String,
        beatmapSetId: Long = 42,
        playedAudioFilename: String? = null,
        supportedModes: Set<RenderMode> = setOf(RenderMode.OSU)
    ): RenderCatalog? = RenderDifficultyMatcher.selectCatalog(
        maps = maps,
        beatmapSetId = beatmapSetId,
        defaultDifficultyName = defaultDifficultyName,
        supportedModes = supportedModes,
        playedAudioFilename = playedAudioFilename
    )

    private fun map(
        path: String,
        difficulty: String,
        audio: String,
        beatmapSetId: Long = 42,
        beatmapId: Long? = null,
        mode: RenderMode = RenderMode.OSU
    ) = RenderDifficulty(
        key = RenderDifficultyKey(mode, difficulty),
        path = path,
        beatmapSetId = beatmapSetId,
        audioFilename = audio,
        beatmapId = beatmapId
    )
}
