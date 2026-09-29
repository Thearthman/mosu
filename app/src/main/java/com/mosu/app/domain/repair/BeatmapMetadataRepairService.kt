package com.mosu.app.domain.repair

import android.content.Context
import android.util.Log
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.media.MosuBackupService
import com.mosu.app.data.repository.OsuRepository
import retrofit2.HttpException
import java.io.IOException

/**
 * Result of a metadata repair pass.
 *
 * @param failureReasons a histogram of skip reasons, e.g. {"HTTP 404": 2, "network": 1}.
 */
data class MetadataRepairResult(
    val total: Int,
    val repaired: Int,
    val skipped: Int,
    val failureReasons: Map<String, Int> = emptyMap()
)

/**
 * Debug/maintenance helper that re-fetches each locally stored beatmapset's
 * metadata from the API by its [com.mosu.app.data.db.BeatmapEntity.beatmapSetId]
 * (the canonical key) and overwrites the local set-level fields.
 *
 * Failures are per-set and non-fatal: a set that cannot be fetched is skipped and
 * its reason is counted, so a single bad id never aborts the whole pass.
 */
class BeatmapMetadataRepairService(
    private val context: Context,
    private val db: AppDatabase,
    private val repository: OsuRepository
) {
    suspend fun repairAll(
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): MetadataRepairResult {
        val setIds = db.beatmapDao().getDistinctSetIds()
        var repaired = 0
        val failureReasons = linkedMapOf<String, Int>()

        setIds.forEachIndexed { index, setId ->
            onProgress(index, setIds.size)
            try {
                val detail = repository.getBeatmapsetDetail(setId)
                db.beatmapDao().updateMetadataForSet(
                    setId = setId,
                    title = detail.title,
                    artist = detail.artist,
                    titleUnicode = detail.titleUnicode?.ifBlank { null },
                    artistUnicode = detail.artistUnicode?.ifBlank { null },
                    creator = detail.creator,
                    genreId = detail.genreId
                )
                repaired++
            } catch (e: Exception) {
                val reason = describeFailure(e)
                Log.w(TAG, "Skipped metadata repair for set $setId: $reason", e)
                failureReasons[reason] = (failureReasons[reason] ?: 0) + 1
            }
        }
        onProgress(setIds.size, setIds.size)

        if (repaired > 0) {
            // Keep the MediaStore recovery manifest in sync with the new metadata.
            runCatching { MosuBackupService.exportState(context, db) }
                .onFailure { Log.w(TAG, "Failed to refresh MediaStore manifest after repair", it) }
        }

        return MetadataRepairResult(
            total = setIds.size,
            repaired = repaired,
            skipped = setIds.size - repaired,
            failureReasons = failureReasons
        )
    }

    private fun describeFailure(e: Throwable): String = when (e) {
        is HttpException -> "HTTP ${e.code()}"
        is IOException -> "network"
        else -> e::class.java.simpleName
    }

    companion object {
        private const val TAG = "BeatmapMetadataRepair"
    }
}
