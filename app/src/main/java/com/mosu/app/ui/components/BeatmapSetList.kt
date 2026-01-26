package com.mosu.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * Extension function to add a list of BeatmapSets to a LazyColumn
 */
fun LazyListScope.beatmapSetList(
    sets: List<BeatmapSetData>,
    actions: BeatmapSetActions,
    config: BeatmapSetListConfig = BeatmapSetListConfig(),
    highlightedSetId: Long? = null,
    highlightedTrackId: Long? = null,
    backgroundBrush: @Composable (BeatmapSetData) -> Brush? = { null }
) {
    items(
        items = sets,
        key = { it.id }
    ) { set ->
        val isHighlighted = set.id == highlightedSetId
        val brush = backgroundBrush(set)

        if (set.isExpandable) {
            // Expandable beatmap set item (handles its own swipe wrapper)
            BeatmapSetExpandableItem(
                album = set,
                actions = actions,
                highlight = isHighlighted,
                highlightTrackId = highlightedTrackId,
                backgroundBrush = brush
            )
        } else {
            // Flat beatmap set item
            if (actions.onSwipeLeft != null || actions.onSwipeRight != null) {
                BeatmapSetSwipeItem(
                    swipeActions = BeatmapSetSwipeActions(
                        onDelete = { actions.onSwipeLeft?.invoke(set) },
                        onAddToPlaylist = actions.onSwipeRight?.let { action -> { action(set) } }
                    ),
                    highlight = isHighlighted,
                    backgroundBrush = brush,
                    startToEndIcon = actions.swipeRightIcon ?: Icons.Default.Add,
                    endToStartIcon = actions.swipeLeftIcon ?: Icons.Default.Delete,
                    enableDismissFromStartToEnd = actions.onSwipeRight != null,
                    enableDismissFromEndToStart = actions.onSwipeLeft != null
                ) {
                    BeatmapSetItem(
                        set = set,
                        actions = actions,
                        highlight = isHighlighted
                    )
                }
            } else {
                BeatmapSetItem(
                    set = set,
                    actions = actions,
                    highlight = isHighlighted,
                    backgroundBrush = brush
                )
            }
        }

        if (config.showDividers) {
            HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
        }
    }
}

/**
 * A standalone BeatmapSetList component wrapping a LazyColumn
 */
@Composable
fun BeatmapSetList(
    sets: List<BeatmapSetData>,
    actions: BeatmapSetActions,
    config: BeatmapSetListConfig = BeatmapSetListConfig(),
    modifier: Modifier = Modifier,
    highlightedSetId: Long? = null,
    highlightedTrackId: Long? = null,
    backgroundBrush: @Composable (BeatmapSetData) -> Brush? = { null }
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = config.contentPadding
    ) {
        beatmapSetList(
            sets = sets,
            actions = actions,
            config = config,
            highlightedSetId = highlightedSetId,
            highlightedTrackId = highlightedTrackId,
            backgroundBrush = backgroundBrush
        )
    }
}
