package com.mosu.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mosu.app.R

/**
 * Data class representing a playlist option
 */
data class PlaylistOption(
    val id: Long,
    val name: String
)

/**
 * A reusable playlist selector dialog
 */
@Composable
fun PlaylistSelectorDialog(
    playlists: List<PlaylistOption>,
    selectedPlaylistIds: Set<Long>,
    onSelectionChanged: (Set<Long>) -> Unit,
    onAddToPlaylist: (Long, Long) -> Unit, // (playlistId, beatmapSetId)
    onRemoveFromPlaylist: (Long, Long) -> Unit, // (playlistId, beatmapSetId)
    beatmapSetId: Long,
    onDismiss: () -> Unit
) {
    var currentSelection by mutableStateOf(selectedPlaylistIds)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.playlist_dialog_title)) },
        text = {
            Column {
                LazyColumn {
                    items(playlists.size) { index ->
                        val playlist = playlists[index]
                        val checked = currentSelection.contains(playlist.id)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newChecked = !checked
                                    currentSelection = if (newChecked) {
                                        currentSelection + playlist.id
                                    } else {
                                        currentSelection - playlist.id
                                    }
                                    onSelectionChanged(currentSelection)

                                    if (newChecked) {
                                        onAddToPlaylist(playlist.id, beatmapSetId)
                                    } else {
                                        onRemoveFromPlaylist(playlist.id, beatmapSetId)
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { newChecked ->
                                    currentSelection = if (newChecked) {
                                        currentSelection + playlist.id
                                    } else {
                                        currentSelection - playlist.id
                                    }
                                    onSelectionChanged(currentSelection)

                                    if (newChecked) {
                                        onAddToPlaylist(playlist.id, beatmapSetId)
                                    } else {
                                        onRemoveFromPlaylist(playlist.id, beatmapSetId)
                                    }
                                }
                            )
                            Text(
                                text = playlist.name,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
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
