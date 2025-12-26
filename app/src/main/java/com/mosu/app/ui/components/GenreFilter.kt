package com.mosu.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mosu.app.R

/**
 * Data class representing a genre option
 */
data class GenreOption(
    val id: Int,
    val nameResId: Int
)

/**
 * A reusable genre filter component with selectable buttons
 */
@Composable
fun GenreFilter(
    selectedGenreId: Int?,
    onGenreSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    genres: List<GenreOption> = defaultGenres
) {
    LazyRow(modifier = modifier) {
        items(genres) { (id, nameResId) ->
            Button(
                onClick = {
                    onGenreSelected(if (selectedGenreId == id) null else id)
                },
                modifier = Modifier.padding(end = 8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedGenreId == id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (selectedGenreId == id) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text(stringResource(id = nameResId), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * Default genre options used in the app
 */
private val defaultGenres = listOf(
    GenreOption(10, R.string.genre_electronic),
    GenreOption(3, R.string.genre_anime),
    GenreOption(4, R.string.genre_rock),
    GenreOption(5, R.string.genre_pop),
    GenreOption(2, R.string.genre_game),
    GenreOption(9, R.string.genre_hiphop),
    GenreOption(11, R.string.genre_metal),
    GenreOption(12, R.string.genre_classical),
    GenreOption(13, R.string.genre_folk),
    GenreOption(14, R.string.genre_jazz),
    GenreOption(7, R.string.genre_novelty),
    GenreOption(6, R.string.genre_other)
)
