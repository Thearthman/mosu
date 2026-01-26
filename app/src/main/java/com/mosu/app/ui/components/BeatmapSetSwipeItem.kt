package com.mosu.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mosu.app.R

/**
 * Swipe configuration for beatmap set items
 */
data class BeatmapSetSwipeActions(
    val onDelete: (() -> Unit)? = null,
    val onAddToPlaylist: (() -> Unit)? = null
)

/**
 * A swipe-to-dismiss wrapper specifically for beatmap set list items
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeatmapSetSwipeItem(
    swipeActions: BeatmapSetSwipeActions,
    highlight: Boolean = false,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    backgroundBrush: Brush? = null,
    modifier: Modifier = Modifier,
    enableDismissFromStartToEnd: Boolean = swipeActions.onAddToPlaylist != null,
    enableDismissFromEndToStart: Boolean = swipeActions.onDelete != null,
    dismissOnDelete: Boolean = false,
    startToEndIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Add,
    endToStartIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Remove,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    swipeActions.onAddToPlaylist?.invoke()
                    false // Return false to snap back (keep item in list)
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    swipeActions.onDelete?.invoke()
                    dismissOnDelete // Return true to dismiss if configured, else snap back
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = enableDismissFromStartToEnd,
        enableDismissFromEndToStart = enableDismissFromEndToStart,
        backgroundContent = {
            val flickerColor = if (isSystemInDarkTheme()) Color(0xFF300063) else Color.LightGray

            val bgColor = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
                SwipeToDismissBoxValue.EndToStart -> if (highlight) flickerColor else MaterialTheme.colorScheme.error
                else -> Color.Transparent // or your default settled state color
            }

            val icon = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> startToEndIcon
                SwipeToDismissBoxValue.EndToStart -> endToStartIcon
                else -> null // no icon when settled
            }

            val iconTint = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.onPrimary
                SwipeToDismissBoxValue.EndToStart -> if (highlight) Color.Transparent else MaterialTheme.colorScheme.onError
                else -> Color.Transparent
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd)
                    Alignment.CenterStart else Alignment.CenterEnd
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd)
                            stringResource(id = R.string.library_cd_add_playlist)
                        else
                            stringResource(id = R.string.library_cd_delete),
                        tint = iconTint,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        },
        content = {
            val flickerColor = if (isSystemInDarkTheme()) Color(0xFF300063) else Color.LightGray
            
            val finalBackgroundModifier = if (backgroundBrush != null) {
                Modifier.background(backgroundBrush)
            } else {
                Modifier.background(if (highlight) flickerColor else backgroundColor)
            }

            Box(modifier = modifier.then(finalBackgroundModifier)) {
                content()
            }
        }
    )
}
