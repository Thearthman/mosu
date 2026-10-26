package com.mosu.app.domain.rating

import com.mosu.app.data.api.model.BeatmapDetail
import com.mosu.app.data.api.model.BeatmapsetDetail
import com.mosu.app.data.api.model.Covers
import com.mosu.app.data.api.model.SayobotBeatmap
import com.mosu.app.data.api.model.SayobotDetailData
import org.junit.Assert.assertEquals
import org.junit.Test

class BeatmapStarRatingMappingsTest {
    @Test
    fun officialMappingKeepsOnlyStandardDifficulties() {
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
                    beatmap(id = 103, mode = "osu", rating = 0f),
                    beatmap(id = 104, mode = "osu", rating = Float.NaN)
                )
            )
        )

        assertEquals(mapOf(101L to 2.5f), ratings)
    }

    @Test
    fun sayobotMappingUsesBeatmapIdsAndStandardMode() {
        val ratings = BeatmapStarRatingMappings.fromSayobot(
            SayobotDetailData(
                sid = 42,
                title = "Title",
                artist = "Artist",
                creator = "Creator",
                approved = 1,
                bidData = listOf(
                    sayobotBeatmap(id = 101, mode = 0, stars = 5.9),
                    sayobotBeatmap(id = 102, mode = 3, stars = 9.1)
                )
            )
        )

        assertEquals(mapOf(101L to 5.9f), ratings)
    }

    @Test
    fun officialValuesTakePrecedenceWhenMergingFallbackRatings() {
        val merged = BeatmapStarRatingMappings.merge(
            official = mapOf(101L to 5.68253f),
            fallback = mapOf(101L to 5.9f, 102L to 3.2f)
        )

        assertEquals(
            mapOf(101L to 5.68253f, 102L to 3.2f),
            merged
        )
    }

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
