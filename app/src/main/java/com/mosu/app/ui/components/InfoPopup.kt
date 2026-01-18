package com.mosu.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.constraintlayout.compose.ConstraintLayout
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

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
import com.mosu.app.domain.model.modeLabel
import com.mosu.app.domain.model.getStarRatingColor
import com.mosu.app.domain.model.getGradientColorsForRange
import com.mosu.app.domain.model.createGradientStops

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
                    val sortedSets = bySet.entries.toList().sortedByDescending { it.value.sumOf { b -> b.playCount } }
                    items(sortedSets) { (setId, list) ->
                        val sortedList = list.sortedByDescending { it.playCount }
                        val modes = sortedList.groupBy { it.mode }
                        val starMin = sortedList.minOfOrNull { it.difficultyRating } ?: 0f
                        val starMax = sortedList.maxOfOrNull { it.difficultyRating } ?: 0f
                        val url = sortedList.firstOrNull()?.url ?: "https://osu.ppy.sh/beatmapsets/$setId"
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
                                    .padding(horizontal = 4.dp)
                                    .height(50.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    // Replace the main Row with ConstraintLayout for precise positioning
                                    ConstraintLayout(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start=4.dp, end=2.dp, top = 3.dp)
                                            
                                    ) {
                                        // Create references for the three main sections
                                        val (leftContent, centerModeIcons, rightStarRating) = createRefs()

                                        // Create a guideline at 55% of the width
                                        val biasGuideline = createGuidelineFromStart(0.64f)

                                        // 1. LEFT CONTENT (Status + Playcount)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.constrainAs(leftContent) {
                                                start.linkTo(parent.start)
                                                top.linkTo(parent.top)
                                                bottom.linkTo(parent.bottom)
                                            }
                                        ) {
                                            // Status Icon
                                            val setStatus = if (sortedList.isNotEmpty()) sortedList.first().status else "unknown"
                                            if (setStatus.isNotEmpty() && setStatus != "unknown") {
                                                val statusIcon = when (setStatus.lowercase()) {
                                                    "ranked" -> R.drawable.status_ranked
                                                    "loved" -> R.drawable.status_loved
                                                    "qualified" -> R.drawable.status_qualified
                                                    "graveyard", "wip", "pending" -> R.drawable.status_graveyard_wip_pending
                                                    else -> null
                                                }

                                                if (statusIcon != null) {
                                                    Icon(
                                                        painter = painterResource(id = statusIcon),
                                                        contentDescription = setStatus,
                                                        modifier = Modifier.height(14.dp).width(14.dp), // Increased size for visibility
                                                        tint = Color.Unspecified
                                                    )
                                                } else {
                                                    Text(
                                                        text = setStatus.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.tertiary,
                                                        modifier = Modifier
                                                            .background(
                                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                                shape = RoundedCornerShape(4.dp)
                                                            )
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Icon(
                                                painter = painterResource(id = R.drawable.circle_play_solid_full),
                                                contentDescription = "Playcount",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (list.isNotEmpty()) {
                                                    val totalPlaycount = list.sumOf { it.playCount }
                                                    if (totalPlaycount >= 1_000_000) {
                                                        "%.1fM".format(totalPlaycount / 1_000_000f)
                                                    } else if (totalPlaycount >= 1_000) {
                                                        "%.1fk".format(totalPlaycount / 1_000f)
                                                    } else {
                                                        totalPlaycount.toString()
                                                    }
                                                } else "0",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }

                                        // 2. CENTER BIASED CONTENT (Mode Icons)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.constrainAs(centerModeIcons) {
                                                // Anchor the right end of this row to the guideline at 55%
                                                end.linkTo(biasGuideline)
                                                top.linkTo(parent.top)
                                                bottom.linkTo(parent.bottom)
                                            }
                                        ) {
                                            modes.keys.forEach { mode ->
                                                val iconRes = when (mode) {
                                                    "osu" -> R.drawable.std_icon
                                                    "taiko" -> R.drawable.taiko_icon
                                                    "mania" -> R.drawable.mania_icon
                                                    "fruits" -> R.drawable.cth_icon
                                                    else -> null
                                                }

                                                iconRes?.let {
                                                    Icon(
                                                        painter = painterResource(id = it),
                                                        contentDescription = modeLabel(mode),
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }
                                            }
                                        }

                                        // 3. RIGHT CONTENT (Star Rating)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .constrainAs(rightStarRating) {
                                                    end.linkTo(parent.end)
                                                    top.linkTo(parent.top)
                                                    bottom.linkTo(parent.bottom)
                                                }
                                                // ... (Keep your existing background logic for star rating) ...
                                                .then(
                                                    if (sortedList.size == 1) {
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
                                                                brush = Brush.horizontalGradient(*colorStops),
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
                                                .padding(start=5.dp,end=2.dp,top=1.dp,bottom=1.dp)
                                        ) {
                                            if (sortedList.size == 1) {
                                                // Single difficulty: background matches difficulty
                                                Text(
                                                    text = "%.1f".format(starMin),
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold, // Force Normal weight to ensure Monospace applies
                                                        fontFeatureSettings = "tnum"    // Force tabular (fixed-width) numbers
                                                    ),
                                                    color = if (starMin>3.25f){
                                                        Color.White
                                                    }else{
                                                        Color.Gray
                                                    },
//                                                    modifier = Modifier.padding(end = 1.dp)
                                                )
                                                Icon(
                                                    imageVector = Icons.Filled.Star,
                                                    contentDescription = "Star rating",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (starMin>3.25f){
                                                        Color.White
                                                    }else{
                                                        Color.Gray
                                                    },
                                                )
                                            } else {
                                                // Range: background matches max difficulty
                                                Text(
                                                    text = "%.1f-".format(starMin),
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold, // Force Normal weight to ensure Monospace applies
                                                        fontFeatureSettings = "tnum"    // Force tabular (fixed-width) numbers
                                                    ),
                                                    color = if (starMin>3.25f){
                                                        Color.White
                                                    }else{
                                                        Color.Gray
                                                    }
                                                )
                                                Text(
                                                    text = "%.1f".format(starMax),
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold, // Force Normal weight to ensure Monospace applies
                                                        fontFeatureSettings = "tnum"    // Force tabular (fixed-width) numbers
                                                    ),
                                                    color = if (starMax>3.25f){
                                                        Color.White
                                                    }else{
                                                        Color.Gray
                                                    },
//                                                    modifier = Modifier.padding(end = 1.dp)
                                                )
                                                Icon(
                                                    imageVector = Icons.Filled.Star,
                                                    contentDescription = "Star rating",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (starMax>3.25f){
                                                        Color.White
                                                    }else{
                                                        Color.Gray
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    
                                    Row(
                                        modifier = Modifier.padding(start=4.dp, end=4.dp, top=2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ){
                                        Text(
                                            text = stringResource(id = R.string.search_author_prefix, author),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )

                                        Spacer(modifier = Modifier.weight(1f)) 

                                        Text(
                                            text = stringResource(id = R.string.info_diff_count, sortedList.size),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )                                        
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
                    config.onPlayClick != null && !downloaded -> {
                        TextButton(
                            onClick = {
                                config.onPlayClick.invoke(target)
                                onDismiss()
                            }
                        ) {
                            Text(stringResource(id = R.string.search_preview))
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
