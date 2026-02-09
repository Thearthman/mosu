package com.mosu.app.ui.components

import android.util.Log
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mosu.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Swipe configuration for beatmap set items
 */
data class BeatmapSetSwipeActions(
    val onDelete: (() -> Unit)? = null,
    val onDeleteRevert: (() -> Unit)? = null,
    val onDeleteConfirmed: (() -> Unit)? = null,
    val onDeleteMessage: String? = null,
    val onSwipeRight: (() -> Unit)? = null,
    val onSwipeRightRevert: (() -> Unit)? = null,
    val onSwipeRightMessage: String? = null

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
    enableDismissFromStartToEnd: Boolean = swipeActions.onSwipeRight != null,
    enableDismissFromEndToStart: Boolean = swipeActions.onDelete != null,
    dismissOnDelete: Boolean = false,
    startToEndIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Add,
    endToStartIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Remove,
    snackbarHostState: SnackbarHostState? = null,
    externalScope: CoroutineScope? = null,
    content: @Composable () -> Unit
) {
    val localScope = rememberCoroutineScope()
    val scope = externalScope ?: localScope
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            Log.d("BeatmapSetSwipeItem", "Swipe detected: $value")
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    Log.d("BeatmapSetSwipeItem", "Action: SwipeRight")
                    swipeActions.onSwipeRight?.invoke()
                    if (snackbarHostState != null && swipeActions.onSwipeRightMessage != null) {
                        Log.d("BeatmapSetSwipeItem", "Showing snackbar: ${swipeActions.onSwipeRightMessage}")
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = swipeActions.onSwipeRightMessage,
                                actionLabel = "Redo",
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                Log.d("BeatmapSetSwipeItem", "Snackbar Action: Redo SwipeRight")
                                swipeActions.onSwipeRightRevert?.invoke()
                            }
                        }
                    }
                    false // Return false to snap back (keep item in list)
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    Log.d("BeatmapSetSwipeItem", "Action: Delete")
                    swipeActions.onDelete?.invoke()
                    if (snackbarHostState != null && swipeActions.onDeleteMessage != null) {
                        Log.d("BeatmapSetSwipeItem", "Showing snackbar: ${swipeActions.onDeleteMessage}")
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = swipeActions.onDeleteMessage,
                                actionLabel = "Redo",
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                Log.d("BeatmapSetSwipeItem", "Snackbar Action: Redo Delete")
                                swipeActions.onDeleteRevert?.invoke()
                            } else {
                                Log.d("BeatmapSetSwipeItem", "Snackbar Action: Confirm Delete")
                                swipeActions.onDeleteConfirmed?.invoke()
                            }
                        }
                    } else {
                        Log.d("BeatmapSetSwipeItem", "Snackbar skipped: host=${snackbarHostState != null}, msg=${swipeActions.onDeleteMessage != null}")
                        // If no snackbar logic is provided, delete immediately
                        swipeActions.onDeleteConfirmed?.invoke()
                    }
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
