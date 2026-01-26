package com.mosu.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

/**
 * Data class representing a simple beatmap item for selection
 */
data class SelectableBeatmapData(
    val title: String,
    val artist: String,
    val coverPath: String,
    val difficultyName: String? = null,
    val id: Long
)

/**
 * Configuration for SelectableBeatmapItem appearance and behavior
 */
data class SelectableBeatmapConfig(
    val coverSize: Dp = 40.dp,
    val verticalPadding: Dp = 0.dp,
    val checkboxMarginEnd: Dp = 14.dp,
    val backgroundColor: Color? = null,
    val coverStartPadding: Dp = 0.dp,
    val textEndPadding: Dp = 0.dp,
    val textStyle: androidx.compose.ui.text.TextStyle? = null,
    val secondaryTextStyle: androidx.compose.ui.text.TextStyle? = null
)

/**
 * A reusable component for displaying a simple beatmap item
 * Used internally by SelectableBeatmapItem
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SimpleBeatmapItem(
    beatmap: SelectableBeatmapData,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    showDifficulty: Boolean = false,
    coverStartPadding: Dp = 0.dp,
    textEndPadding: Dp = 0.dp,
    coverSize: Dp = 50.dp,
    titleTextStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    subtitleTextStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp)
            .background(backgroundColor)
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = onLongClick
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = File(beatmap.coverPath),
            contentDescription = null,
            modifier = Modifier
                .padding(start = coverStartPadding)
                .size(coverSize)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .padding(start = 16.dp, end = textEndPadding)
                .weight(1f)
        ) {
            Text(
                text = beatmap.title,
                style = titleTextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = if (showDifficulty && beatmap.difficultyName != null) beatmap.difficultyName else beatmap.artist
            Text(
                text = subtitle,
                style = subtitleTextStyle,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * A selectable beatmap item with checkbox for playlist add dialogs
 */
@Composable
fun SelectableBeatmapItem(
    modifier: Modifier = Modifier,
    beatmap: SelectableBeatmapData,
    isSelected: Boolean,
    isDisabled: Boolean,
    onSelectionChanged: (Boolean) -> Unit,
    config: SelectableBeatmapConfig = SelectableBeatmapConfig()
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
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(config.checkboxMarginEnd))
        SimpleBeatmapItem(
            beatmap = beatmap,
            onClick = { onSelectionChanged(!isSelected) },
            backgroundColor = Color.Transparent,
            coverStartPadding = config.coverStartPadding,
            textEndPadding = config.textEndPadding,
            coverSize = config.coverSize,
            titleTextStyle = config.textStyle ?: MaterialTheme.typography.bodyMedium,
            subtitleTextStyle = config.secondaryTextStyle ?: MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * A list of selectable beatmap items with checkboxes for playlist add dialogs
 */
@Composable
fun SelectableBeatmapList(
    beatmaps: List<SelectableBeatmapData>,
    isSelected: (SelectableBeatmapData) -> Boolean,
    onSelectionChanged: (SelectableBeatmapData, Boolean) -> Unit,
    isDisabled: (SelectableBeatmapData) -> Boolean = { false },
    config: SelectableBeatmapConfig = SelectableBeatmapConfig(),
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.spacedBy(config.verticalPadding)
    ) {
        itemsIndexed(beatmaps) { _, beatmap ->
            SelectableBeatmapItem(
                beatmap = beatmap,
                isSelected = isSelected(beatmap),
                isDisabled = isDisabled(beatmap),
                onSelectionChanged = { selected -> onSelectionChanged(beatmap, selected) },
                config = config
            )
        }
    }
}
