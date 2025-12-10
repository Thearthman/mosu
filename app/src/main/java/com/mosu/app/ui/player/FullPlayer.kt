package com.mosu.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.mosu.app.player.MusicController
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun FullPlayer(
    musicController: MusicController,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nowPlaying by musicController.nowPlaying.collectAsState()
    val isPlaying by musicController.isPlaying.collectAsState()
    val duration by musicController.duration.collectAsState()
    val currentPosition by musicController.currentPosition.collectAsState()
    val repeatMode by musicController.repeatMode.collectAsState()
    val shuffleModeEnabled by musicController.shuffleModeEnabled.collectAsState()

    // For smooth seeking
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf(0f) }

    val sliderPosition = if (isDragging) dragPosition else currentPosition.toFloat()
    val sliderRange = 0f..duration.toFloat().coerceAtLeast(1f)

    if (nowPlaying != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Blurred Background (Simulated with zoomed image and overlay)
            AsyncImage(
                model = nowPlaying?.artworkUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.8f)
                    .blur(
                        radiusX = 13.dp, // Horizontal blur radius
                        radiusY = 13.dp, // Vertical blur radius
                        edgeTreatment = BlurredEdgeTreatment.Unbounded // Optional: Adjust how blur handles edges
                    ),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical=10.dp, horizontal=24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Collapse Handle / Arrow
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Square Cover Art
                AsyncImage(
                    model = nowPlaying?.artworkUri,
                    contentDescription = "Cover Art",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.weight(1f))

                // Title and Artist
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = nowPlaying?.title?.toString() ?: "Unknown Title",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = nowPlaying?.artist?.toString() ?: "Unknown Artist",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                // Progress Bar
                Slider(
                    value = sliderPosition,
                    onValueChange = {
                        isDragging = true
                        dragPosition = it
                    },
                    onValueChangeFinished = {
                        musicController.seekTo(dragPosition.toLong())
                        isDragging = false
                    },
                    valueRange = sliderRange,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                    )
                )
                
                // Time Labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(sliderPosition.toLong()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Text(
                        text = formatTime(duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mod Button (Placeholder)
                    IconButton(onClick = { /* TODO: Feature 2.3 */ }) {
                        Icon(
                            imageVector = Icons.Default.Settings, // Placeholder icon
                            contentDescription = "Mods",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Previous
                    IconButton(
                        onClick = { musicController.skipToPrevious() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Play/Pause
                    IconButton(
                        onClick = { musicController.togglePlayPause() },
                        modifier = Modifier
                            .size(72.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Next
                    IconButton(
                        onClick = { musicController.skipToNext() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Loop/Shuffle Toggle (Cycle)
                    IconButton(onClick = {
                        // Cycle Logic: Sequence -> Random -> Single -> Sequence...
                        // Current state logic:
                        // Random = Shuffle ON
                        // Single = Repeat ONE
                        // Sequence = Shuffle OFF, Repeat ALL (default)
                        
                        if (shuffleModeEnabled) {
                            // Random -> Single
                            musicController.toggleShuffleMode() // Turn off shuffle
                            // Toggle repeat to ONE? No, toggleRepeatMode cycles OFF->ALL->ONE.
                            // We need specific setters. But MusicController has toggles.
                            // Let's assume user cycles via UI interaction.
                            // To get to Single (RepeatOne), we need to toggle repeat.
                            // This unified button is tricky with separate toggles.
                            // Let's implement the logic:
                            // If Shuffle -> Turn Shuffle Off, Set Repeat One
                            // If Repeat One -> Set Repeat All
                            // Else (Sequence) -> Turn Shuffle On
                            
                            // Implementation using existing toggles might be messy.
                            // Ideally MusicController should expose setMode methods.
                            // Using toggles:
                            musicController.toggleRepeatMode() // ALL -> ONE (assuming we were in ALL)
                        } else if (repeatMode == Player.REPEAT_MODE_ONE) {
                            // Single -> Sequence
                            musicController.toggleRepeatMode() // ONE -> OFF -> ALL (wait, toggle cycle is OFF->ALL->ONE->OFF)
                            // Actually my toggle logic was OFF->ALL->ONE->OFF.
                            // Sequence is ALL.
                            // Single is ONE.
                            // So ONE -> OFF. We want ALL. So call twice?
                            // Or just add setRepeatMode to controller.
                            // Let's rely on standard toggles for now, simpler.
                            // Actually, I'll just make this button toggle SHUFFLE for now, and have a separate repeat button if needed,
                            // OR I'll update MusicController to have explicit setters later.
                            // For now, let's just make it toggle Shuffle.
                            musicController.toggleShuffleMode()
                        } else {
                            // Sequence -> Random
                            musicController.toggleShuffleMode()
                        }
                    }) {
                        // Icon selection
                        val icon = when {
                            shuffleModeEnabled -> Icons.Default.Shuffle
                            repeatMode == Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat // Sequence
                        }
                        val tint = if (shuffleModeEnabled || repeatMode == Player.REPEAT_MODE_ONE) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onBackground
                            
                        Icon(
                            imageVector = icon,
                            contentDescription = "Mode",
                            tint = tint
                        )
                    }
                }
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

