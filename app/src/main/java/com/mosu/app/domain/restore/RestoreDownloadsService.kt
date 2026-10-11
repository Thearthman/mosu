package com.mosu.app.domain.restore

import android.content.Context
import com.mosu.app.R
import com.mosu.app.data.TokenManager
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.domain.download.DownloadManager
import kotlinx.coroutines.flow.firstOrNull

data class RestoreDownloadsResult(
    val completed: Int,
    val total: Int,
    val enqueued: Int,
    val skipped: Int,
    val message: String
)

class RestoreDownloadsService(
    private val context: Context,
    private val db: AppDatabase,
    private val tokenManager: TokenManager,
    private val downloadManager: DownloadManager
) {
    suspend fun enqueueMissingPreservedDownloads(
        updateProgress: (Int, String) -> Unit
    ): RestoreDownloadsResult {
        updateProgress(0, context.getString(R.string.profile_restore_starting))

        val preservedSetIds = db.preservedBeatmapSetIdDao().getAllPreservedSetIds().firstOrNull().orEmpty()
        if (preservedSetIds.isEmpty()) {
            return RestoreDownloadsResult(
                completed = 0,
                total = 0,
                enqueued = 0,
                skipped = 0,
                message = context.getString(R.string.profile_restore_no_beatmaps)
            )
        }

        val accessToken = tokenManager.getAccessToken()
        var completed = 0
        var enqueued = 0
        var skipped = 0
        val total = preservedSetIds.size

        for (preservedSetId in preservedSetIds) {
            val existingTracks = db.beatmapDao().getTracksForSet(preservedSetId.beatmapSetId)
            if (existingTracks.isEmpty()) {
                downloadManager.enqueue(
                    setId = preservedSetId.beatmapSetId,
                    // We only know the set id here, so pass no metadata at all.
                    // These fields are non-authoritative hints; the download
                    // service derives the real title/artist/creator from the
                    // downloaded file (and genre from the API by set id).
                    title = "",
                    artist = "",
                    creator = "",
                    accessToken = accessToken,
                    genreId = null,
                    coverUrl = null
                )
                enqueued++
            } else {
                skipped++
            }
            completed++
        }

        // "Completed" here only means we finished enqueuing; the actual downloads
        // run in the background, so phrase the progress message accordingly.
        val message = context.getString(R.string.profile_restore_completed_progress, enqueued, total)
        updateProgress(100, message)
        return RestoreDownloadsResult(
            completed = completed,
            total = total,
            enqueued = enqueued,
            skipped = skipped,
            message = message
        )
    }
}
