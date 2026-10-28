package com.mosu.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mosu.app.R

/**
 * Data class representing a genre option.
 */
data class GenreOption(
    val id: Int,
    val nameResId: Int,
    val shortNameResId: Int = nameResId
)

/**
 * Horizontal genre menu used in portrait layouts.
 */
@Composable
fun GenreFilter(
    selectedGenreIds: Set<Int>,
    onGenreSelected: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    genres: List<GenreOption> = defaultGenres
) {
    PillSlideOutMenu(
        items = genreMenuItems(genres),
        selectedValues = selectedGenreIds,
        onSelectionChanged = onGenreSelected,
        modifier = modifier,
        collapsedContent = {
            GenreTrigger(selectedCount = selectedGenreIds.size, compact = false)
        },
        direction = PillMenuDirection.Horizontal,
        showClearAction = true,
        triggerAlignment = Alignment.CenterStart
    )
}

/**
 * Thin vertical genre menu used beside the landscape navigation dock.
 */
@Composable
fun GenreRail(
    selectedGenreIds: Set<Int>,
    onGenreSelected: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    genres: List<GenreOption> = defaultGenres
) {
    PillSlideOutMenu(
        items = genreMenuItems(genres),
        selectedValues = selectedGenreIds,
        onSelectionChanged = onGenreSelected,
        modifier = modifier.width(48.dp).fillMaxHeight(),
        collapsedContent = {
            GenreTrigger(selectedCount = selectedGenreIds.size, compact = true)
        },
        direction = PillMenuDirection.Vertical,
        expandedMenuWidth = 56.dp,
        showClearAction = true,
        triggerAlignment = Alignment.Center
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
    compact: Boolean
) {
    val description = stringResource(R.string.library_filter_genre)
    val selectionDescription = if (selectedCount > 0) {
        stringResource(R.string.genre_filter_selected_count, description, selectedCount)
    } else {
        description
    }
    val shape = RoundedCornerShape(percent = 50)
    val containerColor = if (selectedCount > 0) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (selectedCount > 0) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(
        modifier = Modifier
            .semantics { contentDescription = selectionDescription }
            .then(
                if (compact) {
                    Modifier.width(40.dp).height(64.dp)
                } else {
                    Modifier.widthIn(min = 44.dp).height(40.dp)
                }
            )
            .clip(shape)
            .background(containerColor)
            .padding(horizontal = if (compact) 4.dp else 12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (compact) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                if (selectedCount > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = selectedCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor
                    )
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                if (selectedCount > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = selectedCount.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor
                    )
                }
            }
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
