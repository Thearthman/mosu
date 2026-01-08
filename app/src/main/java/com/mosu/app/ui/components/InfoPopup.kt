package com.mosu.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mosu.app.R
import com.mosu.app.data.api.model.BeatmapsetCompact
import com.mosu.app.data.api.model.BeatmapDetail

/**
 * Data class for info popup configuration
 */
data class InfoPopupConfig(
    val infoCoverEnabled: Boolean = false,
    val showConfirmButton: Boolean = true,
    val onDownloadClick: ((BeatmapsetCompact) -> Unit)? = null,
    val onRestoreClick: ((BeatmapsetCompact) -> Unit)? = null,
    val onPlayClick: ((BeatmapsetCompact) -> Unit)? = null
)

/**
 * Helper function to get star rating color based on difficulty
 */
fun getStarRatingColor(stars: Float): Color {
    return when {
        stars <= 2f -> Color(0xFF4CAF50) // Green
        stars <= 2.7f -> Color(0xFF8BC34A) // Light Green
        stars <= 4f -> Color(0xFFFFC107) // Yellow
        stars <= 5.3f -> Color(0xFFFF9800) // Orange
        stars <= 6.5f -> Color(0xFFFF5722) // Deep Orange
        else -> Color(0xFFE91E63) // Pink
    }
}

/**
 * Helper function to get gradient colors for a star rating range
 */
fun getGradientColorsForRange(minStars: Float, maxStars: Float): List<Color> {
    val colors = mutableListOf<Color>()

    // Define difficulty ranges and their colors
    val ranges = listOf(
        0f to Color(0xFF4CAF50), // Green
        2f to Color(0xFF8BC34A), // Light Green
        2.7f to Color(0xFFFFC107), // Yellow
        4f to Color(0xFFFF9800), // Orange
        5.3f to Color(0xFFFF5722), // Deep Orange
        6.5f to Color(0xFFE91E63) // Pink
    )

    // Find colors for the range
    val minColor = ranges.lastOrNull { minStars >= it.first }?.second ?: ranges.first().second
    val maxColor = ranges.lastOrNull { maxStars >= it.first }?.second ?: ranges.last().second

    colors.add(minColor)
    if (minColor != maxColor) {
        colors.add(maxColor)
    }

    return colors
}

/**
 * Helper function to create gradient stops
 */
fun createGradientStops(colors: List<Color>): Array<Pair<Float, Color>> {
    if (colors.size <= 1) return arrayOf(0f to (colors.firstOrNull() ?: Color.Gray))

    val stops = mutableListOf<Pair<Float, Color>>()
    val step = 1f / (colors.size - 1)

    colors.forEachIndexed { index, color ->
        stops.add((index * step) to color)
    }

    return stops.toTypedArray()
}

/**
 * Helper function to get mode label
 */
fun modeLabel(mode: String): String {
    return when (mode) {
        "osu" -> "Standard"
        "taiko" -> "Taiko"
        "mania" -> "Mania"
        "fruits" -> "Catch the Beat"
        else -> mode
    }
}

/**
 * Reusable Info Popup component that shows beatmap details
 */
