package com.mosu.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mosu.app.player.MusicController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayer(
    musicController: MusicController,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nowPlaying by musicController.nowPlaying.collectAsState()
    val isPlaying by musicController.isPlaying.collectAsState()
    val duration by musicController.duration.collectAsState()
    val currentPosition by musicController.currentPosition.collectAsState()

    // For smooth seeking
    var isDragging by remember { mutableStateOf(false) }
    val sliderRange = 0f..duration.toFloat().coerceAtLeast(1f)
    val sliderInteraction = remember { MutableInteractionSource() }
    var sliderValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(sliderInteraction) {
        sliderInteraction.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> isDragging = true
                is DragInteraction.Stop, is DragInteraction.Cancel -> isDragging = false
            }
        }
    }

    val throttledPosition by remember(currentPosition, isDragging) {
        derivedStateOf {
            if (isDragging) sliderValue.toFloat() else currentPosition.toFloat()
        }
    }

    LaunchedEffect(throttledPosition, sliderRange) {
        sliderValue = throttledPosition.coerceIn(sliderRange.start, sliderRange.endInclusive)
    }

    if (nowPlaying != null) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(64.dp)
                .clickable(onClick = onClick),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 8.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cover Art
                    AsyncImage(
                        model = nowPlaying?.artworkUri,
                        contentDescription = "Cover Art",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title & Artist
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = nowPlaying?.title?.toString() ?: "Unknown Title",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = nowPlaying?.artist?.toString() ?: "Unknown Artist",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    // Previous Button
                    IconButton(onClick = { musicController.skipToPrevious() }) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
                    }

                    // Play/Pause Button
                    IconButton(onClick = { musicController.togglePlayPause() }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play"
                        )
                    }

                    // Next Button
                    IconButton(onClick = { musicController.skipToNext() }) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                    }
                }
                
                // Progress Bar at the absolute bottom
                val sliderColors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    disabledThumbColor = Color.Transparent,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                )
                
                Slider(
                    value = sliderValue,
                    onValueChange = { newValue ->
                        isDragging = true
                        sliderValue = newValue.coerceIn(sliderRange.start, sliderRange.endInclusive)
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        musicController.seekTo(sliderValue.toLong())
                    },
                    valueRange = sliderRange,
                    interactionSource = sliderInteraction,
                    colors = sliderColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(10.dp) // Touch area
                        .offset(y = 4.dp), // Align visual track to bottom edge
                    thumb = {
                        Box(modifier = Modifier.size(0.dp))
                    },
                    track = { sliderState ->
                        val fraction = if (sliderRange.endInclusive > 0) {
                            sliderState.value / sliderRange.endInclusive
                        } else 0f

                        Track(
                            activeRange = 0f..fraction,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            trackHeight = 2.dp, // Thin visual track
                            horizontalExpansion = 0f
                        )
                    }
                )
            }
        }
    }
}
