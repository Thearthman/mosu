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
import org.junit.Assert.assertNull
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

        assertNull(ratings.ratingFor(query(101L)))
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun refreshUsesOfficialValuesAndFallbackOnlyForMissingDifficulties() = runBlocking {
        val dao = FakeStarRatingDao()
        val service = BeatmapStarRatingService(
            dao,
            source(
                official = {
                    officialDetail(
                        beatmapSetId = 42,
                        beatmap(101L, rating = 5.68253f)
                    )
                },
                fallback = { sayobotDetail(101L to 5.9, 102L to 6.1) }
            )
        )

        val ratings = service.refreshMissingRatings(
            beatmapSetId = 42,
            requiredDifficulties = listOf(query(101L), query(102L))
        )

        assertEquals(5.68253f, ratings.ratingFor(query(101L)))
        assertEquals(6.1f, ratings.ratingFor(query(102L)))
    }

    @Test
    fun completeOfficialResponseDoesNotCallFallback() = runBlocking {
        val dao = FakeStarRatingDao()
        val service = BeatmapStarRatingService(
            dao,
            source(
                official = {
                    officialDetail(
                        beatmapSetId = 42,
                        beatmap(101L, rating = 5.68253f)
                    )
                },
                fallback = { throw IOException("fallback should not be called") }
            )
        )

        val ratings = service.refreshMissingRatings(
            beatmapSetId = 42,
            requiredDifficulties = listOf(query(101L))
        )

        assertEquals(5.68253f, ratings.ratingFor(query(101L)))
    }

    @Test
    fun legacyMapsResolveRatingsByUniqueModeAndDifficultyName() = runBlocking {
        val dao = FakeStarRatingDao()
        val service = BeatmapStarRatingService(
            dao,
            source(
                official = {
                    officialDetail(
                        beatmapSetId = 43,
                        beatmap(101L, "Easy", 1.505f),
                        beatmap(102L, "Gladi's Hard", 3.518f),
                        beatmap(103L, "Insane", 4.747f),
                        beatmap(104L, "Normal", 2.389f),
                        beatmap(105L, "Sp3's Taiko", 3.855f, mode = "taiko")
                    )
                },
                fallback = { throw IOException("fallback should not be called") }
            )
        )
        val required = listOf(
            query(beatmapId = null, difficultyName = "Easy", mode = 0),
            query(beatmapId = null, difficultyName = "Gladi's Hard", mode = 0),
            query(beatmapId = null, difficultyName = "Insane", mode = 0),
            query(beatmapId = null, difficultyName = "Normal", mode = 0)
        )

        val ratings = service.refreshMissingRatings(
            beatmapSetId = 43,
            requiredDifficulties = required
        )

        assertEquals(1.505f, ratings.ratingFor(required[0]))
        assertEquals(3.518f, ratings.ratingFor(required[1]))
        assertEquals(4.747f, ratings.ratingFor(required[2]))
        assertEquals(2.389f, ratings.ratingFor(required[3]))
        assertEquals(
            3.855f,
            ratings.ratingFor(
                query(beatmapId = null, difficultyName = "Sp3's Taiko", mode = 1)
            )
        )
    }

    @Test
    fun ambiguousLegacyDifficultyNameIsNotAssignedARating() = runBlocking {
        val dao = FakeStarRatingDao()
        val service = BeatmapStarRatingService(
            dao,
            source(
                official = {
                    officialDetail(
                        beatmapSetId = 44,
                        beatmap(101L, "Easy", 1.5f),
                        beatmap(102L, "Easy", 2.5f)
                    )
                },
                fallback = { throw IOException("fallback should not be called") }
            )
        )
        val legacyQuery = query(
            beatmapId = null,
            difficultyName = "Easy",
            mode = 0
        )

        val ratings = service.refreshMissingRatings(
            beatmapSetId = 44,
            requiredDifficulties = listOf(legacyQuery)
        )

        assertNull(ratings.ratingFor(legacyQuery))
    }

    @Test
    fun cachedRatingWithoutLegacyMetadataIsRefreshed() = runBlocking {
        val dao = FakeStarRatingDao().apply {
            rows[45L to 101L] = BeatmapStarRatingEntity(
                beatmapSetId = 45L,
                beatmapId = 101L,
                starRating = 5f
            )
        }
        val service = BeatmapStarRatingService(
            dao,
            source(
                official = {
                    officialDetail(
                        beatmapSetId = 45,
                        beatmap(101L, "Easy", 1.505f)
                    )
                },
                fallback = { throw IOException("fallback should not be called") }
            )
        )
        val legacyQuery = query(
            beatmapId = null,
            difficultyName = "Easy",
            mode = 0
        )

        val ratings = service.refreshMissingRatings(
            beatmapSetId = 45,
            requiredDifficulties = listOf(legacyQuery)
        )

        assertEquals(1.505f, ratings.ratingFor(legacyQuery))
        assertEquals("Easy", dao.rows[45L to 101L]?.difficultyName)
        assertEquals(0, dao.rows[45L to 101L]?.mode)
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

    private fun query(
        beatmapId: Long?,
        difficultyName: String = "Difficulty $beatmapId",
        mode: Int = 0
    ) = DifficultyRatingQuery(
        beatmapId = beatmapId,
        mode = mode,
        difficultyName = difficultyName
    )

    private fun officialDetail(
        beatmapSetId: Long = 42,
        vararg beatmaps: BeatmapDetail
    ) = BeatmapsetDetail(
        id = beatmapSetId,
        title = "Title",
        artist = "Artist",
        creator = "Creator",
        covers = covers(),
        status = "ranked",
        beatmaps = beatmaps.toList()
    )

    private fun beatmap(
        id: Long,
        difficultyName: String = "Difficulty $id",
        rating: Float,
        mode: String = "osu"
    ) = BeatmapDetail(
        id = id,
        version = difficultyName,
        mode = mode,
        difficultyRating = rating,
        url = null,
        beatmapsetId = 42,
        playCount = 0,
        status = "ranked"
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
