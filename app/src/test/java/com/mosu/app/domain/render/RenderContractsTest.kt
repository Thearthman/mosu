package com.mosu.app.domain.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RenderContractsTest {
    @Test
    fun mapsAllFourOsuRulesets() {
        assertEquals(RenderMode.OSU, RenderMode.fromOsuMode(0))
        assertEquals(RenderMode.TAIKO, RenderMode.fromOsuMode(1))
        assertEquals(RenderMode.CATCH, RenderMode.fromOsuMode(2))
        assertEquals(RenderMode.MANIA, RenderMode.fromOsuMode(3))
        assertNull(RenderMode.fromOsuMode(4))
    }

    @Test
    fun difficultyKeyRoundTripsNamesContainingSeparators() {
        val key = RenderDifficultyKey(RenderMode.MANIA, "LN: 4K [Hard]: 2")

        assertEquals(key, RenderDifficultyKey.decode(key.encode()))
    }

    @Test
    fun malformedDifficultyKeysAreRejected() {
        assertNull(RenderDifficultyKey.decode("Hard"))
        assertNull(RenderDifficultyKey.decode("v1:not-a-mode:Hard"))
        assertNull(RenderDifficultyKey.decode("v1:0:"))
        assertNull(RenderDifficultyKey.decode("v2:0:Hard"))
    }

    @Test
    fun encodedSelectionWinsEvenWhenTheLegacyNameIsAmbiguous() {
        val catalog = catalog()
        val taiko = RenderDifficultyKey(RenderMode.TAIKO, "Hard")

        assertEquals(taiko, catalog.resolveSavedDifficulty(taiko.encode()))
    }

    @Test
    fun uniqueLegacyNameResolvesToItsMode() {
        val catalog = catalog()

        assertEquals(
            RenderDifficultyKey(RenderMode.CATCH, "Overdose"),
            catalog.resolveSavedDifficulty("Overdose")
        )
    }

    @Test
    fun ambiguousLegacyNameFallsBackToTheDefault() {
        val catalog = catalog()

        assertEquals(catalog.defaultDifficultyKey, catalog.resolveSavedDifficulty("Hard"))
    }

    @Test
    fun unavailableOrMalformedSelectionFallsBackToTheDefault() {
        val catalog = catalog()

        assertEquals(
            catalog.defaultDifficultyKey,
            catalog.resolveSavedDifficulty(RenderDifficultyKey(RenderMode.MANIA, "4K").encode())
        )
        assertEquals(catalog.defaultDifficultyKey, catalog.resolveSavedDifficulty("v1:0:"))
        assertEquals(catalog.defaultDifficultyKey, catalog.resolveSavedDifficulty(null))
    }

    private fun catalog(): RenderCatalog {
        val difficulties = listOf(
            difficulty(RenderMode.OSU, "Hard"),
            difficulty(RenderMode.TAIKO, "Hard"),
            difficulty(RenderMode.CATCH, "Overdose")
        )
        return RenderCatalog(
            beatmapSetId = 42,
            audioFilename = "audio.mp3",
            difficulties = difficulties,
            defaultDifficultyKey = difficulties.first().key
        )
    }

    private fun difficulty(mode: RenderMode, name: String) = RenderDifficulty(
        key = RenderDifficultyKey(mode, name),
        path = "${mode.osuMode}-$name.osu",
        beatmapSetId = 42,
        audioFilename = "audio.mp3"
    )
}
