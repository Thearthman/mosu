package com.mosu.app.domain.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatmapSearchModeTest {
    @Test
    fun `filter-provided modes use their concrete beatmapsets`() {
        listOf("played", "recent", "favorite", "most_played", "top_play").forEach { mode ->
            assertTrue(mode.usesFilterProvidedBeatmapset())
        }
    }

    @Test
    fun `generic search still uses title deduplication`() {
        assertFalse("all".usesFilterProvidedBeatmapset())
    }
}
