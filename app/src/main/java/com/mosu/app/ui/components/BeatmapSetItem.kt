package com.mosu.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mosu.app.R
import com.mosu.app.domain.model.modeLabel
import com.mosu.app.utils.MediaPathUtils

/**
 * A reusable component for displaying a BeatmapSet item (flat row)
 * Handles covers (local/remote), metadata slots, and download progress.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BeatmapSetItem(
    set: BeatmapSetData,
    actions: BeatmapSetActions = BeatmapSetActions(),
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    backgroundBrush: Brush? = null,
    highlight: Boolean = false,
    contentPadding: PaddingValues? = null
) {
    val flickerColor = if (isSystemInDarkTheme()) Color(0xFF300063) else Color.LightGray
    val finalBackgroundModifier = if (backgroundBrush != null) {
        Modifier.background(backgroundBrush)
    } else {
        Modifier.background(if (highlight) flickerColor else backgroundColor)
    }
    val score = set.score
    val resolvedContentPadding = contentPadding ?: if (set.ranking != null) {
        PaddingValues(start = 0.dp, end = 8.dp)
    } else {
        PaddingValues(horizontal = 16.dp)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (score != null) 48.dp else 60.dp)
            .then(finalBackgroundModifier)
            .combinedClickable(
                onClick = { actions.onClick(set) },
                onLongClick = { actions.onLongClick(set) }
            )
            .padding(resolvedContentPadding)
            .padding(vertical = if (score != null) 5.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading metadata (Top Play ranking)
        if (set.ranking != null) {
            Text(
                text = "#${set.ranking}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.Center
            )
        }

        if (score == null) {
            BeatmapSetCover(set)

            // Main Content
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Text(
                    text = set.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = set.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (set.lastPlayed != null) {
                    Text(
                        text = set.lastPlayed,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                BeatmapSetDownloadProgress(set)
            }

            // Trailing Metadata (Play Count)
            if (set.playCount != null) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = "${set.playCount}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(id = R.string.search_playcount_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        } else {
            BeatmapSetCover(set)
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                if (score.grade != null || score.mode != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        score.grade?.let { GradeBadge(it) }
                        if (score.grade != null && score.mode != null) {
                            Spacer(Modifier.width(6.dp))
                        }
                        score.mode?.let { mode ->
                            Text(
                                text = modeLabel(mode),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    text = set.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(set.artist, score.difficultyName).joinToString(" - "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (set.lastPlayed != null) {
                    Text(
                        text = set.lastPlayed,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                BeatmapSetDownloadProgress(set)
            }

            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                StarRatingBadge(score.starRating)
                Text(
                    text = score.pp?.let { stringResource(id = R.string.score_pp_value, it) }
                        ?: stringResource(id = R.string.score_pp_none),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = stringResource(
                        id = R.string.score_length_prefix,
                        formatLength(score.lengthSeconds)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (set.playCount != null && score != null) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = "${set.playCount}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(id = R.string.search_playcount_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Secondary Action (Download / Status)
        if (actions.onSecondaryAction != null) {
            IconButton(
                onClick = { actions.onSecondaryAction.invoke(set) },
                enabled = !set.isDownloaded && set.downloadProgress == null,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (set.isDownloaded) Icons.Default.CheckCircle else Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (set.isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun BeatmapSetCover(set: BeatmapSetData) {
    AsyncImage(
        model = MediaPathUtils.asImageModel(set.coverPath, set.coverUrl),
        contentDescription = set.title,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(6.dp)),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun BeatmapSetDownloadProgress(set: BeatmapSetData) {
    if (set.downloadProgress == null) return
    if (set.downloadProgress >= 0) {
        LinearProgressIndicator(
            progress = { set.downloadProgress / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .padding(top = 2.dp)
        )
    } else {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .padding(top = 2.dp)
        )
    }
}
