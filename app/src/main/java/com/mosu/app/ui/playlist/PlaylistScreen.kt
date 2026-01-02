package com.mosu.app.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.data.db.PlaylistEntity
import com.mosu.app.data.services.TrackService
import com.mosu.app.data.db.PlaylistTrackWithBeatmap
import com.mosu.app.R
import com.mosu.app.player.MusicController
import com.mosu.app.ui.components.AlbumGroup
import com.mosu.app.ui.components.AlbumGroupActions
import com.mosu.app.ui.components.AlbumGroupData
import com.mosu.app.ui.components.PlaylistOption
import com.mosu.app.ui.components.PlaylistSelectorDialog
import com.mosu.app.ui.components.SongItemData
import com.mosu.app.ui.components.SwipeActions
import com.mosu.app.ui.components.SwipeToDismissSongItem
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    db: AppDatabase,
    musicController: MusicController
) {
    val playlists by db.playlistDao().getPlaylists().collectAsState(initial = emptyList())
    val playlistCounts by db.playlistDao().getPlaylistCounts().collectAsState(initial = emptyList())
    val downloadedTracks by db.beatmapDao().getAllBeatmaps().collectAsState(initial = emptyList())
    val playlistTrackRefs by db.playlistDao().getAllPlaylistTracks().collectAsState(initial = emptyList())
    val beatmapBySetId = remember(downloadedTracks) { downloadedTracks.associateBy { it.beatmapSetId } }
    val playlistMembership = playlistTrackRefs
        .groupBy { it.playlistId }
        .mapValues { entry -> entry.value.map { it.beatmapSetId }.toSet() }

    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
    val playlistTracksWithStatus: List<PlaylistTrackWithBeatmap> by if (selectedPlaylistId != null) {
        db.playlistDao().getTracksWithStatusForPlaylist(selectedPlaylistId!!).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }

    // Extract downloaded tracks for backward compatibility
    val playlistTracks: List<BeatmapEntity> = playlistTracksWithStatus.mapNotNull { it.toBeatmapEntity() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    var showAddDialog by remember { mutableStateOf(false) }
    var addSelection by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // Playlist dialog for adding tracks/playlists to other playlists
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var dialogTrack by remember { mutableStateOf<BeatmapEntity?>(null) }
    var dialogSelection by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val dialogSelectionCache = remember { mutableStateMapOf<Long, Set<Long>>() }

    val scope = rememberCoroutineScope()

    fun playAlbum(tracks: List<BeatmapEntity>) {
        if (tracks.isEmpty()) return
        musicController.playSong(tracks.first(), tracks)
    }

    fun openPlaylistDialog(track: BeatmapEntity) {
        dialogTrack = track
        dialogSelection = dialogSelectionCache[track.uid]
            ?: playlists
                .filter { playlistMembership[it.id]?.contains(track.beatmapSetId) == true }
                .map { it.id }
                .toSet()
        showPlaylistDialog = true
    }

    // Keep dialog checkboxes in sync with latest membership while dialog is open
    LaunchedEffect(playlistTrackRefs, dialogTrack, playlists) {
        val track = dialogTrack ?: return@LaunchedEffect
        val latest = playlists
            .filter { playlistMembership[it.id]?.contains(track.beatmapSetId) == true }
            .map { it.id }
            .toSet()
        dialogSelection = latest
        dialogSelectionCache[track.uid] = latest
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (selectedPlaylistId == null) {
            // Grid view of albums
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.playlist_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(id = R.string.playlist_cd_create_playlist))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = stringResource(id = R.string.playlist_empty_state))
                }
            } else {
                val countsMap = playlistCounts.associate { it.playlistId to it.count }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        val coverPaths = playlistTrackRefs
                            .asSequence()
                            .filter { it.playlistId == playlist.id }
                            .sortedBy { it.addedAt }
                            .mapNotNull { ref -> beatmapBySetId[ref.beatmapSetId]?.coverPath }
                            .filter { it.isNotBlank() }
                            .take(4)
                            .toList()
                        PlaylistCard(
                            playlist = playlist,
                            trackCount = countsMap[playlist.id] ?: 0,
                            coverPaths = coverPaths,
                            onClick = { selectedPlaylistId = playlist.id },
                            onDelete = {
                                scope.launch {
                                    // Delete all tracks from this playlist first
                                    db.playlistDao().removeAllTracksFromPlaylist(playlist.id)
                                    // Then delete the playlist itself
                                    db.playlistDao().deletePlaylist(playlist.id)
                                }
                            }
                        )
                    }
                }
            }
        } else {
            val selectedPlaylist = playlists.find { it.id == selectedPlaylistId }
            val existingIds = playlistTracks.map { it.uid }.toSet()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedPlaylistId = null }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(id = R.string.playlist_cd_back))
                }
                Column {
                    Text(
                        text = selectedPlaylist?.name ?: stringResource(id = R.string.playlist_default_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val downloadedCount = playlistTracksWithStatus.count { it.toBeatmapEntity() != null }
                    val totalCount = playlistTracksWithStatus.size
                    Text(
                        text = if (downloadedCount < totalCount) {
                            "$downloadedCount/${totalCount} ${stringResource(id = R.string.playlist_song_count_suffix)}"
                        } else {
                            "$totalCount ${stringResource(id = R.string.playlist_song_count_suffix)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    addSelection = emptySet()
                    showAddDialog = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(id = R.string.playlist_cd_add_songs))
                }
                IconButton(onClick = { playAlbum(playlistTracks) }, enabled = playlistTracks.isNotEmpty()) {
                    Icon(Icons.Default.PlayArrow, contentDescription = stringResource(id = R.string.playlist_cd_play_playlist))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (playlistTracks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = stringResource(id = R.string.playlist_no_songs))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        count = playlistTracksWithStatus.size,
                        key = { index -> playlistTracksWithStatus[index].beatmapSetId }
                    ) { index ->
                        val trackWithStatus = playlistTracksWithStatus[index]
                        val beatmapEntity = trackWithStatus.toBeatmapEntity()

                        if (beatmapEntity != null) {
                            // Downloaded track - show normally
                            val songData = SongItemData(
                                title = beatmapEntity.title,
                                artist = beatmapEntity.artist,
                                coverPath = beatmapEntity.coverPath,
                                difficultyName = beatmapEntity.difficultyName,
                                id = beatmapEntity.uid
                            )

                            val swipeActions = SwipeActions(
                                onDelete = {
                                    scope.launch {
                                        TrackService.removeTrackFromPlaylist(selectedPlaylistId!!, beatmapEntity.beatmapSetId, db)
                                    }
                                },
                                onAddToPlaylist = { openPlaylistDialog(beatmapEntity) }
                            )

                            SwipeToDismissSongItem(
                                song = songData,
                                onClick = { musicController.playSong(beatmapEntity, playlistTracks) },
                                swipeActions = swipeActions,
                                highlight = false
                            )
                        } else {
                            // Undownloaded track - show placeholder with different styling
                            val songData = SongItemData(
                                title = "Undownloaded Track",
                                artist = "ID: ${trackWithStatus.beatmapSetId}",
                                coverPath = "",
                                difficultyName = "",
                                id = trackWithStatus.beatmapSetId
                            )

                            SwipeToDismissSongItem(
                                song = songData,
                                onClick = {
                                    // TODO: Implement restore for individual playlist tracks
                                    // This should trigger download of the specific beatmap set
                                },
                                swipeActions = SwipeActions(
                                    onDelete = {
                                        scope.launch {
                                            TrackService.removeTrackFromPlaylist(selectedPlaylistId!!, trackWithStatus.beatmapSetId, db)
                                        }
                                    },
                                    onAddToPlaylist = null // Can't add undownloaded tracks to other playlists
                                ),
                                highlight = false,
                                backgroundColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }

            if (showAddDialog) {
                AlertDialog(
                    onDismissRequest = { showAddDialog = false },
                    title = { Text(stringResource(id = R.string.playlist_add_songs_dialog_title)) },
                    text = {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(downloadedTracks.size) { idx ->
                                val track = downloadedTracks[idx]
                                val alreadyIn = existingIds.contains(track.uid)
                                val checked = addSelection.contains(track.uid)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !alreadyIn) {
                                            if (checked) {
                                                addSelection = addSelection - track.uid
                                            } else {
                                                addSelection = addSelection + track.uid
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked || alreadyIn,
                                        onCheckedChange = {
                                            if (!alreadyIn) {
                                                addSelection = if (checked) addSelection - track.uid else addSelection + track.uid
                                            }
                                        },
                                        enabled = !alreadyIn
                                    )
                                    Column(modifier = Modifier.padding(start = 8.dp)) {
                                        Text(text = track.title, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            text = track.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    if (alreadyIn) {
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text(
                                            text = stringResource(id = R.string.playlist_added_status),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val playlistId = selectedPlaylistId ?: return@TextButton
                                scope.launch {
                                    addSelection.forEach { uid ->
                                        val track = downloadedTracks.find { it.uid == uid }
                                        if (track != null) {
                                            TrackService.addTrackToPlaylist(playlistId, track.beatmapSetId, db)
                                        }
                                    }
                                }
                                showAddDialog = false
                            }
                        ) {
                            Text(stringResource(id = R.string.playlist_add_button))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddDialog = false }) { Text(stringResource(id = R.string.playlist_cancel_button)) }
                    }
                )
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(id = R.string.playlist_create_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    singleLine = true,
                    label = { Text(stringResource(id = R.string.playlist_name_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newPlaylistName.trim()
                        if (name.isNotEmpty()) {
                            scope.launch {
                                db.playlistDao().insertPlaylist(PlaylistEntity(name = name))
                            }
                            newPlaylistName = ""
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text(stringResource(id = R.string.playlist_create_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(id = R.string.playlist_cancel_button))
                }
            }
        )
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
            onAddToPlaylist = { playlistId, beatmapSetId ->
                scope.launch {
                    TrackService.addTrackToPlaylist(playlistId, beatmapSetId, db)
                }
            },
            onRemoveFromPlaylist = { playlistId, beatmapSetId ->
                scope.launch {
                    TrackService.removeTrackFromPlaylist(playlistId, beatmapSetId, db)
                }
            },
            beatmapSetId = track.beatmapSetId,
            onDismiss = { showPlaylistDialog = false }
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: PlaylistEntity,
    trackCount: Int,
    coverPaths: List<String>,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteIcon by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (showDeleteIcon) {
                            // If delete icon is showing, hide it instead of opening playlist
                            showDeleteIcon = false
                        } else {
                            onClick()
                        }
                    },
                    onLongPress = {
                        showDeleteIcon = true
                    }
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            CollageBackground(
                coverPaths = coverPaths,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .padding(12.dp)
            ) {
                // Delete icon overlay (top-right corner)
                if (showDeleteIcon) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(id = R.string.playlist_cd_delete_playlist),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(24.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                            .padding(4.dp)
                            .clickable(
                                onClick = {
                                    onDelete()
                                    showDeleteIcon = false  // Hide icon after deletion
                                }
                            )
                    )
                }
            Column(
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Icon(
                    Icons.Default.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.65f),
                            offset = Offset(1f, 1f),
                            blurRadius = 4f
                        )
                    ),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$trackCount songs",
                    style = MaterialTheme.typography.bodySmall.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.6f),
                            offset = Offset(0.8f, 0.8f),
                            blurRadius = 3f
                        )
                    ),
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        }
    }
}


@Composable
private fun CollageBackground(
    coverPaths: List<String>,
    modifier: Modifier = Modifier
) {
    val paths = coverPaths.take(4)
    if (paths.isEmpty()) {
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        return
    }

    val chunks = listOf(
        paths.take(2),
        paths.drop(2)
    )

    Column(modifier = modifier) {
        chunks.forEach { row ->
            Row(modifier = Modifier.weight(1f)) {
                row.forEach { path ->
                    AsyncImage(
                        model = File(path),
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .aspectRatio(1f),
                        contentScale = ContentScale.Crop
                    )
                }
                repeat(2 - row.size) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        }
    }
}

