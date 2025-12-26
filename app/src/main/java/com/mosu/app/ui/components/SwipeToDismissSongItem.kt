package com.mosu.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.SwipeToDismissDefaults
import androidx.compose.material3.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mosu.app.R

/**
 * Configuration for swipe actions
 */
data class SwipeActions(
    val onDelete: (() -> Unit)? = null,
    val onAddToPlaylist: ((SongItemData) -> Unit)? = null
)

/**
 * A SongItem wrapped with swipe-to-dismiss functionality
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDismissSongItem(
    song: SongItemData,
    onClick: () -> Unit,
    swipeActions: SwipeActions,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    backgroundColor: Color = MaterialTheme.colorScheme.surface
) {
    val dismissState = rememberDismissState(
        confirmValueChange = { value ->
            when (value) {
                DismissValue.DismissedToStart -> {
                    swipeActions.onDelete?.invoke()
                    false
                }
                DismissValue.DismissedToEnd -> {
                    swipeActions.onAddToPlaylist?.invoke(song)
                    false
                }
                else -> false
            }
        }
    )

    SwipeToDismiss(
        state = dismissState,
        directions = buildSet {
            if (swipeActions.onDelete != null) add(DismissDirection.EndToStart)
            if (swipeActions.onAddToPlaylist != null) add(DismissDirection.StartToEnd)
        },
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
            val bg = if (highlight) flickerColor else backgroundColor

            SongItem(
                song = song,
                onClick = onClick,
                modifier = modifier.background(bg),
                backgroundColor = bg
            )
        }
    )
}
