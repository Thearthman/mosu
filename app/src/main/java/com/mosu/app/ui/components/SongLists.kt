package com.mosu.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Actions that can be performed on songs in different contexts
 */
sealed class SongListActions {
    data class LibraryActions(
        val onDelete: (SongItemData) -> Unit,
        val onAddToPlaylist: (SongItemData) -> Unit,
        val onClick: (SongItemData) -> Unit = { },
        val onLongClick: (SongItemData) -> Unit = { }
    ) : SongListActions()

    data class PlaylistActions(
        val onRemove: (SongItemData) -> Unit,
        val onAddToOtherPlaylist: (SongItemData) -> Unit,
        val onClick: (SongItemData) -> Unit = { },
        val onLongClick: (SongItemData) -> Unit = { },
        val isUndownloaded: (SongItemData) -> Boolean = { false }
    ) : SongListActions()
}

/**
 * Configuration for SwipeableSongList appearance and behavior
 */
data class SongListConfig(
    val showDividers: Boolean = true,
    val dividerPaddingStart: Dp = 80.dp,
    val dividerPaddingEnd: Dp = 20.dp,
    val coverStartPadding: Dp = 0.dp,
    val textEndPadding: Dp = 0.dp,
    val contentPadding: PaddingValues = PaddingValues(0.dp),
    val verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(0.dp),
    val lastItemModifier: Modifier = Modifier
)

/**
 * Configuration for SelectableSongItem appearance and behavior
 */
data class SelectableSongConfig(
    val coverSize: Dp = 40.dp, // Smaller cover for compact menus
    val verticalPadding: Dp = 0.dp, // Minimal padding for tight spacing
    val checkboxMarginEnd: Dp = 14.dp, // No margin after checkbox
    val backgroundColor: Color? = null, // Will use dialog surface color
    val coverStartPadding: Dp = 0.dp,
    val textEndPadding: Dp = 0.dp,
    val textStyle: androidx.compose.ui.text.TextStyle? = null, // Will use default if null
    val secondaryTextStyle: androidx.compose.ui.text.TextStyle? = null // Will use default if null
)

/**
 * A reusable component for displaying swipeable song lists with different configurations
 */
@Composable
fun SwipeableSongList(
    modifier: Modifier = Modifier,
    songs: List<SongItemData>,
    actions: SongListActions,
    config: SongListConfig = SongListConfig()
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = config.contentPadding,
        verticalArrangement = config.verticalArrangement
    ) {
        itemsIndexed(songs) { index, song ->
            val isLastItem = index == songs.size - 1
            val itemModifier = if (isLastItem) config.lastItemModifier else Modifier

            when (actions) {
                is SongListActions.LibraryActions -> {
                    SwipeToDismissSongItem(
                        song = song,
                        onClick = { actions.onClick(song) },
                        onLongClick = { actions.onLongClick(song) },
                        swipeActions = SwipeActions(
                            onDelete = { actions.onDelete(song) },
                            onAddToPlaylist = { actions.onAddToPlaylist(song) }
                        ),
                        coverStartPadding = config.coverStartPadding,
                        textEndPadding = config.textEndPadding,
                        modifier = itemModifier
                    )

                    if (config.showDividers && !isLastItem) {
                        HorizontalDivider(modifier = Modifier.padding(start = config.dividerPaddingStart, end = config.dividerPaddingEnd))
                    }
                }

                is SongListActions.PlaylistActions -> {
                    SwipeToDismissSongItem(
                        song = song,
                        onClick = { actions.onClick(song) },
                        onLongClick = { actions.onLongClick(song) },
                        swipeActions = SwipeActions(
                            onDelete = { actions.onRemove(song) },
                            onAddToPlaylist = { actions.onAddToOtherPlaylist(song) }
                        ),
                        backgroundColor = if (actions.isUndownloaded(song)) {
                            androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            androidx.compose.material3.MaterialTheme.colorScheme.surface
                        },
                        coverStartPadding = config.coverStartPadding,
                        textEndPadding = config.textEndPadding,
                        modifier = itemModifier
                    )

                    if (config.showDividers && !isLastItem) {
                        HorizontalDivider(modifier = Modifier.padding(start = config.dividerPaddingStart, end = config.dividerPaddingEnd))
                    }
                }
            }
        }
    }
}



/**
 * A selectable song item with checkbox for playlist add dialogs
 */
@Composable
fun SelectableSongItem(
    modifier: Modifier = Modifier,
    song: SongItemData,
    isSelected: Boolean,
    isDisabled: Boolean,
    onSelectionChanged: (Boolean) -> Unit,
    config: SelectableSongConfig = SelectableSongConfig()
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(config.backgroundColor ?: MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = !isDisabled) {
                onSelectionChanged(!isSelected)
            }
            .padding(vertical = config.verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected || isDisabled,
            onCheckedChange = if (isDisabled) null else { checked -> onSelectionChanged(checked) },
            enabled = !isDisabled,
            modifier = Modifier
                .size(18.dp) // Smaller checkbox size
        )
        Spacer(modifier = Modifier.width(config.checkboxMarginEnd))
        SongItem(
            song = song,
            onClick = { onSelectionChanged(!isSelected) },
            backgroundColor = androidx.compose.ui.graphics.Color.Transparent, // No background since Row already has it
            coverStartPadding = config.coverStartPadding,
            textEndPadding = config.textEndPadding,
            coverSize = config.coverSize,
            titleTextStyle = config.textStyle ?: androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            subtitleTextStyle = config.secondaryTextStyle ?: androidx.compose.material3.MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * A list of selectable song items with checkboxes for playlist add dialogs
 */
@Composable
fun SelectableSongList(
    songs: List<SongItemData>,
    isSelected: (SongItemData) -> Boolean,
    onSelectionChanged: (SongItemData, Boolean) -> Unit,
    isDisabled: (SongItemData) -> Boolean = { false },
    config: SelectableSongConfig = SelectableSongConfig(),
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.spacedBy(config.verticalPadding)
    ) {
        itemsIndexed(songs) { _, song ->
            SelectableSongItem(
                song = song,
                isSelected = isSelected(song),
                isDisabled = isDisabled(song),
                onSelectionChanged = { selected -> onSelectionChanged(song, selected) },
                config = config
            )
        }
    }
}