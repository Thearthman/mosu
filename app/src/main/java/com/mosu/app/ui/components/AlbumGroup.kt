package com.mosu.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mosu.app.R
import java.io.File

/**
 * Data class representing an album group
 */
data class AlbumGroupData(
    val title: String,
    val artist: String,
    val coverPath: String,
    val trackCount: Int,
    val songs: List<SongItemData>,
    val id: Long
)

/**
 * Configuration for album group actions
 */
data class AlbumGroupActions(
    val onAlbumPlay: (() -> Unit)? = null, // Play the album (first track)
    val onTrackPlay: ((SongItemData) -> Unit)? = null, // Play a specific track
    val onDelete: (() -> Unit)? = null,
    val onAddToPlaylist: (() -> Unit)? = null, // Add album to playlist
    val onTrackDelete: ((SongItemData) -> Unit)? = null,
    val onTrackAddToPlaylist: ((SongItemData) -> Unit)? = null
)

/**
 * An expandable album group component with swipe-to-dismiss functionality
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumGroup(
    album: AlbumGroupData,
    actions: AlbumGroupActions,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    forceExpanded: Boolean = false,
    onExpansionChanged: ((Boolean) -> Unit)? = null,
    highlightTrackId: Long? = null
) {
    var expanded by remember { mutableStateOf(false) }

    // Handle forced expansion
    LaunchedEffect(forceExpanded) {
        if (forceExpanded && !expanded) {
            expanded = true
            onExpansionChanged?.invoke(true)
        }
    }

    var currentProgress by remember { mutableStateOf(0f) }

    val dismissState = rememberDismissState(
        confirmValueChange = { value ->
            when (value) {
                DismissValue.DismissedToStart -> {
                    // Custom threshold for delete: require 70% progress
                    if (currentProgress >= SwipeThresholds.DELETE) {
                        actions.onDelete?.invoke()
                        true // Allow dismissal
                    } else {
                        false // Cancel dismissal
                    }
                }
                DismissValue.DismissedToEnd -> {
                    // Custom threshold for add: require 40% progress
                    if (currentProgress >= SwipeThresholds.ADD_TO_PLAYLIST) {
                        actions.onAddToPlaylist?.invoke()
                        false // Don't dismiss, just execute action
                    } else {
                        false // Cancel dismissal
                    }
                }
                else -> false
            }
        }
    )

    // Track the current progress
    androidx.compose.runtime.LaunchedEffect(dismissState.progress) {
        currentProgress = dismissState.progress
    }

    SwipeToDismiss(
        state = dismissState,
        directions = buildSet {
            if (actions.onDelete != null) add(DismissDirection.EndToStart)
            if (actions.onAddToPlaylist != null) add(DismissDirection.StartToEnd)
        },
        background = {
            val flickerColor = if (isSystemInDarkTheme()) Color(0xFF300063) else Color.LightGray
            val bgColor = when (dismissState.dismissDirection) {
                DismissDirection.StartToEnd -> MaterialTheme.colorScheme.primary
                else -> if (highlight) flickerColor else MaterialTheme.colorScheme.error
            }
            val icon = when (dismissState.dismissDirection) {
                DismissDirection.StartToEnd -> androidx.compose.material.icons.Icons.Default.Add
                else -> androidx.compose.material.icons.Icons.Default.Delete
            }
            val iconTint = when (dismissState.dismissDirection) {
                DismissDirection.StartToEnd -> MaterialTheme.colorScheme.onPrimary
                else -> if (highlight) Color.Transparent else MaterialTheme.colorScheme.onError
            }

            androidx.compose.foundation.layout.Box(
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
            val bg = if (highlight) flickerColor else backgroundColor

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg)
            ) {
                // Album Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!forceExpanded) {
                                val newExpanded = !expanded
                                expanded = newExpanded
                                onExpansionChanged?.invoke(newExpanded)
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cover Art
                    AsyncImage(
                        model = File(album.coverPath),
                        contentDescription = null,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp).weight(1f)) {
                        Text(text = album.title, style = MaterialTheme.typography.titleMedium)
                        Text(text = album.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                        Text(text = stringResource(id = R.string.library_track_count, album.trackCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }

                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(id = R.string.library_cd_expand)
                    )
                }

                // Expanded Tracks
                if (expanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        // Sort songs by difficulty name for better organization in beatmapsets
                        val sortedSongs = album.songs.sortedBy { it.difficultyName }
                        sortedSongs.forEachIndexed { index, song ->
                            androidx.compose.runtime.key(song.id) {
                                val isHighlightedTrack = highlightTrackId == song.id
                                TrackRowWithSwipe(
                                    song = song,
                                    onPlay = { actions.onTrackPlay?.invoke(song) },
                                    onDelete = { actions.onTrackDelete?.invoke(song) },
                                    onAddToPlaylist = { actions.onTrackAddToPlaylist?.invoke(song) },
                                    modifier = Modifier,
                                    backgroundColor = when {
                                        isHighlightedTrack -> MaterialTheme.colorScheme.primaryContainer
                                        index % 2 == 0 -> MaterialTheme.colorScheme.surface
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

/**
 * A simplified track row with swipe functionality for individual tracks within an album
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackRowWithSwipe(
    song: SongItemData,
    onPlay: () -> Unit,
    onDelete: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface
) {
    var currentProgress by remember { mutableStateOf(0f) }

    val dismissState = rememberDismissState(
        confirmValueChange = { value ->
            when (value) {
                DismissValue.DismissedToEnd -> {
                    // Custom threshold for add: require 40% progress
                    if (currentProgress >= SwipeThresholds.ADD_TO_PLAYLIST) {
                        onAddToPlaylist?.invoke()
                        false // Don't dismiss, just execute action
                    } else {
                        false // Cancel dismissal
                    }
                }
                DismissValue.DismissedToStart -> {
                    // Custom threshold for delete: require 70% progress
                    if (currentProgress >= SwipeThresholds.DELETE) {
                        onDelete?.invoke()
                        true // Allow dismissal
                    } else {
                        false // Cancel dismissal
                    }
                }
                else -> false
            }
        }
    )

    // Track the current progress
    androidx.compose.runtime.LaunchedEffect(dismissState.progress) {
        currentProgress = dismissState.progress
    }

    androidx.compose.material3.SwipeToDismiss(
        state = dismissState,
        directions = buildSet {
            if (onAddToPlaylist != null) add(DismissDirection.StartToEnd)
            if (onDelete != null) add(DismissDirection.EndToStart)
        },
        background = {
            val bgColor = when (dismissState.dismissDirection) {
                DismissDirection.StartToEnd -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
            }
            val icon = when (dismissState.dismissDirection) {
                DismissDirection.StartToEnd -> androidx.compose.material.icons.Icons.Default.Add
                else -> androidx.compose.material.icons.Icons.Default.Delete
            }
            val iconTint = when (dismissState.dismissDirection) {
                DismissDirection.StartToEnd -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onError
            }
            val alignment = when (dismissState.dismissDirection) {
                DismissDirection.StartToEnd -> Alignment.CenterStart
                else -> Alignment.CenterEnd
            }

            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = when (dismissState.dismissDirection) {
                        DismissDirection.StartToEnd -> stringResource(id = R.string.library_cd_add_playlist)
                        else -> stringResource(id = R.string.library_cd_delete)
                    },
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        dismissContent = {
            androidx.compose.foundation.layout.Row(
                modifier = modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .clickable { onPlay() }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.layout.Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(text = song.difficultyName ?: "", style = MaterialTheme.typography.titleMedium)
                    Text(text = song.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    )
}
