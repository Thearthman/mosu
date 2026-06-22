package com.mosu.app.ui.components

import android.util.Log
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mosu.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Swipe configuration for beatmap set items
 */
data class BeatmapSetSwipeActions(
    val onDelete: (() -> Unit)? = null,
    val onDeleteRevert: (() -> Unit)? = null,
    val onDeleteConfirmed: (suspend () -> Unit)? = null,
    val onDeleteMessage: String? = null,
    val onSwipeRight: (() -> Unit)? = null,
    val onSwipeRightRevert: (() -> Unit)? = null,
    val onSwipeRightMessage: String? = null

)

/**
 * A swipe-to-dismiss wrapper specifically for beatmap set list items
 */
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
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current.density
    var offsetPx by remember { mutableStateOf(0f) }
    var rowWidthPx by remember { mutableStateOf(0f) }

    fun semanticDirectionForOffset(offsetPx: Float): BeatmapSwipeDirection {
        val swipedRight = offsetPx > 0f
        return when {
            swipedRight && layoutDirection == LayoutDirection.Ltr -> BeatmapSwipeDirection.StartToEnd
            swipedRight -> BeatmapSwipeDirection.EndToStart
            layoutDirection == LayoutDirection.Ltr -> BeatmapSwipeDirection.EndToStart
            else -> BeatmapSwipeDirection.StartToEnd
        }
    }

    fun isDirectionEnabled(direction: BeatmapSwipeDirection): Boolean {
        return when (direction) {
            BeatmapSwipeDirection.StartToEnd -> enableDismissFromStartToEnd
            BeatmapSwipeDirection.EndToStart -> enableDismissFromEndToStart
        }
    }

    fun performAction(direction: BeatmapSwipeDirection) {
        Log.d("BeatmapSetSwipeItem", "Swipe detected: $direction")
        when (direction) {
            BeatmapSwipeDirection.StartToEnd -> {
                Log.d("BeatmapSetSwipeItem", "Action: SwipeRight")
                swipeActions.onSwipeRight?.invoke()
                if (snackbarHostState != null && swipeActions.onSwipeRightMessage != null) {
                    Log.d("BeatmapSetSwipeItem", "Showing snackbar: ${swipeActions.onSwipeRightMessage}")
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = swipeActions.onSwipeRightMessage,
                            actionLabel = context.getString(R.string.snackbar_redo),
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            Log.d("BeatmapSetSwipeItem", "Snackbar Action: Redo SwipeRight")
                            swipeActions.onSwipeRightRevert?.invoke()
                        }
                    }
                }
            }
            BeatmapSwipeDirection.EndToStart -> {
                Log.d("BeatmapSetSwipeItem", "Action: Delete")
                swipeActions.onDelete?.invoke()
                if (snackbarHostState != null && swipeActions.onDeleteMessage != null) {
                    Log.d("BeatmapSetSwipeItem", "Showing snackbar: ${swipeActions.onDeleteMessage}")
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = swipeActions.onDeleteMessage,
                            actionLabel = context.getString(R.string.snackbar_redo),
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
                    scope.launch {
                        swipeActions.onDeleteConfirmed?.invoke()
                    }
                }
            }
        }
    }

    val flickerColor = if (isSystemInDarkTheme()) Color(0xFF300063) else Color.LightGray
    val currentDirection = offsetPx.takeIf { it != 0f }?.let(::semanticDirectionForOffset)

    val bgColor = when (currentDirection) {
        BeatmapSwipeDirection.StartToEnd -> MaterialTheme.colorScheme.primary
        BeatmapSwipeDirection.EndToStart -> if (highlight) flickerColor else MaterialTheme.colorScheme.error
        null -> Color.Transparent
    }

    val icon = when (currentDirection) {
        BeatmapSwipeDirection.StartToEnd -> startToEndIcon
        BeatmapSwipeDirection.EndToStart -> endToStartIcon
        null -> null
    }

    val iconTint = when (currentDirection) {
        BeatmapSwipeDirection.StartToEnd -> MaterialTheme.colorScheme.onPrimary
        BeatmapSwipeDirection.EndToStart -> if (highlight) Color.Transparent else MaterialTheme.colorScheme.onError
        null -> Color.Transparent
    }

    val contentAlignment = when (currentDirection) {
        BeatmapSwipeDirection.StartToEnd -> Alignment.CenterStart
        BeatmapSwipeDirection.EndToStart -> Alignment.CenterEnd
        null -> Alignment.CenterEnd
    }

    val finalBackgroundModifier = if (backgroundBrush != null) {
        Modifier.background(backgroundBrush)
    } else {
        Modifier.background(if (highlight) flickerColor else backgroundColor)
    }

    Box(
        modifier = Modifier.onSizeChanged { rowWidthPx = it.width.toFloat() }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(bgColor)
                .padding(horizontal = 20.dp),
            contentAlignment = contentAlignment
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = if (currentDirection == BeatmapSwipeDirection.StartToEnd)
                        stringResource(id = R.string.library_cd_add_playlist)
                    else
                        stringResource(id = R.string.library_cd_delete),
                    tint = iconTint,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Box(
            modifier = modifier
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .pointerInput(
                    enableDismissFromStartToEnd,
                    enableDismissFromEndToStart,
                    layoutDirection,
                    rowWidthPx,
                    density
                ) {
                    while (true) {
                        var releaseResult: SwipeReleaseResult? = null
                        awaitPointerEventScope {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startPosition = down.position
                            var lockedDirection: BeatmapSwipeDirection? = null
                            val allowPositiveOffset = isDirectionEnabled(semanticDirectionForOffset(1f))
                            val allowNegativeOffset = isDirectionEnabled(semanticDirectionForOffset(-1f))
                            val touchSlop = viewConfiguration.touchSlop
                            while (lockedDirection == null) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitPointerEventScope
                                if (!change.pressed) return@awaitPointerEventScope

                                val drag = change.position - startPosition
                                if (abs(drag.x) < touchSlop && abs(drag.y) < touchSlop) continue

                                if (!SwipeThresholds.hasHorizontalLock(drag.x, drag.y) || drag.x == 0f) {
                                    waitForAllPointersUp()
                                    return@awaitPointerEventScope
                                }

                                val direction = semanticDirectionForOffset(drag.x)
                                if (!isDirectionEnabled(direction)) {
                                    waitForAllPointersUp()
                                    return@awaitPointerEventScope
                                }

                                lockedDirection = direction
                                change.consume()
                                offsetPx = drag.x.coerceInSwipeBounds(
                                    widthPx = rowWidthPx,
                                    allowNegative = allowNegativeOffset,
                                    allowPositive = allowPositiveOffset
                                )
                            }

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitPointerEventScope
                                if (!change.pressed) {
                                    val direction = offsetPx.takeIf { it != 0f }
                                        ?.let(::semanticDirectionForOffset)
                                        ?: lockedDirection
                                    val committed = SwipeThresholds.shouldCommit(offsetPx, rowWidthPx, density) &&
                                        isDirectionEnabled(direction)

                                    val targetOffset = if (
                                        committed &&
                                        direction == BeatmapSwipeDirection.EndToStart &&
                                        dismissOnDelete
                                    ) {
                                        if (offsetPx > 0f) rowWidthPx else -rowWidthPx
                                    } else {
                                        0f
                                    }
                                    releaseResult = SwipeReleaseResult(
                                        direction = direction,
                                        committed = committed,
                                        targetOffset = targetOffset
                                    )
                                    return@awaitPointerEventScope
                                }

                                val positionChange = change.positionChange().x
                                if (positionChange != 0f) {
                                    val nextOffset = (offsetPx + positionChange)
                                        .coerceInSwipeBounds(
                                            widthPx = rowWidthPx,
                                            allowNegative = allowNegativeOffset,
                                            allowPositive = allowPositiveOffset
                                        )
                                    offsetPx = nextOffset
                                }
                                change.consume()
                            }
                        }

                        releaseResult?.let { result ->
                            if (result.committed) {
                                performAction(result.direction)
                            }
                            animate(
                                initialValue = offsetPx,
                                targetValue = result.targetOffset,
                                animationSpec = tween(durationMillis = 180)
                            ) { value, _ ->
                                offsetPx = value
                            }
                        }
                    }
                }
        ) {
            Box(modifier = Modifier.matchParentSize().then(finalBackgroundModifier))
            content()
        }
    }
}

private data class SwipeReleaseResult(
    val direction: BeatmapSwipeDirection,
    val committed: Boolean,
    val targetOffset: Float
)

private fun Float.coerceInSwipeBounds(
    widthPx: Float,
    allowNegative: Boolean,
    allowPositive: Boolean
): Float {
    val minOffset = if (allowNegative) -widthPx else 0f
    val maxOffset = if (allowPositive) widthPx else 0f
    return coerceIn(minOffset, maxOffset)
}

private suspend fun AwaitPointerEventScope.waitForAllPointersUp() {
    do {
        val event = awaitPointerEvent()
    } while (event.changes.any { it.pressed })
}
