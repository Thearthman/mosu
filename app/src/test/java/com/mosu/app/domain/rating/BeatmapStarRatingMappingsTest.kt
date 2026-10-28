package com.mosu.app.domain.rating

import com.mosu.app.data.api.model.BeatmapDetail
import com.mosu.app.data.api.model.BeatmapsetDetail
import com.mosu.app.data.api.model.Covers
import com.mosu.app.data.api.model.SayobotBeatmap
import com.mosu.app.data.api.model.SayobotDetailData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BeatmapStarRatingMappingsTest {
    @Test
    fun officialMappingKeepsAllRulesetsAndRejectsInvalidRatings() {
        val ratings = BeatmapStarRatingMappings.fromOfficial(
            BeatmapsetDetail(
                id = 42,
                title = "Title",
                artist = "Artist",
                creator = "Creator",
                covers = covers(),
                status = "ranked",
                beatmaps = listOf(
                    beatmap(id = 101, mode = "osu", rating = 2.5f),
                    beatmap(id = 102, mode = "taiko", rating = 7.0f),
                    beatmap(id = 103, mode = "fruits", rating = 4.2f),
                    beatmap(id = 104, mode = "mania", rating = 6.8f),
                    beatmap(id = 105, mode = "osu", rating = 0f),
                    beatmap(id = 106, mode = "osu", rating = Float.NaN)
                )
            )
        )

        assertEquals(2.5f, ratings.getValue(101L).starRating)
        assertEquals(7.0f, ratings.getValue(102L).starRating)
        assertEquals(4.2f, ratings.getValue(103L).starRating)
        assertEquals(6.8f, ratings.getValue(104L).starRating)
        assertEquals(listOf("Difficulty 101", "Difficulty 102", "Difficulty 103", "Difficulty 104"),
            ratings.values.map { it.difficultyName })
        assertEquals(listOf(0, 1, 2, 3), ratings.values.map { it.mode })
        assertEquals(4, ratings.size)
        assertNull(ratings[105L])
        assertNull(ratings[106L])
    }

    @Test
    fun sayobotMappingUsesBeatmapIdsAcrossAllRulesets() {
        val ratings = BeatmapStarRatingMappings.fromSayobot(
            SayobotDetailData(
                sid = 42,
                title = "Title",
                artist = "Artist",
                creator = "Creator",
                approved = 1,
                bidData = listOf(
                    sayobotBeatmap(id = 101, mode = 0, stars = 5.9),
                    sayobotBeatmap(id = 102, mode = 1, stars = 6.2),
                    sayobotBeatmap(id = 103, mode = 2, stars = 7.1),
                    sayobotBeatmap(id = 104, mode = 3, stars = 9.1)
                )
            )
        )

        assertEquals(5.9f, ratings.getValue(101L).starRating)
        assertEquals(6.2f, ratings.getValue(102L).starRating)
        assertEquals(7.1f, ratings.getValue(103L).starRating)
        assertEquals(9.1f, ratings.getValue(104L).starRating)
        assertEquals(listOf(0, 1, 2, 3), ratings.values.map { it.mode })
        assertEquals(4, ratings.size)
    }

    @Test
    fun officialValuesTakePrecedenceWhenMergingFallbackRatings() {
        val merged = BeatmapStarRatingMappings.merge(
            official = mapOf(101L to rating(101L, 5.68253f)),
            fallback = mapOf(101L to rating(101L, 5.9f), 102L to rating(102L, 3.2f))
        )

        assertEquals(5.68253f, merged.getValue(101L).starRating)
        assertEquals(3.2f, merged.getValue(102L).starRating)
        assertEquals(2, merged.size)
    }

    private fun rating(beatmapId: Long, stars: Float) = DifficultyStarRating(
        beatmapId = beatmapId,
        starRating = stars,
        difficultyName = "Difficulty $beatmapId",
        mode = 0
    )

    private fun beatmap(id: Long, mode: String, rating: Float) = BeatmapDetail(
        id = id,
        version = "Difficulty $id",
        mode = mode,
        difficultyRating = rating,
        url = null,
        beatmapsetId = 42,
        playCount = 0,
        status = "ranked"
    )

    private fun sayobotBeatmap(id: Long, mode: Int, stars: Double) = SayobotBeatmap(
        bid = id,
        version = "Difficulty $id",
        star = stars,
        ar = 9.0,
        cs = 4.0,
        hp = 5.0,
        od = 8.0,
        mode = mode
    )

    private fun covers() = Covers(
        coverUrl = "https://example.com/cover.jpg",
        listUrl = "https://example.com/list.jpg"
    )
}
