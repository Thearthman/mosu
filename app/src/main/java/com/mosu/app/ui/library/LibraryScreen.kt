package com.mosu.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.data.db.PlaylistTrackEntity
import com.mosu.app.player.MusicController
import com.mosu.app.ui.components.AlbumGroup
import com.mosu.app.ui.components.AlbumGroupActions
import com.mosu.app.ui.components.AlbumGroupData
import com.mosu.app.ui.components.GenreFilter
import com.mosu.app.ui.components.PlaylistOption
import com.mosu.app.ui.components.PlaylistSelectorDialog
import com.mosu.app.ui.components.SongItemData
import com.mosu.app.ui.components.SwipeActions
import com.mosu.app.ui.components.SwipeToDismissSongItem
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.mosu.app.R

@Composable
fun LibraryScreen(
    db: AppDatabase,
    musicController: MusicController
) {
    val downloadedMaps by db.beatmapDao().getAllBeatmaps().collectAsState(initial = emptyList())
    val playlists by db.playlistDao().getPlaylists().collectAsState(initial = emptyList())
    val playlistTracks by db.playlistDao().getAllPlaylistTracks().collectAsState(initial = emptyList())
    val playlistMembership = playlistTracks
        .groupBy { it.playlistId }
        .mapValues { entry -> entry.value.map { it.beatmapUid }.toSet() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var highlightSetId by remember { mutableStateOf<Long?>(null) }
    var buttonBlink by remember { mutableStateOf(false) }
    val nowPlaying by musicController.nowPlaying.collectAsState()
    val playingTitle = nowPlaying?.title?.toString()?.trim()?.lowercase()

    // Genre Filter State
    var selectedGenreId by remember { mutableStateOf<Int?>(null) }
    
    // Filter maps by selected genre
    val filteredMaps = if (selectedGenreId != null) {
        downloadedMaps.filter { it.genreId == selectedGenreId }
    } else {
        downloadedMaps
    }
    
    // Group maps by Set ID
    val groupedMaps = filteredMaps.groupBy { it.beatmapSetId }

    var showPlaylistDialog by remember { mutableStateOf(false) }
    var dialogTrack by remember { mutableStateOf<BeatmapEntity?>(null) }
    var dialogSelection by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val dialogSelectionCache = remember { mutableStateMapOf<Long, Set<Long>>() }

    fun openPlaylistDialog(track: BeatmapEntity) {
        dialogTrack = track
        dialogSelection = dialogSelectionCache[track.uid]
            ?: playlists
                .filter { playlistMembership[it.id]?.contains(track.uid) == true }
                .map { it.id }
                .toSet()
        showPlaylistDialog = true
    }

    // Keep dialog checkboxes in sync with latest membership while dialog is open
    LaunchedEffect(playlistTracks, dialogTrack, playlists) {
        val track = dialogTrack ?: return@LaunchedEffect
        val latest = playlists
            .filter { playlistMembership[it.id]?.contains(track.uid) == true }
            .map { it.id }
            .toSet()
        dialogSelection = latest
        dialogSelectionCache[track.uid] = latest
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(id = R.string.library_title),
            style = MaterialTheme.typography.displayMedium, // Apple Music style large title
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
        )
        
        // Genre Filter
        Text(text = stringResource(id = R.string.library_filter_genre), style = MaterialTheme.typography.labelMedium)
        GenreFilter(
            selectedGenreId = selectedGenreId,
            onGenreSelected = { selectedGenreId = it },
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )

            LazyColumn(state = listState) {
            items(
                items = groupedMaps.keys.toList(),
                key = { setId -> setId } // Add unique key for proper state management
            ) { setId ->
                val tracks = groupedMaps[setId] ?: emptyList()
                if (tracks.isEmpty()) return@items

                if (tracks.size > 1) {
                    // Album Group
                    val albumData = AlbumGroupData(
                        title = tracks[0].title,
                        artist = tracks[0].artist,
                        coverPath = tracks[0].coverPath,
                        trackCount = tracks.size,
                        songs = tracks.map { track ->
                            SongItemData(
                                title = track.difficultyName,
                                artist = track.creator,
                                coverPath = track.coverPath,
                                difficultyName = track.difficultyName,
                                id = track.uid
                            )
                        },
                        id = setId
                    )

                    val albumActions = AlbumGroupActions(
                        onAlbumPlay = {
                            // Play the first track of the album
                            musicController.playSong(tracks.first(), filteredMaps)
                        },
                        onTrackPlay = { songData ->
                            // Find the actual BeatmapEntity and play it
                            val track = tracks.find { it.uid == songData.id }
                            if (track != null) {
                                musicController.playSong(track, filteredMaps)
                            }
                        },
                        onDelete = {
                            scope.launch {
                                tracks.forEach { track ->
                                    db.beatmapDao().deleteBeatmap(track)
                                    java.io.File(track.audioPath).delete()
                                    java.io.File(track.coverPath).delete()
                                }
                            }
                        },
                        onAddToPlaylist = {
                            openPlaylistDialog(tracks.first())
                        },
                        onTrackDelete = { songData ->
                            scope.launch {
                                val track = tracks.find { it.uid == songData.id }
                                if (track != null) {
                                    db.beatmapDao().deleteBeatmap(track)
                                    java.io.File(track.audioPath).delete()
                                    // Don't delete cover photo for individual songs - only when entire beatmapset is deleted
                                }
                            }
                        },
                        onTrackAddToPlaylist = { songData ->
                            val track = tracks.find { it.uid == songData.id }
                            if (track != null) {
                                openPlaylistDialog(track)
                            }
                        }
                    )

                    AlbumGroup(
                        album = albumData,
                        actions = albumActions,
                        highlight = highlightSetId == setId && nowPlaying != null
                    )
                } else {
                    // Single Track
                    val track = tracks[0]
                    val songData = SongItemData(
                        title = track.title,
                        artist = track.artist,
                        coverPath = track.coverPath,
                        difficultyName = track.difficultyName,
                        id = track.uid
                    )

                    val swipeActions = SwipeActions(
                        onDelete = {
                            scope.launch {
                                // Delete from database
                                db.beatmapDao().deleteBeatmap(track)
                                // Delete audio and cover files
                                java.io.File(track.audioPath).delete()
                                java.io.File(track.coverPath).delete()
                            }
                        },
                        onAddToPlaylist = { openPlaylistDialog(track) }
                    )

                    SwipeToDismissSongItem(
                        song = songData,
                        onClick = { musicController.playSong(track, filteredMaps) },
                        swipeActions = swipeActions,
                        highlight = highlightSetId == setId && nowPlaying != null
                    )
                }
                Divider(modifier = Modifier.padding(start = 64.dp)) // Apple style separator
            }
        }
        }

        if (showPlaylistDialog && dialogTrack != null) {
            val track = dialogTrack!!
            val playlistOptions = playlists.map { PlaylistOption(it.id, it.name) }

            PlaylistSelectorDialog(
                playlists = playlistOptions,
                selectedPlaylistIds = dialogSelection,
                onSelectionChanged = { newSelection ->
                    dialogSelection = newSelection
                    dialogSelectionCache[track.uid] = newSelection
                },
                onAddToPlaylist = { playlistId, beatmapUid ->
                    scope.launch {
                        db.playlistDao().addTrack(
                            PlaylistTrackEntity(
                                playlistId = playlistId,
                                beatmapUid = beatmapUid
                            )
                        )
                    }
                },
                onRemoveFromPlaylist = { playlistId, beatmapUid ->
                    scope.launch {
                        db.playlistDao().removeTrack(
                            playlistId = playlistId,
                            beatmapUid = beatmapUid
                        )
                    }
                },
                beatmapUid = track.uid,
                onDismiss = { showPlaylistDialog = false }
            )
        }

        if (nowPlaying != null) {
            val isLight = androidx.compose.foundation.isSystemInDarkTheme().not()
            val flickerColor = if (isLight) MaterialTheme.colorScheme.onPrimary else Color(0xff300063)
            val buttonBg = if (buttonBlink) flickerColor.copy(alpha = 0.35f) else Color.Transparent
            IconButton(
                onClick = {
                    val keyList = groupedMaps.keys.toList()
                    val match = filteredMaps.firstOrNull { it.title.trim().lowercase() == playingTitle }
                    if (match != null) {
                        val targetIndex = keyList.indexOf(match.beatmapSetId)
                        if (targetIndex >= 0) {
                            scope.launch {
                                listState.animateScrollToItem(targetIndex)
                                repeat(3) {
                                    highlightSetId = match.beatmapSetId
                                    delay(150)
                                    highlightSetId = null
                                    delay(150)
                                }
                                highlightSetId = match.beatmapSetId
                                delay(700)
                                highlightSetId = null
                            }
                        }
                    } else {
                        scope.launch {
                            repeat(3) {
                                buttonBlink = true
                                delay(150)
                                buttonBlink = false
                                delay(150)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 14.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(buttonBg)
//                    .zIndex(2f)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = stringResource(id = R.string.library_find_current_cd),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}