@Composable
fun InfoPopup(
    visible: Boolean,
    onDismiss: () -> Unit,
    target: BeatmapsetCompact?,
    beatmaps: List<BeatmapDetail>,
    loading: Boolean,
    error: String?,
    setCreators: Map<Long, String>,
    downloaded: Boolean,
    config: InfoPopupConfig = InfoPopupConfig()
) {
    if (!visible || target == null) return

    val uriHandler = LocalUriHandler.current

    val grouped = beatmaps.groupBy { it.mode }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = target.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2
                )
                Text(
                    text = target.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                state = androidx.compose.foundation.lazy.rememberLazyListState()
            ) {
                if (config.infoCoverEnabled) {
                    item {
                        AsyncImage(
                            model = target.covers.listUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                if (loading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                } else if (error != null) {
                    item {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else if (grouped.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(id = R.string.info_no_details),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else {
                    val bySet = beatmaps.groupBy { it.beatmapsetId }
                    items(bySet.entries.toList()) { (setId, list) ->
                        val modes = list.groupBy { it.mode }
                        val starMin = list.minOfOrNull { it.difficultyRating } ?: 0f
                        val starMax = list.maxOfOrNull { it.difficultyRating } ?: 0f
                        val url = list.firstOrNull()?.url ?: "https://osu.ppy.sh/beatmapsets/$setId"
                        val author = setCreators[setId] ?: target.creator
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { uriHandler.openUri(url) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.search_author_prefix, author),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        // Add gamemode icons at the rightmost position
                                        modes.keys.forEach { mode ->
                                            val iconRes = when (mode) {
                                                "osu" -> R.drawable.std_icon
                                                "taiko" -> R.drawable.taiko_icon
                                                "mania" -> R.drawable.mania_icon
                                                "fruits" -> R.drawable.cth_icon
                                                else -> null
                                            }
                                            iconRes?.let {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    painter = painterResource(id = it),
                                                    contentDescription = modeLabel(mode),
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.padding(start=4.dp, end=2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ){
                                        Text(
                                            text = stringResource(id = R.string.info_diff_count, list.size),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(modifier=Modifier.weight(1f))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .then(
                                                    if (list.size == 1) {
                                                        // Single difficulty: solid color background
                                                        Modifier.background(
                                                            color = getStarRatingColor(starMin),
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                    } else {
                                                        // Multiple difficulties: gradient background if range spans multiple colors, solid otherwise
                                                        val gradientColors = getGradientColorsForRange(starMin, starMax)
                                                        if (gradientColors.size >= 2) {
                                                            val colorStops = createGradientStops(gradientColors)
                                                            Modifier.background(
                                                                brush = Brush.horizontalGradient(
                                                                    colorStops = colorStops
                                                                ),
                                                                shape = RoundedCornerShape(8.dp)
                                                            )
                                                        } else {
                                                            // Fall back to solid color if range is too narrow
                                                            Modifier.background(
                                                                color = gradientColors.firstOrNull() ?: getStarRatingColor(starMax),
                                                                shape = RoundedCornerShape(8.dp)
                                                            )
                                                        }
                                                    }
                                                )
                                                .padding(start=9.dp,end=2.dp,top=1.dp,bottom=1.dp)
                                        ) {
                                            if (list.size == 1) {
                                                // Single difficulty: background matches difficulty
                                                Text(
                                                    text = "%.1f".format(starMin),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(end=4.dp)
                                                )
                                                Icon(
                                                    imageVector = Icons.Filled.Star,
                                                    contentDescription = "Star rating",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = Color.White
                                                )
                                            } else {
                                                // Range: background matches max difficulty
                                                Text(
                                                    text = "%.1f - %.1f".format(starMin, starMax),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(end=4.dp)
                                                )
                                                Icon(
                                                    imageVector = Icons.Filled.Star,
                                                    contentDescription = "Star rating",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
                when {
                    !config.showConfirmButton-> {
                    }
                    config.onPlayClick != null && downloaded -> {
                        TextButton(
                            onClick = {
                                config.onPlayClick.invoke(target)
                                onDismiss()
                            }
                        ) {
                            Text(stringResource(id = R.string.search_play))
                        }
                    }
                    downloaded -> {
                        TextButton(
                            onClick = {
                                config.onRestoreClick?.invoke(target)
                                onDismiss()
                            }
                        ) {
                            Text(stringResource(id = R.string.profile_restore_button))
                        }
                    }
                    else -> {
                        TextButton(
                            onClick = {
                                config.onDownloadClick?.invoke(target)
                                onDismiss()
                            }
                        ) {
                            Text(stringResource(id = R.string.search_cd_download))
                        }
                    }
                }
            }
        // dismissButton = {
        //     TextButton(onClick = onDismiss) {
        //         Text(stringResource(id = R.string.playlist_cancel_button))
        //     }
        // }
    )
}
