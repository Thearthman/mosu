package com.mosu.app.data.media

import android.content.Context
import android.util.Log
import com.mosu.app.data.AccountCredentialBackup
import com.mosu.app.data.AccountManager
import com.mosu.app.data.TokenManager
import com.google.gson.Gson
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.data.db.PlaylistEntity
import com.mosu.app.data.db.PlaylistTrackEntity
import com.mosu.app.data.db.PreservedBeatmapSetIdEntity
import com.mosu.app.data.db.RecentPlayEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object MosuBackupService {
    private const val TAG = "MosuBackupService"
    private const val MAX_RECENT_PLAYS = 500
    private val gson = Gson()

    suspend fun exportState(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        runCatching {
            MediaStoreFileService(context).writeManifest(
                createManifestJson(
                    db = db,
                    accountManager = AccountManager(context, TokenManager(context))
                )
            )
        }.onFailure { error ->
            Log.w(TAG, "Unable to export MediaStore manifest", error)
        }
    }

    suspend fun createManifestJson(
        db: AppDatabase,
        accountManager: AccountManager? = null
    ): String = withContext(Dispatchers.IO) {
        val beatmaps = db.beatmapDao().getAllBeatmaps().first()
        val playlists = db.playlistDao().getPlaylists().first()
        val playlistTracks = db.playlistDao().getAllPlaylistTracks().first()
        val recentPlays = db.recentPlayDao().getRecentPlays()

        val manifest = MosuManifest(
            exportedAt = System.currentTimeMillis(),
            accountCredentials = accountManager?.getCredentialBackups().orEmpty(),
            tracks = beatmaps.map { it.toBackupTrack() },
            playlists = playlists.map { it.toBackupPlaylist() },
            playlistTracks = playlistTracks.map { it.toBackupPlaylistTrack() },
            recentPlays = recentPlays.map { it.toBackupRecentPlay() }
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
        requireEmptyLibrary: Boolean,
        accountManager: AccountManager? = null
    ): Int = withContext(Dispatchers.IO) {
        if (requireEmptyLibrary && db.beatmapDao().getAllBeatmaps().first().isNotEmpty()) {
            return@withContext 0
        }

        val manifest = runCatching { gson.fromJson(json, MosuManifest::class.java) }
            .onFailure { Log.w(TAG, "Unable to parse MediaStore manifest", it) }
            .getOrNull()
            ?: return@withContext 0

        val tracks = manifest.tracks.orEmpty()
        val playlists = manifest.playlists.orEmpty()
        val playlistTracks = manifest.playlistTracks.orEmpty()

        val effectiveAccountManager = accountManager ?: AccountManager(context, TokenManager(context))
        effectiveAccountManager.restoreCredentialBackups(manifest.accountCredentials.orEmpty())

        val mediaStore = MediaStoreFileService(context)
        val restoredKeys = mutableSetOf<String>()
        tracks.forEach { backup ->
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

        playlists
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

        playlistTracks.forEach { backup ->
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

        restoreRecentPlays(db, manifest.recentPlays.orEmpty())

        Log.i(TAG, "Restored ${restoredKeys.size} tracks and ${playlists.size} playlists from MediaStore manifest")
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

    private fun RecentPlayEntity.toBackupRecentPlay(): BackupRecentPlay {
        return BackupRecentPlay(
            scoreId = scoreId,
            beatmapSetId = beatmapSetId,
            title = title,
            artist = artist,
            creator = creator,
            coverUrl = coverUrl,
            genreId = genreId,
            playedAt = playedAt
        )
    }

    private fun BackupRecentPlay.toRecentPlayEntity(): RecentPlayEntity {
        return RecentPlayEntity(
            scoreId = scoreId,
            beatmapSetId = beatmapSetId,
            title = title,
            artist = artist,
            creator = creator,
            coverUrl = coverUrl,
            genreId = genreId,
            playedAt = playedAt
        )
    }

    private suspend fun restoreRecentPlays(db: AppDatabase, imported: List<BackupRecentPlay>) {
        if (imported.isEmpty()) return

        val existing = db.recentPlayDao().getRecentPlays().map { it.toBackupRecentPlay() }
        val merged = (imported + existing)
            .sortedByDescending { it.playedAt }
            .distinctBy { it.dedupKey() }
            .take(MAX_RECENT_PLAYS)
            .map { it.toRecentPlayEntity() }

        db.recentPlayDao().replaceAll(merged)
    }

    private fun BackupRecentPlay.dedupKey(): String {
        return scoreId?.let { "score:$it" } ?: "beatmap-set:$beatmapSetId:$playedAt"
    }

    private fun BackupTrack.trackKey(): String = "$beatmapSetId|$difficultyName"

    private fun BackupPlaylistTrack.trackKey(): String = "$beatmapSetId|$difficultyName"
}

data class MosuManifest(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val accountCredentials: List<AccountCredentialBackup>? = emptyList(),
    val tracks: List<BackupTrack>? = emptyList(),
    val playlists: List<BackupPlaylist>? = emptyList(),
    val playlistTracks: List<BackupPlaylistTrack>? = emptyList(),
    val recentPlays: List<BackupRecentPlay>? = emptyList()
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

data class BackupRecentPlay(
    val scoreId: Long? = null,
    val beatmapSetId: Long,
    val title: String,
    val artist: String,
    val creator: String,
    val coverUrl: String? = null,
    val genreId: Int? = null,
    val playedAt: Long
)
