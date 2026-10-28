package com.mosu.app.domain.model

import com.google.gson.Gson
import com.mosu.app.data.api.model.BeatmapDetail
import com.mosu.app.data.api.model.BeatmapsetCompact
import com.mosu.app.data.api.model.Covers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatmapsetSelectionTest {
    @Test
    fun `total play count sums every difficulty`() {
        val beatmapset = beatmapset(
            id = 1L,
            playCounts = listOf(120, 30, 5)
        )

        assertEquals(155L, beatmapset.totalBeatmapPlayCount())
    }

    @Test
    fun `total play count does not overflow when a set has very popular difficulties`() {
        val beatmapset = beatmapset(
            id = 1L,
            playCounts = listOf(Int.MAX_VALUE, 1)
        )

        assertEquals(Int.MAX_VALUE.toLong() + 1L, beatmapset.totalBeatmapPlayCount())
    }

    @Test
    fun `Gson leaves omitted optional beatmaps null`() {
        val beatmapset = Gson().fromJson(
            """
            {
              "id": 1,
              "title": "Song",
              "artist": "Artist",
              "creator": "Creator",
              "covers": {
                "cover": "https://example.com/cover.jpg",
                "list": "https://example.com/list.jpg"
              },
              "status": "ranked"
            }
            """.trimIndent(),
            BeatmapsetCompact::class.java
        )

        assertNull(beatmapset.beatmaps)
        assertEquals(0L, beatmapset.totalBeatmapPlayCount())
    }

    @Test
    fun `dedupe comparison does not crash when both sets omit beatmaps`() {
        val existing = beatmapset(id = 1L, playCounts = emptyList()).copy(beatmaps = null)
        val candidate = beatmapset(id = 2L, playCounts = emptyList()).copy(beatmaps = null)

        assertFalse(
            shouldReplaceDedupedCandidate(
                existing = existing,
                candidate = candidate,
                downloadedBeatmapSetIds = emptySet()
            )
        )
    }

    @Test
    fun `most played exact match compares totals across all difficulties`() {
        val lowerTotalWithMoreDifficulties = beatmapset(
            id = 1L,
            playCounts = listOf(50, 40, 10)
        )
        val higherTotal = beatmapset(
            id = 2L,
            playCounts = listOf(100, 20)
        )

        val selected = listOf(lowerTotalWithMoreDifficulties, higherTotal)
            .mostPlayedExactTitleArtist(title = "Song", artist = "Artist")

        assertEquals(higherTotal, selected)
    }

    @Test
    fun `most played exact match rejects different title or artist`() {
        val selected = listOf(
            beatmapset(id = 1L, title = "Different", playCounts = listOf(500)),
            beatmapset(id = 2L, artist = "Different", playCounts = listOf(500))
        ).mostPlayedExactTitleArtist(title = "Song", artist = "Artist")

        assertNull(selected)
    }

    @Test
    fun `dedupe replaces a lower-played candidate with the higher total`() {
        val existing = beatmapset(id = 1L, playCounts = listOf(80, 10))
        val candidate = beatmapset(id = 2L, playCounts = listOf(60, 50))

        assertTrue(
            shouldReplaceDedupedCandidate(
                existing = existing,
                candidate = candidate,
                downloadedBeatmapSetIds = emptySet()
            )
        )
        assertFalse(
            shouldReplaceDedupedCandidate(
                existing = candidate,
                candidate = existing,
                downloadedBeatmapSetIds = emptySet()
            )
        )
    }

    @Test
    fun `dedupe keeps a downloaded candidate regardless of play count`() {
        val downloaded = beatmapset(id = 1L, playCounts = listOf(1))
        val popular = beatmapset(id = 2L, playCounts = listOf(10_000))

        assertFalse(
            shouldReplaceDedupedCandidate(
                existing = downloaded,
                candidate = popular,
                downloadedBeatmapSetIds = setOf(downloaded.id)
            )
        )
        assertTrue(
            shouldReplaceDedupedCandidate(
                existing = popular,
                candidate = downloaded,
                downloadedBeatmapSetIds = setOf(downloaded.id)
            )
        )
    }

    private fun beatmapset(
        id: Long,
        title: String = "Song",
        artist: String = "Artist",
        playCounts: List<Int>
    ): BeatmapsetCompact {
        return BeatmapsetCompact(
            id = id,
            title = title,
            artist = artist,
            creator = "Creator",
            covers = Covers(coverUrl = "", listUrl = ""),
            status = "ranked",
            beatmaps = playCounts.mapIndexed { index, playCount ->
                BeatmapDetail(
                    id = id * 100 + index,
                    version = "Difficulty $index",
                    mode = "osu",
                    difficultyRating = 5f,
                    url = null,
                    beatmapsetId = id,
                    playCount = playCount,
                    status = "ranked"
                )
            }
        )
    }
}
