package com.mosu.app.domain

import android.content.Context
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.BeatmapEntity

/**
 * Service for handling track deletion operations and preserved list management.
 * This keeps business logic separate from UI components.
 */
class TrackDeletionService {

    companion object {

        /**
         * Common function for deleting tracks with preserved list management.
         * Handles database deletion, file cleanup, and preserved list updates.
         */
        suspend fun deleteTrackWithPreservedListUpdate(
            track: BeatmapEntity,
            db: AppDatabase,
            context: Context
        ) {
            // Delete from database
            db.beatmapDao().deleteBeatmap(track)
            // Delete audio and cover files
            java.io.File(track.audioPath).delete()
            java.io.File(track.coverPath).delete()

            // Remove from preserved list if no more tracks from this set remain
            val remainingTracks = db.beatmapDao().getTracksForSet(track.beatmapSetId)
            if (remainingTracks.isEmpty()) {
                db.preservedBeatmapSetIdDao().deletePreservedSetId(track.beatmapSetId)

                // Also update SharedPreferences backup
                val prefs = context.getSharedPreferences("preserved_beatmaps", Context.MODE_PRIVATE)
                val preservedSetIdsKey = "preserved_set_ids"
                val currentPrefs = prefs.getStringSet(preservedSetIdsKey, emptySet()) ?: emptySet()
                val updatedPrefs = currentPrefs.filter { it != track.beatmapSetId.toString() }.toSet()
                prefs.edit().putStringSet(preservedSetIdsKey, updatedPrefs).apply()
            }
        }
    }
}
