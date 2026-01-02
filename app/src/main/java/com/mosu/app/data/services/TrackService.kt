package com.mosu.app.data.services

import android.content.Context
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.data.db.PlaylistTrackEntity

/**
 * Comprehensive service for handling all track operations.
 * This coordinates data operations and keeps business logic separate from UI components.
 */
class TrackService {

    companion object {

        /**
         * Adds a track to the database and updates preserved list.
         */
        suspend fun addTrack(
            track: BeatmapEntity,
            db: AppDatabase,
            context: Context
        ) {
            // Add to database
            db.beatmapDao().insertBeatmap(track)

            // Add to preserved list for future restoration
            db.preservedBeatmapSetIdDao().insertPreservedSetId(
                com.mosu.app.data.db.PreservedBeatmapSetIdEntity(beatmapSetId = track.beatmapSetId)
            )

            // Update SharedPreferences backup
            val prefs = context.getSharedPreferences("preserved_beatmaps", Context.MODE_PRIVATE)
            val preservedSetIdsKey = "preserved_set_ids"
            val currentPrefs = prefs.getStringSet(preservedSetIdsKey, emptySet()) ?: emptySet()
            val updatedPrefs = currentPrefs + track.beatmapSetId.toString()
            prefs.edit().putStringSet(preservedSetIdsKey, updatedPrefs).apply()
        }

        /**
         * Deletes a track with preserved list management.
         * Handles database deletion, file cleanup, and preserved list updates.
         */
        suspend fun deleteTrack(
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

        /**
         * Adds a track to a playlist.
         */
        suspend fun addTrackToPlaylist(
            playlistId: Long,
            beatmapSetId: Long,
            db: AppDatabase
        ) {
            val playlistTrack = PlaylistTrackEntity(
                playlistId = playlistId,
                beatmapSetId = beatmapSetId
            )
            db.playlistDao().addTrack(playlistTrack)
        }

        /**
         * Removes a track from a playlist.
         */
        suspend fun removeTrackFromPlaylist(
            playlistId: Long,
            beatmapSetId: Long,
            db: AppDatabase
        ) {
            db.playlistDao().removeTrack(playlistId, beatmapSetId)
        }

        /**
         * Updates track download status in playlists.
         */
        suspend fun updateTrackDownloadStatus(
            beatmapSetId: Long,
            downloaded: Boolean,
            db: AppDatabase
        ) {
            db.playlistDao().updateTrackDownloadStatus(beatmapSetId, downloaded)
        }
    }
}
