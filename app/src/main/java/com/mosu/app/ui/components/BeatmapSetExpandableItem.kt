package com.mosu.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mosu.app.R
import kotlinx.coroutines.CoroutineScope
import java.io.File

/**
 * An expandable beatmap set item header with swipe actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BeatmapSetHeaderItem(
    album: BeatmapSetData,
    actions: BeatmapSetActions,
    expanded: Boolean,
    onExpandClick: () -> Unit,
    highlight: Boolean = false,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    backgroundBrush: Brush? = null,
    startToEndIcon: ImageVector = actions.swipeRightIcon ?: Icons.Default.Add,
    endToStartIcon: ImageVector = actions.swipeLeftIcon ?: Icons.Default.Remove,
    snackbarHostState: SnackbarHostState? = actions.snackbarHostState,
    externalScope: CoroutineScope? = actions.coroutineScope
) {
    BeatmapSetSwipeItem(
        swipeActions = BeatmapSetSwipeActions(
            onDelete = { actions.onSwipeLeft?.invoke(album) },
            onDeleteRevert = { actions.onSwipeLeftRevert?.invoke(album) },
            onDeleteConfirmed = { actions.onSwipeLeftConfirmed?.invoke(album) },
            onDeleteMessage = actions.onSwipeLeftMessage?.invoke(album),
            onSwipeRight = { actions.onSwipeRight?.invoke(album) },
            onSwipeRightRevert = { actions.onSwipeRightRevert?.invoke(album) },
            onSwipeRightMessage = actions.onSwipeRightMessage?.invoke(album)
        ),
        highlight = highlight,
        backgroundColor = backgroundColor,
        backgroundBrush = backgroundBrush,
        startToEndIcon = startToEndIcon,
        endToStartIcon = endToStartIcon,
        enableDismissFromStartToEnd = actions.onSwipeRight != null,
        enableDismissFromEndToStart = actions.onSwipeLeft != null,
        snackbarHostState = snackbarHostState,
        externalScope = externalScope
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp) // Unified height
            .combinedClickable(
                onClick = onExpandClick,
                onLongClick = { actions.onLongClick(album) }
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover Art
        AsyncImage(
            model = if (album.coverPath != null) File(album.coverPath) else album.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp) // Adjusted for 60dp
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )

        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp).weight(1f)) {
            Text(text = album.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = album.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(id = R.string.library_cd_expand),
            modifier = Modifier.size(24.dp)
        )
    }
    }
}

/**
 * A track row within an expandable beatmap set item
 */
@Composable
fun BeatmapSetTrackItem(
    track: BeatmapTrackData,
    onPlay: () -> Unit,
    onDelete: (() -> Unit)?,
    onDeleteRevert: (() -> Unit)? = null,
    onDeleteConfirmed: (suspend () -> Unit)? = null,
    onDeleteMessage: String? = null,
    onAddToPlaylist: (() -> Unit)?,
    onAddToPlaylistRevert: (() -> Unit)? = null,
    onAddToPlaylistMessage: String? = null,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    startToEndIcon: ImageVector = Icons.Default.Add,
    endToStartIcon: ImageVector = Icons.Default.Remove,
    snackbarHostState: SnackbarHostState? = null,
    externalScope: CoroutineScope? = null
) {
    BeatmapSetSwipeItem(
        swipeActions = BeatmapSetSwipeActions(
            onDelete = onDelete,
            onDeleteRevert = onDeleteRevert,
            onDeleteConfirmed = onDeleteConfirmed,
            onDeleteMessage = onDeleteMessage,
            onSwipeRight = onAddToPlaylist,
            onSwipeRightRevert = onAddToPlaylistRevert,
            onSwipeRightMessage = onAddToPlaylistMessage
        ),
        backgroundColor = backgroundColor,
        modifier = modifier,
        dismissOnDelete = true,
        startToEndIcon = startToEndIcon,
        endToStartIcon = endToStartIcon,
        enableDismissFromStartToEnd = onAddToPlaylist != null,
        enableDismissFromEndToStart = onDelete != null,
        snackbarHostState = snackbarHostState,
        externalScope = externalScope
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp) // Unified height
                .clickable { onPlay() }
                .padding(horizontal = 16.dp), // Match header padding
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.difficultyName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
