package com.mosu.app.ui.playlist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.data.db.PlaylistEntity
import com.mosu.app.data.db.PlaylistTrackEntity
import com.mosu.app.player.MusicController
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PlaylistScreen(
    db: AppDatabase,
    musicController: MusicController
) {
    val playlists by db.playlistDao().getPlaylists().collectAsState(initial = emptyList())
    val playlistCounts by db.playlistDao().getPlaylistCounts().collectAsState(initial = emptyList())
    val downloadedTracks by db.beatmapDao().getAllBeatmaps().collectAsState(initial = emptyList())
    val playlistTrackRefs by db.playlistDao().getAllPlaylistTracks().collectAsState(initial = emptyList())
    val beatmapByUid = remember(downloadedTracks) { downloadedTracks.associateBy { it.uid } }

    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
    val playlistTracks: List<BeatmapEntity> by if (selectedPlaylistId != null) {
        db.playlistDao().getTracksForPlaylist(selectedPlaylistId!!).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    var showAddDialog by remember { mutableStateOf(false) }
    var addSelection by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val scope = rememberCoroutineScope()

    fun playAlbum(tracks: List<BeatmapEntity>) {
        if (tracks.isEmpty()) return
        musicController.playSong(tracks.first(), tracks)
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
                    text = "Playlists",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Create playlist")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "No playlists yet. Tap + to create one.")
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
                            .mapNotNull { ref -> beatmapByUid[ref.beatmapUid]?.coverPath }
                            .filter { it.isNotBlank() }
                            .take(4)
                            .toList()
                        PlaylistCard(
                            playlist = playlist,
                            trackCount = countsMap[playlist.id] ?: 0,
                            coverPaths = coverPaths,
                            onClick = { selectedPlaylistId = playlist.id }
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
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = selectedPlaylist?.name ?: "Playlist",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    addSelection = emptySet()
                    showAddDialog = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add songs")
                }
                IconButton(onClick = { playAlbum(playlistTracks) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play playlist")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (playlistTracks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "No songs yet. Tap + to add.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(playlistTracks.size) { index ->
                        val track = playlistTracks[index]
                        PlaylistTrackRow(
                            track = track,
                            onPlay = { playAlbum(playlistTracks) }
                        )
                    }
                }
            }

            if (showAddDialog) {
                AlertDialog(
                    onDismissRequest = { showAddDialog = false },
                    title = { Text("Add songs") },
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
                                            text = "Added",
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
                                        db.playlistDao().addTrack(
                                            PlaylistTrackEntity(
                                                playlistId = playlistId,
                                                beatmapUid = uid
                                            )
                                        )
                                    }
                                }
                                showAddDialog = false
                            }
                        ) {
                            Text("Add")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    singleLine = true,
                    label = { Text("Name") },
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
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: PlaylistEntity,
    trackCount: Int,
    coverPaths: List<String>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clickable { onClick() },
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
private fun PlaylistTrackRow(
    track: BeatmapEntity,
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = File(track.coverPath),
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        ) {
            Text(
                text = track.title, 
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1
            )
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

