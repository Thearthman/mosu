package com.mosu.app.domain.rating

import com.mosu.app.data.api.model.BeatmapDetail
import com.mosu.app.data.api.model.BeatmapsetDetail
import com.mosu.app.data.api.model.SayobotDetailData
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.BeatmapStarRatingEntity
import com.mosu.app.data.db.BeatmapStarRatingDao
import com.mosu.app.data.repository.OsuRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persisted star ratings for standard-mode difficulties in one beatmapset.
 *
 * Ratings are fetched asynchronously and cached by beatmap id because one
 * library row can correspond to several difficulties that share an audio file.
 */
class BeatmapStarRatingService internal constructor(
    private val dao: BeatmapStarRatingDao,
    private val source: BeatmapStarRatingSource
) {
    constructor(
        db: AppDatabase,
        repository: OsuRepository = OsuRepository()
    ) : this(
        dao = db.beatmapStarRatingDao(),
        source = RepositoryBeatmapStarRatingSource(repository)
    )

    private val backoff = processBackoff

    suspend fun getCachedRatings(beatmapSetId: Long): Map<Long, Float> =
        runCatching {
            dao
                .getForSet(beatmapSetId)
                .associate { it.beatmapId to it.starRating }
        }.getOrDefault(emptyMap())

    /**
     * Refreshes at most once for [beatmapSetId] while another caller is already
     * refreshing it. The official osu! values are preferred; Sayobot only fills
     * ids that the official response did not resolve.
     */
    suspend fun refreshMissingRatings(
        beatmapSetId: Long,
        requiredBeatmapIds: Set<Long>
    ): Map<Long, Float> {
        if (requiredBeatmapIds.isEmpty()) return getCachedRatings(beatmapSetId)

        if (!backoff.isReady(beatmapSetId)) {
            return getCachedRatings(beatmapSetId)
        }

        return mutexFor(beatmapSetId).withLock {
            var cached = getCachedRatings(beatmapSetId)
            if (requiredBeatmapIds.all(cached::containsKey)) {
                backoff.recordResolved(beatmapSetId)
                return@withLock cached
            }

            // Another playback effect may have completed a refresh while this
            // caller was waiting for the per-set lock.
            if (!backoff.isReady(beatmapSetId)) return@withLock cached

            val official = runCatching {
                BeatmapStarRatingMappings.fromOfficial(
                    source.getOfficialDetail(beatmapSetId)
                )
            }.getOrNull()

            var resolved = official.orEmpty()
            if (!requiredBeatmapIds.all(resolved::containsKey)) {
                val fallback = runCatching {
                    BeatmapStarRatingMappings.fromSayobot(
                        source.getFallbackDetail(beatmapSetId)
                    )
                }.getOrNull()
                resolved = BeatmapStarRatingMappings.merge(official.orEmpty(), fallback.orEmpty())
            }

            if (resolved.isNotEmpty()) {
                runCatching { persist(beatmapSetId, resolved) }
                cached = getCachedRatings(beatmapSetId)
            }

            if (requiredBeatmapIds.all(cached::containsKey)) {
                backoff.recordResolved(beatmapSetId)
            } else {
                backoff.recordIncomplete(beatmapSetId)
            }
            cached
        }
    }

    /**
     * Download-path entry point. The caller may already have fetched the official
     * detail for genre resolution, so it is reused here to avoid a duplicate call.
     */
    suspend fun cacheRatingsForDownload(
        beatmapSetId: Long,
        officialDetail: BeatmapsetDetail?
    ): Map<Long, Float> {
        val official = officialDetail?.let(BeatmapStarRatingMappings::fromOfficial).orEmpty()
        var resolved = official
        if (resolved.isEmpty()) {
            val fallback = runCatching {
                BeatmapStarRatingMappings.fromSayobot(
                    source.getFallbackDetail(beatmapSetId)
                )
            }.getOrNull().orEmpty()
            resolved = BeatmapStarRatingMappings.merge(official, fallback)
        }
        if (resolved.isNotEmpty()) persist(beatmapSetId, resolved)
        return getCachedRatings(beatmapSetId)
    }

    private fun mutexFor(beatmapSetId: Long): Mutex =
        processSetMutexes.computeIfAbsent(beatmapSetId) { Mutex() }

    private suspend fun persist(beatmapSetId: Long, ratings: Map<Long, Float>) {
        dao.upsertAll(
            ratings.map { (beatmapId, stars) ->
                BeatmapStarRatingEntity(
                    beatmapSetId = beatmapSetId,
                    beatmapId = beatmapId,
                    starRating = stars
                )
            }
        )
    }

    private companion object {
        // Backoff and in-flight coalescing are process-local by design. Both
        // reset naturally when the app process restarts.
        val processBackoff = RatingRefreshBackoff()
        val processSetMutexes = ConcurrentHashMap<Long, Mutex>()
    }
}

internal interface BeatmapStarRatingSource {
    suspend fun getOfficialDetail(beatmapSetId: Long): BeatmapsetDetail
    suspend fun getFallbackDetail(beatmapSetId: Long): SayobotDetailData
}

private class RepositoryBeatmapStarRatingSource(
    private val repository: OsuRepository
) : BeatmapStarRatingSource {
    override suspend fun getOfficialDetail(beatmapSetId: Long): BeatmapsetDetail =
        repository.getBeatmapsetDetail(beatmapSetId)

    override suspend fun getFallbackDetail(beatmapSetId: Long): SayobotDetailData =
        repository.getSayobotBeatmapsetDetail(beatmapSetId)
}

internal object BeatmapStarRatingMappings {
    fun fromOfficial(detail: BeatmapsetDetail): Map<Long, Float> =
        fromBeatmaps(detail.beatmaps)

    fun fromSayobot(detail: SayobotDetailData): Map<Long, Float> =
        fromBeatmaps(detail.toBeatmapsetDetail().beatmaps)

    fun merge(
        official: Map<Long, Float>,
        fallback: Map<Long, Float>
    ): Map<Long, Float> = fallback + official

    private fun fromBeatmaps(beatmaps: List<BeatmapDetail>): Map<Long, Float> =
        beatmaps.asSequence()
            .filter { it.mode.equals("osu", ignoreCase = true) }
            .filter { it.id > 0L && it.difficultyRating.isFinite() && it.difficultyRating > 0f }
            .associate { it.id to it.difficultyRating }
}

internal class RatingRefreshBackoff(
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val initialDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 3_600_000L
) {
    private data class State(
        val nextAttemptAtMs: Long,
        val nextDelayMs: Long
    )

    private val states = mutableMapOf<Long, State>()

    @Synchronized
    fun isReady(beatmapSetId: Long): Boolean {
        val state = states[beatmapSetId] ?: return true
        return clockMs() >= state.nextAttemptAtMs
    }

    @Synchronized
    fun recordIncomplete(beatmapSetId: Long) {
        val delay = states[beatmapSetId]?.nextDelayMs ?: initialDelayMs
        val nextDelay = if (delay >= maxDelayMs / 2L) {
            maxDelayMs
        } else {
            (delay * 2L).coerceAtMost(maxDelayMs)
        }
        states[beatmapSetId] = State(
            nextAttemptAtMs = clockMs() + delay,
            nextDelayMs = nextDelay
        )
    }

    @Synchronized
    fun recordResolved(beatmapSetId: Long) {
        states.remove(beatmapSetId)
    }
}
