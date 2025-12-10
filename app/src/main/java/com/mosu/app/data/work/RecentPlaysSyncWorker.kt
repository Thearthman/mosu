package com.mosu.app.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Worker
import com.mosu.app.data.TokenManager
import com.mosu.app.data.api.RetrofitClient
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.RecentPlayEntity
import com.mosu.app.data.repository.OsuRepository
import kotlinx.coroutines.flow.firstOrNull
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class RecentPlaysSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val token = TokenManager(applicationContext).accessToken.firstOrNull() ?: return Result.retry()

        val db = AppDatabase.getDatabase(applicationContext)
        val repository = OsuRepository(db.searchCacheDao())
        return runCatching {
            val user = repository.getMe(token)
            val recentScores = RetrofitClient.api.getUserRecentScores(
                authHeader = "Bearer $token",
                userId = user.id.toString(),
                limit = 100,
                includeFails = true
            )

            val cutoff = OffsetDateTime.now().minusDays(7)
            val seen = mutableSetOf<Long>()
            val entities = recentScores.mapNotNull { score ->
                val playedAt = score.createdAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: return@mapNotNull null
                if (playedAt.isBefore(cutoff)) return@mapNotNull null
                val beatmapset = score.beatmap?.beatmapset ?: return@mapNotNull null
                if (!seen.add(beatmapset.id)) return@mapNotNull null
                RecentPlayEntity(
                    scoreId = score.scoreId,
                    beatmapSetId = beatmapset.id,
                    title = beatmapset.title,
                    artist = beatmapset.artist,
                    creator = beatmapset.creator,
                    coverUrl = beatmapset.covers.coverUrl,
                    playedAt = playedAt.toInstant().toEpochMilli()
                )
            }

            db.recentPlayDao().replaceAll(entities)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "recent-plays-sync"

        fun scheduleDaily(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val initialDelay = computeInitialDelayToNoon()

            val request = PeriodicWorkRequestBuilder<RecentPlaysSyncWorker>(
                24, TimeUnit.HOURS
            )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        private fun computeInitialDelayToNoon(): Long {
            val now = ZonedDateTime.now()
            val nextNoon = now.withHour(12).withMinute(0).withSecond(0).withNano(0).let {
                if (it.isBefore(now)) it.plusDays(1) else it
            }
            return Duration.between(now, nextNoon).toMillis().coerceAtLeast(0)
        }
    }
}

