package com.mosu.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
    sets.forEach { set ->
        val isHighlighted = set.id == highlightedSetId
        val isExpanded = config.expandedIds.contains(set.id)

        if (set.isExpandable) {
            // Header item for expandable sets
            item(key = "header_${set.id}") {
                val brush = backgroundBrush(set)
                Column {
                    BeatmapSetHeaderItem(
                        album = set,
                        actions = actions,
                        expanded = isExpanded,
                        onExpandClick = {
                            config.onExpansionChanged(set.id, !isExpanded)
                        },
                        highlight = isHighlighted,
                        backgroundBrush = brush
                    )
                    if (config.showDividers) {
                        HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
                    }
                }
            }

            // Track items if expanded
            if (isExpanded) {
                val sortedSongs = set.tracks.sortedBy { it.difficultyName }
                items(
                    items = sortedSongs,
                    key = { "track_${set.id}_${it.id}" }
                ) { track ->
                    val isHighlightedTrack = highlightedTrackId == track.id
                    val trackIndex = remember(track, sortedSongs) { sortedSongs.indexOf(track) }
                    
                    Column {
                        BeatmapSetTrackItem(
                            track = track,
                            onPlay = { actions.onTrackPlay?.invoke(track) },
                            onDelete = actions.onTrackSwipeLeft?.let { action -> { action(track) } },
                            onAddToPlaylist = actions.onTrackSwipeRight?.let { action -> { action(track) } },
                            modifier = Modifier,
                            backgroundColor = when {
                                isHighlightedTrack -> MaterialTheme.colorScheme.primaryContainer
                                trackIndex % 2 == 0 -> MaterialTheme.colorScheme.surface
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            startToEndIcon = actions.swipeRightIcon ?: Icons.Default.Add,
                            endToStartIcon = actions.swipeLeftIcon ?: Icons.Default.Delete
                        )
                        if (config.showDividers) {
                            HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
                        }
                    }
                }
            }
        } else {
            // Flat beatmap set item
            item(key = "set_${set.id}") {
                val brush = backgroundBrush(set)
                Column {
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
                    if (config.showDividers) {
                        HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
                    }
                }
            }
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
    listState: LazyListState = rememberLazyListState(),
    backgroundBrush: @Composable (BeatmapSetData) -> Brush? = { null }
) {
    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
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

        if (config.showScrollbar) {
            DraggableScrollbar(
                state = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(vertical = config.contentPadding.calculateTopPadding())
            )
        }
    }
}
