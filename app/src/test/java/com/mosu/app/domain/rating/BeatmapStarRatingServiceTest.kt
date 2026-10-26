package com.mosu.app.domain.rating

import com.mosu.app.data.api.model.BeatmapDetail
import com.mosu.app.data.api.model.BeatmapsetDetail
import com.mosu.app.data.api.model.Covers
import com.mosu.app.data.api.model.SayobotBeatmap
import com.mosu.app.data.api.model.SayobotDetailData
import com.mosu.app.data.db.BeatmapStarRatingDao
import com.mosu.app.data.db.BeatmapStarRatingEntity
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatmapStarRatingServiceTest {
    @Test
    fun fallbackSourceFailureIsContainedAndLeavesTheCacheEmpty() = runBlocking {
        val dao = FakeStarRatingDao()
        val service = BeatmapStarRatingService(
            dao,
            source(
                official = { throw IOException("official unavailable") },
                fallback = { throw IOException("fallback unavailable") }
            )
        )

        val ratings = service.cacheRatingsForDownload(
            beatmapSetId = 42,
            officialDetail = null
        )

        assertTrue(ratings.isEmpty())
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun refreshUsesOfficialValuesAndFallbackOnlyForMissingDifficulties() = runBlocking {
        val dao = FakeStarRatingDao()
        val service = BeatmapStarRatingService(
            dao,
            source(
                official = { officialDetail(101L to 5.68253f) },
                fallback = { sayobotDetail(101L to 5.9, 102L to 6.1) }
            )
        )

        val ratings = service.refreshMissingRatings(
            beatmapSetId = 42,
            requiredBeatmapIds = setOf(101L, 102L)
        )

        assertEquals(5.68253f, ratings.getValue(101L))
        assertEquals(6.1f, ratings.getValue(102L))
    }

    @Test
    fun completeOfficialResponseDoesNotCallFallback() = runBlocking {
        val dao = FakeStarRatingDao()
        val service = BeatmapStarRatingService(
            dao,
            source(
                official = { officialDetail(101L to 5.68253f) },
                fallback = { throw IOException("fallback should not be called") }
            )
        )

        val ratings = service.refreshMissingRatings(
            beatmapSetId = 42,
            requiredBeatmapIds = setOf(101L)
        )

        assertEquals(mapOf(101L to 5.68253f), ratings)
    }

    private fun source(
        official: suspend (Long) -> BeatmapsetDetail,
        fallback: suspend (Long) -> SayobotDetailData
    ) = object : BeatmapStarRatingSource {
        override suspend fun getOfficialDetail(beatmapSetId: Long): BeatmapsetDetail =
            official(beatmapSetId)

        override suspend fun getFallbackDetail(beatmapSetId: Long): SayobotDetailData =
            fallback(beatmapSetId)
    }

    private fun officialDetail(vararg ratings: Pair<Long, Float>) = BeatmapsetDetail(
        id = 42,
        title = "Title",
        artist = "Artist",
        creator = "Creator",
        covers = covers(),
        status = "ranked",
        beatmaps = ratings.map { (id, rating) ->
            BeatmapDetail(
                id = id,
                version = "Difficulty $id",
                mode = "osu",
                difficultyRating = rating,
                url = null,
                beatmapsetId = 42,
                playCount = 0,
                status = "ranked"
            )
        }
    )

    private fun sayobotDetail(vararg ratings: Pair<Long, Double>) = SayobotDetailData(
        sid = 42,
        title = "Title",
        artist = "Artist",
        creator = "Creator",
        approved = 1,
        bidData = ratings.map { (id, rating) ->
            SayobotBeatmap(
                bid = id,
                version = "Difficulty $id",
                star = rating,
                ar = 9.0,
                cs = 4.0,
                hp = 5.0,
                od = 8.0,
                mode = 0
            )
        }
    )

    private fun covers() = Covers(
        coverUrl = "https://example.com/cover.jpg",
        listUrl = "https://example.com/list.jpg"
    )
}

private class FakeStarRatingDao : BeatmapStarRatingDao {
    val rows = mutableMapOf<Pair<Long, Long>, BeatmapStarRatingEntity>()

    override suspend fun getForSet(setId: Long): List<BeatmapStarRatingEntity> =
        rows.values.filter { it.beatmapSetId == setId }

    override suspend fun upsertAll(ratings: List<BeatmapStarRatingEntity>) {
        ratings.forEach { rows[it.beatmapSetId to it.beatmapId] = it }
    }

    override suspend fun deleteForSet(setId: Long) {
        rows.keys.removeAll { it.first == setId }
    }

    override suspend fun deleteAll() {
        rows.clear()
    }
}
