package com.mosu.app.domain.rating

import com.mosu.app.data.api.model.BeatmapDetail
import com.mosu.app.data.api.model.BeatmapsetDetail
import com.mosu.app.data.api.model.SayobotDetailData
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.BeatmapStarRatingEntity
import com.mosu.app.data.db.BeatmapStarRatingDao
import com.mosu.app.data.repository.OsuRepository
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A renderer map whose rating needs to be resolved. */
data class DifficultyRatingQuery(
    val beatmapId: Long?,
    val mode: Int,
    val difficultyName: String
)

data class DifficultyStarRating(
    val beatmapId: Long,
    val starRating: Float,
    val difficultyName: String?,
    val mode: Int?
)

/**
 * Resolves ratings by beatmap id first, then by a unique ruleset + version
 * name. The name fallback exists for legacy `.osu` files that predate
 * BeatmapID metadata.
 */
class BeatmapStarRatingCatalog(
    ratings: List<DifficultyStarRating> = emptyList()
) {
    private val ratingsById = ratings.associateBy { it.beatmapId }
    private val ratingsByModeAndName = ratings
        .asSequence()
        .filter { it.mode != null && !it.difficultyName.isNullOrBlank() }
        .groupBy { rating ->
            rating.mode!! to normalizeDifficultyName(rating.difficultyName!!)
        }

    fun ratingFor(query: DifficultyRatingQuery): Float? =
        ratingFor(query.beatmapId, query.mode, query.difficultyName)

    fun ratingFor(beatmapId: Long?, mode: Int, difficultyName: String): Float? {
        beatmapId?.let(ratingsById::get)?.let { return it.starRating }

        val matches = ratingsByModeAndName[
            mode to normalizeDifficultyName(difficultyName)
        ].orEmpty()
        return matches.singleOrNull()?.starRating
    }

    fun hasRatingFor(query: DifficultyRatingQuery): Boolean =
        ratingFor(query) != null

    companion object {
        val Empty = BeatmapStarRatingCatalog()
    }
}

private fun normalizeDifficultyName(value: String): String =
    value.trim().lowercase(Locale.ROOT)

/**
 * Persisted star ratings for all difficulties in one beatmapset.
 *
 * Ratings are fetched asynchronously and cached by beatmap id because one
 * library row can correspond to several difficulties that share an audio file.
 * Consumers decide which rulesets to display.
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

    suspend fun getCachedRatings(beatmapSetId: Long): BeatmapStarRatingCatalog =
        runCatching {
            BeatmapStarRatingCatalog(
                dao.getForSet(beatmapSetId).map { entity ->
                    DifficultyStarRating(
                        beatmapId = entity.beatmapId,
                        starRating = entity.starRating,
                        difficultyName = entity.difficultyName,
                        mode = entity.mode
                    )
                }
            )
        }.getOrDefault(BeatmapStarRatingCatalog.Empty)

    /**
     * Refreshes at most once for [beatmapSetId] while another caller is already
     * refreshing it. The official osu! values are preferred; Sayobot only fills
     * ids that the official response did not resolve.
     */
    suspend fun refreshMissingRatings(
        beatmapSetId: Long,
        requiredDifficulties: Collection<DifficultyRatingQuery>
    ): BeatmapStarRatingCatalog {
        if (requiredDifficulties.isEmpty()) return getCachedRatings(beatmapSetId)

        if (!backoff.isReady(beatmapSetId)) {
            return getCachedRatings(beatmapSetId)
        }

        return mutexFor(beatmapSetId).withLock {
            var cached = getCachedRatings(beatmapSetId)
            if (requiredDifficulties.all(cached::hasRatingFor)) {
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
            if (!requiredDifficulties.all(catalogOf(resolved)::hasRatingFor)) {
                val fallback = runCatching {
                    BeatmapStarRatingMappings.fromSayobot(
                        source.getFallbackDetail(beatmapSetId)
                    )
                }.getOrNull()
                resolved = BeatmapStarRatingMappings.merge(official.orEmpty(), fallback.orEmpty())
            }

            if (resolved.isNotEmpty()) {
                runCatching { persist(beatmapSetId, resolved.values) }
                cached = getCachedRatings(beatmapSetId)
            }

            if (requiredDifficulties.all(cached::hasRatingFor)) {
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
    ): BeatmapStarRatingCatalog {
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
        if (resolved.isNotEmpty()) persist(beatmapSetId, resolved.values)
        return getCachedRatings(beatmapSetId)
    }

    private fun mutexFor(beatmapSetId: Long): Mutex =
        processSetMutexes.computeIfAbsent(beatmapSetId) { Mutex() }

    private suspend fun persist(
        beatmapSetId: Long,
        ratings: Collection<DifficultyStarRating>
    ) {
        dao.upsertAll(
            ratings.map { rating ->
                BeatmapStarRatingEntity(
                    beatmapSetId = beatmapSetId,
                    beatmapId = rating.beatmapId,
                    starRating = rating.starRating,
                    difficultyName = rating.difficultyName,
                    mode = rating.mode
                )
            }
        )
    }

    private fun catalogOf(
        ratings: Map<Long, DifficultyStarRating>
    ): BeatmapStarRatingCatalog = BeatmapStarRatingCatalog(ratings.values.toList())

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
    fun fromOfficial(detail: BeatmapsetDetail): Map<Long, DifficultyStarRating> =
        fromBeatmaps(detail.beatmaps)

    fun fromSayobot(detail: SayobotDetailData): Map<Long, DifficultyStarRating> =
        fromBeatmaps(detail.toBeatmapsetDetail().beatmaps)

    fun merge(
        official: Map<Long, DifficultyStarRating>,
        fallback: Map<Long, DifficultyStarRating>
    ): Map<Long, DifficultyStarRating> = fallback + official

    private fun fromBeatmaps(
        beatmaps: List<BeatmapDetail>
    ): Map<Long, DifficultyStarRating> =
        beatmaps.asSequence()
            .filter { it.id > 0L && it.difficultyRating.isFinite() && it.difficultyRating > 0f }
            .associate { beatmap ->
                beatmap.id to DifficultyStarRating(
                    beatmapId = beatmap.id,
                    starRating = beatmap.difficultyRating,
                    difficultyName = beatmap.version,
                    mode = modeInt(beatmap.mode)
                )
            }

    private fun modeInt(mode: String?): Int? = when (
        mode?.trim()?.lowercase(Locale.ROOT)
    ) {
        "osu" -> 0
        "taiko" -> 1
        "fruits", "catch" -> 2
        "mania" -> 3
        else -> null
    }
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
