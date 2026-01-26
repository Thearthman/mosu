package com.mosu.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
    val finalBackgroundModifier = if (backgroundBrush != null) {
        Modifier.background(backgroundBrush)
    } else {
        Modifier.background(backgroundColor)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(finalBackgroundModifier)
            .combinedClickable(
                onClick = { actions.onClick(set) },
                onLongClick = { actions.onLongClick(set) }
            )
            .padding(vertical = 8.dp)
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
                .size(50.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )

        // Main Content
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Subtitle Metadata (Last Played)
            if (set.lastPlayed != null) {
                Text(
                    text = set.lastPlayed,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Download Progress Bar
            if (set.downloadProgress != null) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    LinearProgressIndicator(
                        progress = { set.downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(id = R.string.search_playcount_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Secondary Action (Download / Status)
        // Only show if we are not in ranking/playcount mode (which implies generic list) OR if explicitly needed.
        // But search screen shows download button even with ranking/playcount?
        // Let's check SearchScreen logic. 
        // Logic: "Download Button" is always shown in SearchScreen row.
        // We'll show it if onSecondaryAction is provided.
        if (actions.onSecondaryAction != null) {
            IconButton(
                onClick = { actions.onSecondaryAction.invoke(set) },
                enabled = !set.isDownloaded && set.downloadProgress == null
            ) {
                Icon(
                    imageVector = if (set.isDownloaded) Icons.Default.CheckCircle else Icons.Default.Add,
                    contentDescription = if (set.isDownloaded) 
                        stringResource(id = R.string.search_cd_downloaded) 
                    else 
                        stringResource(id = R.string.search_cd_download),
                    tint = if (set.isDownloaded) {
                        MaterialTheme.colorScheme.primary
                    } else if (set.downloadProgress != null) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}
