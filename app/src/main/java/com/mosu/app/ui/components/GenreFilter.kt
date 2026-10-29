package com.mosu.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.mosu.app.R

/**
 * Data class representing a genre option.
 */
data class GenreOption(
    val id: Int,
    val nameResId: Int,
    val shortNameResId: Int = nameResId
)

enum class GenreTriggerStyle {
    /** Rounded, filled pill used by Search's recent row. */
    Pill,

    /** Square search control used immediately beside the portrait Library search button. */
    LibrarySearch,

    /** Transparent circular control matching the score-mode filter icons. */
    ModeIcon,

    /** Transparent half of the landscape Library's fused genre/search control. */
    FusedHalf
}

/**
 * Genre menu used by Library and Search in both orientations.
 */
@Composable
fun GenreFilter(
    selectedGenreIds: Set<Int>,
    onGenreSelected: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    genres: List<GenreOption> = defaultGenres,
    triggerStyle: GenreTriggerStyle = GenreTriggerStyle.Pill,
    maxExpandedWidth: Dp? = null,
    expandedWindowInset: Dp = 0.dp,
    inactiveContainerColor: Color? = null,
    expandedContainerColor: Color? = null,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    val triggerSize = when (triggerStyle) {
        GenreTriggerStyle.Pill -> 40.dp
        GenreTriggerStyle.LibrarySearch -> 40.dp
        GenreTriggerStyle.ModeIcon -> 36.dp
        GenreTriggerStyle.FusedHalf -> 40.dp
    }
    val triggerShape = when (triggerStyle) {
        GenreTriggerStyle.Pill, GenreTriggerStyle.ModeIcon -> CircleShape
        GenreTriggerStyle.LibrarySearch -> RoundedCornerShape(12.dp)
        GenreTriggerStyle.FusedHalf -> RoundedCornerShape(
            topStart = 12.dp,
            bottomStart = 12.dp
        )
    }

    PillSlideOutMenu(
        items = genreMenuItems(genres),
        selectedValues = selectedGenreIds,
        onSelectionChanged = onGenreSelected,
        modifier = modifier,
        collapsedContent = {
            GenreTrigger(
                selectedCount = selectedGenreIds.size,
                style = triggerStyle,
                size = triggerSize,
                inactiveContainerColor = inactiveContainerColor
            )
        },
        showClearAction = true,
        triggerAlignment = Alignment.Center,
        triggerTouchSize = triggerSize,
        triggerShape = triggerShape,
        maxExpandedWidth = maxExpandedWidth,
        expandedWindowInset = expandedWindowInset,
        expandedContainerColor =
            expandedContainerColor ?: MaterialTheme.colorScheme.surfaceVariant,
        onExpandedChange = onExpandedChange
    )
}

@Composable
private fun genreMenuItems(genres: List<GenreOption>): List<PillMenuItem<Int>> {
    return genres.map { option ->
        val fullLabel = stringResource(option.nameResId)
        PillMenuItem(
            value = option.id,
            label = stringResource(option.shortNameResId),
            contentDescription = fullLabel
        )
    }
}

@Composable
private fun GenreTrigger(
    selectedCount: Int,
    style: GenreTriggerStyle,
    size: Dp,
    inactiveContainerColor: Color?
) {
    val description = stringResource(R.string.library_filter_genre)
    val selectionDescription = if (selectedCount > 0) {
        stringResource(R.string.genre_filter_selected_count, description, selectedCount)
    } else {
        description
    }
    val selected = selectedCount > 0
    val styleContainerColor = when (style) {
        GenreTriggerStyle.Pill -> if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
        GenreTriggerStyle.LibrarySearch -> MaterialTheme.colorScheme.surfaceVariant
        GenreTriggerStyle.ModeIcon -> if (selected) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
        GenreTriggerStyle.FusedHalf -> if (selected) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    }
    val containerColor = if (!selected && inactiveContainerColor != null) {
        inactiveContainerColor
    } else {
        styleContainerColor
    }
    val contentColor = when (style) {
        GenreTriggerStyle.Pill -> if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
        GenreTriggerStyle.LibrarySearch -> MaterialTheme.colorScheme.onSurface
        GenreTriggerStyle.ModeIcon, GenreTriggerStyle.FusedHalf -> if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    val shape: Shape = when (style) {
        GenreTriggerStyle.Pill, GenreTriggerStyle.ModeIcon -> CircleShape
        GenreTriggerStyle.LibrarySearch -> RoundedCornerShape(12.dp)
        GenreTriggerStyle.FusedHalf -> RoundedCornerShape(
            topStart = 12.dp,
            bottomStart = 12.dp
        )
    }
    Box(
        modifier = Modifier
            .semantics { contentDescription = selectionDescription }
            .size(size)
            .clip(shape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.FilterList,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(if (style == GenreTriggerStyle.ModeIcon) 22.dp else 19.dp)
        )
        if (selected) {
            Text(
                text = selectedCount.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = contentColor,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 6.dp)
            )
        }
    }
}

/**
 * Default genre options used in the app.
 */
private val defaultGenres = listOf(
    GenreOption(10, R.string.genre_electronic, R.string.genre_electronic_short),
    GenreOption(3, R.string.genre_anime, R.string.genre_anime_short),
    GenreOption(4, R.string.genre_rock, R.string.genre_rock_short),
    GenreOption(5, R.string.genre_pop, R.string.genre_pop_short),
    GenreOption(2, R.string.genre_game, R.string.genre_game_short),
    GenreOption(9, R.string.genre_hiphop, R.string.genre_hiphop_short),
    GenreOption(11, R.string.genre_metal, R.string.genre_metal_short),
    GenreOption(12, R.string.genre_classical, R.string.genre_classical_short),
    GenreOption(13, R.string.genre_folk, R.string.genre_folk_short),
    GenreOption(14, R.string.genre_jazz, R.string.genre_jazz_short),
    GenreOption(7, R.string.genre_novelty, R.string.genre_novelty_short),
    GenreOption(6, R.string.genre_other, R.string.genre_other_short)
)
