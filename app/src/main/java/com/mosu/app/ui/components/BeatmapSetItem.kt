package com.mosu.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import java.io.File

/**
 * A reusable component for displaying a BeatmapSet item (flat row)
 * Handles covers (local/remote), metadata slots, and download progress.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BeatmapSetItem(
    set: BeatmapSetData,
    actions: BeatmapSetActions,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    backgroundBrush: Brush? = null,
    highlight: Boolean = false
) {
    val flickerColor = if (isSystemInDarkTheme()) Color(0xFF300063) else Color.LightGray
    val finalBackgroundModifier = if (backgroundBrush != null) {
        Modifier.background(backgroundBrush)
    } else {
        Modifier.background(if (highlight) flickerColor else backgroundColor)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp) // Unified height
            .then(finalBackgroundModifier)
            .combinedClickable(
                onClick = { actions.onClick(set) },
                onLongClick = { actions.onLongClick(set) }
            )
            .padding(horizontal = if (set.ranking != null) 0.dp else 16.dp)
            .padding(end = if (set.ranking != null) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading Metadata (Ranking)
        if (set.ranking != null) {
            Text(
                text = "#${set.ranking}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.Center
            )
        }

        // Cover Art
        AsyncImage(
            model = if (set.coverPath != null) File(set.coverPath) else set.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp) // Slightly smaller to fit 60dp
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )

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
                style = MaterialTheme.typography.bodySmall, // Smaller text for secondary info
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Subtitle Metadata (Last Played)
            if (set.lastPlayed != null) {
                Text(
                    text = set.lastPlayed,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Download Progress Bar
            if (set.downloadProgress != null) {
                LinearProgressIndicator(
                    progress = { set.downloadProgress / 100f },
                    modifier = Modifier.fillMaxWidth().height(5.dp).padding(top = 2.dp)
                )
            }
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
