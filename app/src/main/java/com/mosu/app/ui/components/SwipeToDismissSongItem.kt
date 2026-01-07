package com.mosu.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Generic swipe configuration for any content
 */
data class GenericSwipeActions(
    val onDelete: (() -> Unit)? = null,
    val onAddToPlaylist: (() -> Unit)? = null
)

/**
 * A generic swipe-to-dismiss wrapper for any content
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDismissWrapper(
    swipeActions: GenericSwipeActions,
    highlight: Boolean = false,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    modifier: Modifier = Modifier,
    enableDismissFromStartToEnd: Boolean = swipeActions.onAddToPlaylist != null,
    enableDismissFromEndToStart: Boolean = swipeActions.onDelete != null,
    dismissOnDelete: Boolean = false,
    content: @Composable () -> Unit
) {
    // REMOVE: var currentProgress by remember { mutableStateOf(0f) }
    
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // REMOVE: if (currentProgress >= SwipeThresholds.ADD_TO_PLAYLIST)
                    // ACTION: Just execute. If the user swiped past default threshold, do it.
                    swipeActions.onAddToPlaylist?.invoke()
                    false // Return false to snap back (keep item in list)
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // REMOVE: if (currentProgress >= SwipeThresholds.DELETE)
                    swipeActions.onDelete?.invoke()
                    dismissOnDelete // Return true to dismiss if configured, else snap back
                }
                else -> false
            }
        }
    )

    // REMOVE: LaunchedEffect(dismissState.progress) { ... }

    // var currentProgress by remember { mutableStateOf(0f) }

    // val dismissState = rememberSwipeToDismissBoxState(
    //     confirmValueChange = { value ->
    //         when (value) {
    //             SwipeToDismissBoxValue.StartToEnd -> {
    //                 // Custom threshold for add: require 40% progress
    //                 if (currentProgress >= SwipeThresholds.ADD_TO_PLAYLIST) {
    //                     swipeActions.onAddToPlaylist?.invoke()
    //                     false // Don't dismiss, just execute action
    //                 } else {
    //                     false // Cancel dismissal
    //                 }
    //             }
    //             SwipeToDismissBoxValue.EndToStart -> {
    //                 // Custom threshold for delete: require 70% progress
    //                 if (currentProgress >= SwipeThresholds.DELETE) {
    //                     swipeActions.onDelete?.invoke()
    //                     dismissOnDelete // Allow dismissal only if specified
    //                 } else {
    //                     false // Cancel dismissal
    //                 }
    //             }
    //             else -> false
    //         }
    //     }
    // )

    // Track the current progress
//    androidx.compose.runtime.LaunchedEffect(dismissState.progress) {
//        currentProgress = dismissState.progress
//    }

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
                SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Add
                SwipeToDismissBoxValue.EndToStart -> Icons.Default.Remove
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
            val bg = if (highlight) flickerColor else backgroundColor

            Box(modifier = modifier.background(bg)) {
                content()
            }
        }
    )
}

/**
 * A SongItem wrapped with swipe-to-dismiss functionality
 */
@Composable
fun SwipeToDismissSongItem(
    song: SongItemData,
    onClick: () -> Unit,
    swipeActions: SwipeActions,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    coverStartPadding: androidx.compose.ui.unit.Dp = 0.dp,
    textEndPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    SwipeToDismissWrapper(
        swipeActions = GenericSwipeActions(
            onDelete = swipeActions.onDelete,
            // FIX: Only create the lambda if the action is not null
            onAddToPlaylist = swipeActions.onAddToPlaylist?.let { action ->
                { action(song) }
            }
        ),
        highlight = highlight,
        backgroundColor = backgroundColor,
        modifier = modifier
    ) {
        SongItem(
            song = song,
            // FIX: Pass the real onClick here directly
            onClick = onClick,
            // FIX: Remove Modifier.clickable { onClick() } to avoid conflict
            modifier = Modifier, 
            backgroundColor = Color.Transparent, // Background handled by wrapper
            coverStartPadding = coverStartPadding,
            textEndPadding = textEndPadding
        )
    }
}
