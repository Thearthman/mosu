package com.mosu.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
    val onTrackAddToPlaylist: ((SongItemData) -> Unit)? = null,
    val onLongClick: (() -> Unit)? = null
)

/**
 * An expandable album group component with swipe-to-dismiss functionality
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumGroup(
    album: AlbumGroupData,
    actions: AlbumGroupActions,
    highlight: Boolean = false,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    forceExpanded: Boolean = false,
    onExpansionChanged: ((Boolean) -> Unit)? = null,
    highlightTrackId: Long? = null,
    startToEndIcon: ImageVector = Icons.Default.Add,
    endToStartIcon: ImageVector = Icons.Default.Remove
) {
    var expanded by remember { mutableStateOf(false) }

    // Handle forced expansion
    LaunchedEffect(forceExpanded) {
        if (forceExpanded && !expanded) {
            expanded = true
            onExpansionChanged?.invoke(true)
        }
    }

    SwipeToDismissWrapper(
        swipeActions = GenericSwipeActions(
            onDelete = actions.onDelete,
            onAddToPlaylist = actions.onAddToPlaylist
        ),
        highlight = highlight,
        backgroundColor = backgroundColor,
        startToEndIcon = startToEndIcon,
        endToStartIcon = endToStartIcon
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            // Album Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            val newExpanded = !expanded
                            expanded = newExpanded
                            onExpansionChanged?.invoke(newExpanded)
                        },
                        onLongClick = actions.onLongClick
                    )
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
                                },
                                startToEndIcon = startToEndIcon,
                                endToStartIcon = endToStartIcon
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A simplified track row with swipe functionality for individual tracks within an album
 */
@Composable
private fun TrackRowWithSwipe(
    song: SongItemData,
    onPlay: () -> Unit,
    onDelete: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    startToEndIcon: ImageVector = Icons.Default.Add,
    endToStartIcon: ImageVector = Icons.Default.Remove
) {
    SwipeToDismissWrapper(
        swipeActions = GenericSwipeActions(
            onDelete = onDelete,
            onAddToPlaylist = onAddToPlaylist
        ),
        backgroundColor = backgroundColor,
        modifier = modifier,
        dismissOnDelete = true, // Allow dismissal on delete for track rows
        startToEndIcon = startToEndIcon,
        endToStartIcon = endToStartIcon
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable { onPlay() }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.difficultyName ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
