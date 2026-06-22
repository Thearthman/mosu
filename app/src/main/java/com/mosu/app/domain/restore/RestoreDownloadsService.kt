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
                message = context.getString(R.string.profile_restore_no_beatmaps)
            )
        }

        val accessToken = tokenManager.getCurrentAccessToken()
        var completed = 0
        val total = preservedSetIds.size

        for (preservedSetId in preservedSetIds) {
            val existingTracks = db.beatmapDao().getTracksForSet(preservedSetId.beatmapSetId)
            if (existingTracks.isEmpty()) {
                downloadManager.enqueue(
                    setId = preservedSetId.beatmapSetId,
                    title = "Beatmap ${preservedSetId.beatmapSetId}",
                    artist = "",
                    creator = "",
                    accessToken = accessToken,
                    genreId = null,
                    coverUrl = null
                )
            }
            completed++
        }

        val message = context.getString(R.string.profile_restore_completed_progress, completed, total)
        updateProgress(100, message)
        return RestoreDownloadsResult(completed = completed, total = total, message = message)
    }
}
