package com.mosu.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissValue
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.SwipeToDismissDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.material3.IconButton
import kotlinx.coroutines.delay
import coil.compose.AsyncImage
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.data.db.PlaylistTrackEntity
import com.mosu.app.player.MusicController
import kotlinx.coroutines.launch
import java.io.File
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
    
    val genres = listOf(
        10 to stringResource(id = R.string.genre_electronic),
        3 to stringResource(id = R.string.genre_anime),
        4 to stringResource(id = R.string.genre_rock),
        5 to stringResource(id = R.string.genre_pop),
        2 to stringResource(id = R.string.genre_game),
        9 to stringResource(id = R.string.genre_hiphop),
        11 to stringResource(id = R.string.genre_metal),
        12 to stringResource(id = R.string.genre_classical),
        13 to stringResource(id = R.string.genre_folk),
        14 to stringResource(id = R.string.genre_jazz),
        7 to stringResource(id = R.string.genre_novelty),
        6 to stringResource(id = R.string.genre_other)
    )
    
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
        LazyRow(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
            items(genres) { (id, name) ->
                Button(
                    onClick = {
                        selectedGenreId = if (selectedGenreId == id) null else id
                    },
                    modifier = Modifier.padding(end = 8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedGenreId == id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (selectedGenreId == id) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(name, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

            LazyColumn(state = listState) {
            items(
                items = groupedMaps.keys.toList(),
                key = { setId -> setId } // Add unique key for proper state management
            ) { setId ->
                val tracks = groupedMaps[setId] ?: emptyList()
                if (tracks.isEmpty()) return@items

                if (tracks.size > 1) {
                            // Album Group
                    AlbumGroupItem(
                        tracks = tracks,
                        musicController = musicController,
                        db = db,
                        scope = scope,
                        highlight = highlightSetId == setId && nowPlaying != null,
                        onPlay = { selectedTrack ->
                            musicController.playSong(selectedTrack, filteredMaps)
                        },
                        onDelete = {
                            scope.launch {
                                tracks.forEach { track ->
                                    db.beatmapDao().deleteBeatmap(track)
                                    File(track.audioPath).delete()
                                    File(track.coverPath).delete()
                                }
                            }
                        },
                        onAddToPlaylist = { track ->
                            openPlaylistDialog(track)
                        }
                    )
                } else {
                    // Single Track
                    SingleTrackItem(
                        map = tracks[0],
                        musicController = musicController,
                        highlight = highlightSetId == setId && nowPlaying != null,
                        onPlay = {
                            musicController.playSong(tracks[0], filteredMaps)
                        },
                        onDelete = {
                            scope.launch {
                                val track = tracks[0]
                                // Delete from database
                                db.beatmapDao().deleteBeatmap(track)
                                // Delete audio and cover files
                                File(track.audioPath).delete()
                                File(track.coverPath).delete()
                            }
                        },
                        onAddToPlaylist = { track ->
                            openPlaylistDialog(track)
                        }
                    )
                }
                Divider(modifier = Modifier.padding(start = 64.dp)) // Apple style separator
            }
        }
        }

        if (showPlaylistDialog && dialogTrack != null) {
            val track = dialogTrack!!
            AlertDialog(
                onDismissRequest = { showPlaylistDialog = false },
                title = { Text(stringResource(id = R.string.playlist_dialog_title)) },
                text = {
                    Column {
                        playlists.forEach { playlist ->
                            val checked = dialogSelection.contains(playlist.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val newChecked = !checked
                                        dialogSelection = if (newChecked) dialogSelection + playlist.id else dialogSelection - playlist.id
                                        dialogSelectionCache[track.uid] = dialogSelection
                                        scope.launch {
                                            if (newChecked) {
                                                db.playlistDao().addTrack(
                                                    PlaylistTrackEntity(
                                                        playlistId = playlist.id,
                                                        beatmapUid = track.uid
                                                    )
                                                )
                                            } else {
                                                db.playlistDao().removeTrack(
                                                    playlistId = playlist.id,
                                                    beatmapUid = track.uid
                                                )
                                            }
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { newChecked ->
                                        dialogSelection = if (newChecked) dialogSelection + playlist.id else dialogSelection - playlist.id
                                        dialogSelectionCache[track.uid] = dialogSelection
                                        scope.launch {
                                            if (newChecked) {
                                                db.playlistDao().addTrack(
                                                    PlaylistTrackEntity(
                                                        playlistId = playlist.id,
                                                        beatmapUid = track.uid
                                                    )
                                                )
                                            } else {
                                                db.playlistDao().removeTrack(
                                                    playlistId = playlist.id,
                                                    beatmapUid = track.uid
                                                )
                                            }
                                        }
                                    }
                                )
                                Text(text = playlist.name, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        if (playlists.isEmpty()) {
                            Text(
                                text = stringResource(id = R.string.library_no_playlists),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {}
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumGroupItem(
    tracks: List<BeatmapEntity>,
    musicController: MusicController,
    db: AppDatabase,
    scope: CoroutineScope,
    onPlay: (BeatmapEntity) -> Unit,
    onDelete: () -> Unit,
    onAddToPlaylist: (BeatmapEntity) -> Unit,
    highlight: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val firstTrack = tracks[0]
    val dismissState = rememberDismissState(
        confirmValueChange = { value ->
            when (value) {
                DismissValue.DismissedToStart -> {
                    onDelete()
                    false
                }
                DismissValue.DismissedToEnd -> {
                    onAddToPlaylist(firstTrack)
                    false
                }
                else -> false
            }
        }
    )

    SwipeToDismiss(
        state = dismissState,
        directions = setOf(DismissDirection.EndToStart, DismissDirection.StartToEnd),
        background = {
            val flickerColor = if (isSystemInDarkTheme()) Color(0xFF300063) else Color.LightGray
            val bgColor = when (dismissState.dismissDirection) {
                DismissDirection.StartToEnd -> MaterialTheme.colorScheme.primary
                else -> if (highlight) flickerColor else MaterialTheme.colorScheme.error
            }
            val icon = when (dismissState.dismissDirection) {
                DismissDirection.StartToEnd -> Icons.Default.Add
                else -> Icons.Default.Delete
            }
            val iconTint = when (dismissState.dismissDirection) {
                DismissDirection.StartToEnd -> MaterialTheme.colorScheme.onPrimary
                else -> if (highlight) Color.Transparent else MaterialTheme.colorScheme.onError
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (dismissState.dismissDirection == DismissDirection.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (dismissState.dismissDirection == DismissDirection.StartToEnd)
                        stringResource(id = R.string.library_cd_add_playlist)
                    else
                        stringResource(id = R.string.library_cd_delete),
                    tint = iconTint,
                    modifier = Modifier.size(32.dp)
                )
            }

        },
        dismissContent = {
            val flickerColor = if (isSystemInDarkTheme()) Color(0xFF300063) else Color.LightGray
            val bg = if (highlight) flickerColor else MaterialTheme.colorScheme.surface
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg)
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
                        Text(text = stringResource(id = R.string.library_track_count, tracks.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, 
                        contentDescription = stringResource(id = R.string.library_cd_expand)
                    )
                }
                
                if (expanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        tracks.forEachIndexed { index, map ->
                            TrackRowWithSwipe(
                                map = map,
                                scope = scope,
                                onPlay = { onPlay(map) },
                                onAddToPlaylist = { onAddToPlaylist(map) },
                                onDelete = {
                                    db.beatmapDao().deleteBeatmap(map)
                                    File(map.audioPath).delete()
                                    // Don't delete cover photo for individual songs - only when entire beatmapset is deleted
                                },
                                modifier = Modifier,
                                backgroundColor = if (index % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleTrackItem(
    map: BeatmapEntity,
    musicController: MusicController,
    highlight: Boolean = false,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onAddToPlaylist: (BeatmapEntity) -> Unit
) {
    val dismissState = rememberDismissState(
        confirmValueChange = { value ->
            when (value) {
                DismissValue.DismissedToStart -> {
                    onDelete()
                    false
                }
                DismissValue.DismissedToEnd -> {
                    onAddToPlaylist(map)
                    false
                }
                else -> false
            }
        }
    )

    SwipeToDismiss(
        state = dismissState,
        directions = setOf(DismissDirection.EndToStart, DismissDirection.StartToEnd),
        background = {
            val flickerColor = if (isSystemInDarkTheme()) Color(0xFF300063) else Color.LightGray
            val bgColor = when (dismissState.dismissDirection) {
                DismissDirection.StartToEnd -> MaterialTheme.colorScheme.primary
                else -> if (highlight) flickerColor else MaterialTheme.colorScheme.error
            }
            val icon = when (dismissState.dismissDirection) {
                DismissDirection.StartToEnd -> Icons.Default.Add
                else -> Icons.Default.Delete
            }
            val iconTint = when (dismissState.dismissDirection) {
                DismissDirection.StartToEnd -> MaterialTheme.colorScheme.onPrimary
                else -> if (highlight) Color.Transparent else MaterialTheme.colorScheme.onError
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (dismissState.dismissDirection == DismissDirection.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (dismissState.dismissDirection == DismissDirection.StartToEnd)
                        stringResource(id = R.string.library_cd_add_playlist)
                    else
                        stringResource(id = R.string.library_cd_delete),
                    tint = iconTint,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        dismissContent = {
            val flickerColor = if (isSystemInDarkTheme()) Color(0xFF300063) else Color.LightGray
            val bg = if (highlight) flickerColor else MaterialTheme.colorScheme.surface            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .clickable { onPlay() }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackRowWithSwipe(
    map: BeatmapEntity,
    scope: CoroutineScope,
    onPlay: () -> Unit,
    onAddToPlaylist: (BeatmapEntity) -> Unit,
    onDelete: suspend () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface
) {
    val dismissState = rememberDismissState(
        confirmValueChange = { value ->
            when (value) {
                DismissValue.DismissedToEnd -> {
                    onAddToPlaylist(map)
                    false
                }
                DismissValue.DismissedToStart -> {
                    scope.launch {
                        onDelete()
                    }
                    false
                }
                else -> false
            }
        }
    )

    SwipeToDismiss(
        state = dismissState,
        directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart),
        background = {
            val bgColor = when (dismissState.dismissDirection) {
                DismissDirection.StartToEnd -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
            }
            val icon = when (dismissState.dismissDirection) {
                DismissDirection.StartToEnd -> Icons.Default.Add
                else -> Icons.Default.Delete
            }
            val iconTint = when (dismissState.dismissDirection) {
                DismissDirection.StartToEnd -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onError
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (dismissState.dismissDirection == DismissDirection.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (dismissState.dismissDirection == DismissDirection.StartToEnd)
                        stringResource(id = R.string.library_cd_add_playlist)
                    else
                        stringResource(id = R.string.library_cd_delete),
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        dismissContent = {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .clickable { onPlay() }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(text = map.difficultyName, style = MaterialTheme.typography.titleMedium)
                    Text(text = map.creator, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    )
}

