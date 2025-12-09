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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissValue
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDismissState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.player.MusicController
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun LibraryScreen(
    db: AppDatabase,
    musicController: MusicController
) {
    val downloadedMaps by db.beatmapDao().getAllBeatmaps().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    
    // Group maps by Set ID
    val groupedMaps = downloadedMaps.groupBy { it.beatmapSetId }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.displayMedium, // Apple Music style large title
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn {
            items(groupedMaps.keys.toList()) { setId ->
                val tracks = groupedMaps[setId] ?: emptyList()
                if (tracks.isEmpty()) return@items

                if (tracks.size > 1) {
                    // Album Group
                    AlbumGroupItem(
                        tracks = tracks,
                        musicController = musicController,
                        onDelete = {
                            scope.launch {
                                tracks.forEach { track ->
                                    // Delete from database
                                    db.beatmapDao().deleteBeatmap(track)
                                    // Delete audio and cover files
                                    File(track.audioPath).delete()
                                    File(track.coverPath).delete()
                                }
                            }
                        }
                    )
                } else {
                    // Single Track
                    SingleTrackItem(
                        map = tracks[0],
                        musicController = musicController,
                        onDelete = {
                            scope.launch {
                                val track = tracks[0]
                                // Delete from database
                                db.beatmapDao().deleteBeatmap(track)
                                // Delete audio and cover files
                                File(track.audioPath).delete()
                                File(track.coverPath).delete()
                            }
                        }
                    )
                }
                Divider(modifier = Modifier.padding(start = 64.dp)) // Apple style separator
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumGroupItem(tracks: List<BeatmapEntity>, musicController: MusicController, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val firstTrack = tracks[0]
    val dismissState = rememberDismissState(
        confirmValueChange = {
            if (it == DismissValue.DismissedToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismiss(
        state = dismissState,
        directions = setOf(DismissDirection.EndToStart),
        background = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Red delete area on the right side only
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.error)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        },
        dismissContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cover Art
                    AsyncImage(
                        model = File(firstTrack.coverPath),
                        contentDescription = null,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                    
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp).weight(1f)) {
                        Text(text = firstTrack.title, style = MaterialTheme.typography.titleMedium)
                        Text(text = firstTrack.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                        Text(text = "${tracks.size} tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, 
                        contentDescription = "Expand"
                    )
                }
                
                if (expanded) {
                    tracks.forEach { map ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { musicController.playSong(map) }
                                .padding(start = 66.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), // Indented
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = map.difficultyName, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleTrackItem(map: BeatmapEntity, musicController: MusicController, onDelete: () -> Unit) {
    val dismissState = rememberDismissState(
        confirmValueChange = {
            if (it == DismissValue.DismissedToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismiss(
        state = dismissState,
        directions = setOf(DismissDirection.EndToStart),
        background = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Red delete area on the right side only
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.error)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        },
        dismissContent = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { musicController.playSong(map) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = File(map.coverPath),
                    contentDescription = null,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
                
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(text = map.title, style = MaterialTheme.typography.titleMedium)
                    Text(text = map.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    )
}

