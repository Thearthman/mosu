package com.mosu.app.ui.components

import com.mosu.app.data.api.model.BestScore
import com.mosu.app.data.db.RecentPlayEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreRowModelsTest {
    @Test
    fun `display accuracy converts normalized score accuracy to percent`() {
        assertEquals(98.25f, 0.9825f.toDisplayAccuracyPercent())
    }

    @Test
    fun `best score mapping preserves accuracy`() {
        val row = BestScore(accuracy = 0.9825f).toBeatmapSetScoreData()

        assertEquals(0.9825f, row.accuracy)
    }

    @Test
    fun `cached recent score mapping preserves accuracy`() {
        val row = RecentPlayEntity(
            beatmapSetId = 42,
            title = "Title",
            artist = "Artist",
            creator = "Creator",
            accuracy = 0.975f,
            playedAt = 1L
        ).toBeatmapSetData()

        assertEquals(0.975f, row.score?.accuracy)
    }
}
