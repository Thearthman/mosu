package com.mosu.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.material3.Divider

/**
 * Data class representing a song item that can be displayed in the UI
 */
data class SongItemData(
    val title: String,
    val artist: String,
    val coverPath: String,
    val difficultyName: String? = null,
    val id: Long
)

/**
 * A reusable component for displaying a song item with cover art, title, and artist
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    song: SongItemData,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    backgroundColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    showDifficulty: Boolean = false,
    coverStartPadding: androidx.compose.ui.unit.Dp = 0.dp,
    textEndPadding: androidx.compose.ui.unit.Dp = 0.dp,
    coverSize: androidx.compose.ui.unit.Dp = 50.dp,
    titleTextStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    subtitleTextStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp) // Fixed height to ensure consistent item height
            .background(backgroundColor)
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = onLongClick
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover Art
        AsyncImage(
            model = File(song.coverPath),
            contentDescription = null,
            modifier = Modifier
                .padding(start = coverStartPadding)
                .size(coverSize)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )

        // Title & Artist/Difficulty
        Column(
            modifier = Modifier
                .padding(start = 16.dp, end = textEndPadding)
                .weight(1f)
        ) {
            Text(
                text = song.title,
                style = titleTextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showDifficulty && song.difficultyName != null) {
                Text(
                    text = song.difficultyName,
                    style = subtitleTextStyle,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = song.artist,
                    style = subtitleTextStyle,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
