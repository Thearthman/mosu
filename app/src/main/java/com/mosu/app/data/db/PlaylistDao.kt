package com.mosu.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY sortOrder ASC, createdAt DESC")
    fun getPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    @Query("SELECT playlistId, COUNT(*) as count FROM playlist_tracks GROUP BY playlistId")
    fun getPlaylistCounts(): Flow<List<PlaylistTrackCount>>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    suspend fun updatePlaylistName(playlistId: Long, newName: String)

    @Query("UPDATE playlists SET sortOrder = :sortOrder WHERE id = :playlistId")
    suspend fun updatePlaylistSortOrder(playlistId: Long, sortOrder: Int)

    @Transaction
    suspend fun updatePlaylistSortOrders(orderedPlaylistIds: List<Long>) {
        orderedPlaylistIds.forEachIndexed { index, playlistId ->
            updatePlaylistSortOrder(playlistId, index)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrack(crossRef: PlaylistTrackEntity)

    @Query("UPDATE playlist_tracks SET isDownloaded = :downloaded WHERE beatmapSetId = :beatmapSetId")
    suspend fun updateTrackDownloadStatus(beatmapSetId: Long, downloaded: Boolean)

    @Query("UPDATE playlist_tracks SET isDownloaded = 0 WHERE beatmapSetId IN (SELECT beatmapSetId FROM beatmaps)")
    suspend fun markAllTracksAsUndownloaded()

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND beatmapSetId = :beatmapSetId AND difficultyName = :difficultyName")
    suspend fun removeTrack(playlistId: Long, beatmapSetId: Long, difficultyName: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun removeAllTracksFromPlaylist(playlistId: Long)

    @Query(
        """
        SELECT beatmaps.* FROM beatmaps
        INNER JOIN playlist_tracks ON beatmaps.beatmapSetId = playlist_tracks.beatmapSetId AND beatmaps.difficultyName = playlist_tracks.difficultyName
        WHERE playlist_tracks.playlistId = :playlistId
        ORDER BY playlist_tracks.addedAt ASC
    """
    )
    fun getTracksForPlaylist(playlistId: Long): Flow<List<BeatmapEntity>>

    @Query(
        """
        SELECT
            pt.playlistId,
            pt.beatmapSetId,
            pt.title AS storedTitle,
            pt.artist AS storedArtist,
            pt.difficultyName AS storedDifficultyName,
            pt.addedAt,
            pt.isDownloaded,
            b.uid,
            b.title AS beatmapTitle,
            b.artist AS beatmapArtist,
            b.creator,
            b.difficultyName,
            b.audioPath,
            b.coverPath,
            b.downloadedAt,
            b.genreId,
            b.isAlbum
        FROM playlist_tracks pt
        LEFT JOIN beatmaps b ON b.uid = COALESCE(
            (
                SELECT x.uid FROM beatmaps x
                WHERE x.beatmapSetId = pt.beatmapSetId AND x.difficultyName = pt.difficultyName
                LIMIT 1
            ),
            (
                SELECT y.uid FROM beatmaps y
                WHERE y.beatmapSetId = pt.beatmapSetId
                ORDER BY y.uid ASC
                LIMIT 1
            )
        )
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.addedAt ASC
    """
    )
    fun getTracksWithStatusForPlaylist(playlistId: Long): Flow<List<PlaylistTrackWithBeatmap>>

    @Query("SELECT * FROM playlist_tracks")
    fun getAllPlaylistTracks(): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    suspend fun getPlaylistTrackRefs(playlistId: Long): List<PlaylistTrackEntity>
}
