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

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND beatmapUid = :beatmapUid")
    suspend fun removeTrack(playlistId: Long, beatmapUid: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun removeAllTracksFromPlaylist(playlistId: Long)

    @Query(
        """
        SELECT beatmaps.* FROM beatmaps
        INNER JOIN playlist_tracks ON beatmaps.uid = playlist_tracks.beatmapUid
        WHERE playlist_tracks.playlistId = :playlistId
        ORDER BY playlist_tracks.addedAt ASC
    """
    )
    fun getTracksForPlaylist(playlistId: Long): Flow<List<BeatmapEntity>>

    @Query("SELECT * FROM playlist_tracks")
    fun getAllPlaylistTracks(): Flow<List<PlaylistTrackEntity>>
}

