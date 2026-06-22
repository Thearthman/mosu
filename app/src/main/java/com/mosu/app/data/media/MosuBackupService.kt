package com.mosu.app.data.media

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.data.db.PlaylistEntity
import com.mosu.app.data.db.PlaylistTrackEntity
import com.mosu.app.data.db.PreservedBeatmapSetIdEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object MosuBackupService {
    private const val TAG = "MosuBackupService"
    private val gson = Gson()

    suspend fun exportState(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        runCatching {
            MediaStoreFileService(context).writeManifest(createManifestJson(db))
        }.onFailure { error ->
            Log.w(TAG, "Unable to export MediaStore manifest", error)
        }
    }

    suspend fun createManifestJson(db: AppDatabase): String = withContext(Dispatchers.IO) {
        val beatmaps = db.beatmapDao().getAllBeatmaps().first()
        val playlists = db.playlistDao().getPlaylists().first()
        val playlistTracks = db.playlistDao().getAllPlaylistTracks().first()

        val manifest = MosuManifest(
            exportedAt = System.currentTimeMillis(),
            tracks = beatmaps.map { it.toBackupTrack() },
            playlists = playlists.map { it.toBackupPlaylist() },
            playlistTracks = playlistTracks.map { it.toBackupPlaylistTrack() }
        )

        gson.toJson(manifest)
    }

    suspend fun restoreIfLibraryEmpty(context: Context, db: AppDatabase): Int = withContext(Dispatchers.IO) {
        val existingBeatmaps = db.beatmapDao().getAllBeatmaps().first()
        if (existingBeatmaps.isNotEmpty()) return@withContext 0

        val mediaStore = MediaStoreFileService(context)
        val json = mediaStore.readManifest() ?: return@withContext 0
        restoreFromJson(context, db, json, requireEmptyLibrary = false)
    }

    suspend fun restoreFromJson(
        context: Context,
        db: AppDatabase,
        json: String,
        requireEmptyLibrary: Boolean
    ): Int = withContext(Dispatchers.IO) {
        if (requireEmptyLibrary && db.beatmapDao().getAllBeatmaps().first().isNotEmpty()) {
            return@withContext 0
        }

        val manifest = runCatching { gson.fromJson(json, MosuManifest::class.java) }
            .onFailure { Log.w(TAG, "Unable to parse MediaStore manifest", it) }
            .getOrNull()
            ?: return@withContext 0

        val mediaStore = MediaStoreFileService(context)
        val restoredKeys = mutableSetOf<String>()
        manifest.tracks.forEach { backup ->
            if (!mediaStore.canRead(backup.audioUri)) return@forEach

            val coverUri = backup.coverUri.takeIf { mediaStore.canRead(it) }.orEmpty()
            val entity = BeatmapEntity(
                beatmapSetId = backup.beatmapSetId,
                title = backup.title,
                artist = backup.artist,
                creator = backup.creator,
                difficultyName = backup.difficultyName,
                audioPath = backup.audioUri,
                coverPath = coverUri,
                downloadedAt = backup.downloadedAt,
                genreId = backup.genreId,
                isAlbum = backup.isAlbum
            )
            db.beatmapDao().insertBeatmap(entity)
            db.preservedBeatmapSetIdDao().insertPreservedSetId(
                PreservedBeatmapSetIdEntity(beatmapSetId = backup.beatmapSetId)
            )
            restoredKeys += backup.trackKey()
        }

        manifest.playlists
            .sortedBy { it.createdAt }
            .forEach { playlist ->
                db.playlistDao().upsertPlaylist(
                    PlaylistEntity(
                        id = playlist.id,
                        name = playlist.name,
                        createdAt = playlist.createdAt
                    )
                )
            }

        manifest.playlistTracks.forEach { backup ->
            db.playlistDao().addTrack(
                PlaylistTrackEntity(
                    playlistId = backup.playlistId,
                    beatmapSetId = backup.beatmapSetId,
                    title = backup.title,
                    artist = backup.artist,
                    difficultyName = backup.difficultyName,
                    addedAt = backup.addedAt,
                    isDownloaded = backup.trackKey() in restoredKeys
                )
            )
        }

        Log.i(TAG, "Restored ${restoredKeys.size} tracks and ${manifest.playlists.size} playlists from MediaStore manifest")
        restoredKeys.size
    }

    private fun BeatmapEntity.toBackupTrack(): BackupTrack {
        return BackupTrack(
            beatmapSetId = beatmapSetId,
            title = title,
            artist = artist,
            creator = creator,
            difficultyName = difficultyName,
            audioUri = audioPath,
            coverUri = coverPath,
            downloadedAt = downloadedAt,
            genreId = genreId,
            isAlbum = isAlbum
        )
    }

    private fun PlaylistEntity.toBackupPlaylist(): BackupPlaylist {
        return BackupPlaylist(
            id = id,
            name = name,
            createdAt = createdAt
        )
    }

    private fun PlaylistTrackEntity.toBackupPlaylistTrack(): BackupPlaylistTrack {
        return BackupPlaylistTrack(
            playlistId = playlistId,
            beatmapSetId = beatmapSetId,
            title = title,
            artist = artist,
            difficultyName = difficultyName,
            addedAt = addedAt,
            isDownloaded = isDownloaded ?: false
        )
    }

    private fun BackupTrack.trackKey(): String = "$beatmapSetId|$difficultyName"

    private fun BackupPlaylistTrack.trackKey(): String = "$beatmapSetId|$difficultyName"
}

data class MosuManifest(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val tracks: List<BackupTrack> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList(),
    val playlistTracks: List<BackupPlaylistTrack> = emptyList()
)

data class BackupTrack(
    val beatmapSetId: Long,
    val title: String,
    val artist: String,
    val creator: String,
    val difficultyName: String,
    val audioUri: String,
    val coverUri: String,
    val downloadedAt: Long,
    val genreId: Int?,
    val isAlbum: Boolean
)

data class BackupPlaylist(
    val id: Long,
    val name: String,
    val createdAt: Long
)

data class BackupPlaylistTrack(
    val playlistId: Long,
    val beatmapSetId: Long,
    val title: String,
    val artist: String,
    val difficultyName: String,
    val addedAt: Long,
    val isDownloaded: Boolean
)
