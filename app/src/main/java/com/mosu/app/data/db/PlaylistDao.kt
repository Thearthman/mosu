package com.mosu.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT playlistId, COUNT(*) as count FROM playlist_tracks GROUP BY playlistId")
    fun getPlaylistCounts(): Flow<List<PlaylistTrackCount>>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrack(crossRef: PlaylistTrackEntity)

    @Query("UPDATE playlist_tracks SET isDownloaded = :downloaded WHERE beatmapSetId = :beatmapSetId")
    suspend fun updateTrackDownloadStatus(beatmapSetId: Long, downloaded: Boolean)

    @Query("UPDATE playlist_tracks SET isDownloaded = 0 WHERE beatmapSetId IN (SELECT beatmapSetId FROM beatmaps)")
    suspend fun markAllTracksAsUndownloaded()

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND beatmapSetId = :beatmapSetId")
    suspend fun removeTrack(playlistId: Long, beatmapSetId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun removeAllTracksFromPlaylist(playlistId: Long)

    @Query(
        """
        SELECT beatmaps.* FROM beatmaps
        INNER JOIN playlist_tracks ON beatmaps.beatmapSetId = playlist_tracks.beatmapSetId
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
            b.genreId
        FROM playlist_tracks pt
        LEFT JOIN beatmaps b ON pt.beatmapSetId = b.beatmapSetId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.addedAt ASC
    """
    )
    fun getTracksWithStatusForPlaylist(playlistId: Long): Flow<List<PlaylistTrackWithBeatmap>>

    @Query("SELECT * FROM playlist_tracks")
    fun getAllPlaylistTracks(): Flow<List<PlaylistTrackEntity>>
}

