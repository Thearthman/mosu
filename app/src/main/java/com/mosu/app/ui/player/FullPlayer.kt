package com.mosu.app.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderPositions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.mosu.app.player.MusicController
import com.mosu.app.player.PlaybackMod
import com.mosu.app.ui.components.Track
import java.util.Locale

// -- Responsive layout ---------------------------------------------------------
// The full player was designed against a 1216 x 2640 px panel (~2.874 density,
// i.e. ~423 x 918 dp). Instead of hard-coding dp sizes tied to that one panel, the
// vertical regions below are weighted so they scale with whatever height the device
// actually provides. All weights are relative to the cover art's weight of 1.
private const val COVER_WEIGHT = 1f          // cover art slot (grows with the screen)
private const val CONTROLS_WEIGHT = 0.19f    // controls row (≈90 dp on the reference panel)
private const val BOTTOM_GAP_WEIGHT = 0.11f  // bottom breathing room (≈55 dp on the reference panel)
// Buttons are sized as a fraction of the control row's height so they scale with it.
private const val SIDE_BUTTON_HEIGHT_FRACTION = 0.52f // ≈48 dp on the reference panel
private const val PLAY_BUTTON_HEIGHT_FRACTION = 0.78f // ≈72 dp on the reference panel
private const val PLAY_ICON_FRACTION = 0.66f          // ≈48 dp glyph inside the play button
// Keeps the (centered) content column phone-sized on wider displays instead of
// stretching edge to edge.
private val MaxContentWidth = 480.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayer(
    musicController: MusicController,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nowPlaying by musicController.nowPlaying.collectAsState()
    val isPlaying by musicController.isPlaying.collectAsState()
    val duration by musicController.duration.collectAsState()
    val repeatMode by musicController.repeatMode.collectAsState()
    val shuffleModeEnabled by musicController.shuffleModeEnabled.collectAsState()
    val playbackMod by musicController.playbackMod.collectAsState()

    var modMenuExpanded by remember { mutableStateOf(false) }

    if (nowPlaying != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
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
                    .fillMaxHeight()
                    .widthIn(max = MaxContentWidth)
                    .padding(vertical = 10.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Spacer(modifier = Modifier.height(35.dp))

                // Collapse Handle / Arrow
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                // Square Cover Art. It lives inside a weighted, full-height slot so it
                // scales with the leftover vertical space; the square itself is centered
                // in that slot (a square can't fill a slot taller than it is wide).
                Box(
                    modifier = Modifier
                        .weight(COVER_WEIGHT)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = nowPlaying?.artworkUri,
                        contentDescription = "Cover Art",
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(50.dp))

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

                Spacer(modifier = Modifier.height(10.dp))

                // Isolated Progress Bar and Time Labels
                PlayerProgressBar(
                    musicController = musicController,
                    duration = duration
                )

                Spacer(modifier = Modifier.height(30.dp))

                // Controls Row — weighted so the row's height scales with the screen.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(CONTROLS_WEIGHT),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left cluster: Mod + Previous
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        val modTint = if (playbackMod == PlaybackMod.NIGHT_CORE) {
                            MaterialTheme.colorScheme.primary
                        } else if (playbackMod == PlaybackMod.DOUBLE_TIME) {
                            Color(0xFFFFD059)
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        }
                        Box {
                            Column(
                                modifier = Modifier
                                    .widthIn(min = 46.dp)
                                    .clickable { modMenuExpanded = true }
                            ) {
                                Text(
                                    text = "Mod",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = modLabel(playbackMod),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = modTint
                                )
                            }
                            DropdownMenu(
                                expanded = modMenuExpanded,
                                onDismissRequest = { modMenuExpanded = false },
                            ) {
                                PlaybackMod.entries.forEach { mod ->
                                    DropdownMenuItem(
                                        modifier = Modifier.alpha(0.9f).fillMaxSize(),
                                        text = { Text(
                                            text = modMenuLabel(mod),
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = when (mod) {
                                                PlaybackMod.NIGHT_CORE -> {
                                                    MaterialTheme.colorScheme.primary
                                                }
                                                PlaybackMod.DOUBLE_TIME -> {
                                                    Color(0xFFFFD059)
                                                }
                                                else -> {
                                                    MaterialTheme.colorScheme.onBackground
                                                }
                                            },
                                        ) },
                                        onClick = {
                                            musicController.setPlaybackMod(mod)
                                            modMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = { musicController.skipToPrevious() },
                            modifier = Modifier
                                .fillMaxHeight(SIDE_BUTTON_HEIGHT_FRACTION)
                                .aspectRatio(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Play/Pause centered
                    IconButton(
                        onClick = { musicController.togglePlayPause() },
                        modifier = Modifier
                            .fillMaxHeight(PLAY_BUTTON_HEIGHT_FRACTION)
                            .aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.fillMaxSize(PLAY_ICON_FRACTION)
                        )
                    }

                    // Right cluster: Next + Mode
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = { musicController.skipToNext() },
                            modifier = Modifier
                                .fillMaxHeight(SIDE_BUTTON_HEIGHT_FRACTION)
                                .aspectRatio(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        IconButton(
                            onClick = {
                                when {
                                    shuffleModeEnabled -> {
                                        // Random -> Single
                                        musicController.setShuffleMode(false)
                                        musicController.setRepeatMode(Player.REPEAT_MODE_ONE)
                                    }
                                    repeatMode == Player.REPEAT_MODE_ONE -> {
                                        // Single -> Sequence (repeat all, shuffle off)
                                        musicController.setShuffleMode(false)
                                        musicController.setRepeatMode(Player.REPEAT_MODE_ALL)
                                    }
                                    else -> {
                                        // Sequence -> Random (shuffle on, repeat all)
                                        musicController.setRepeatMode(Player.REPEAT_MODE_ALL)
                                        musicController.setShuffleMode(true)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxHeight(SIDE_BUTTON_HEIGHT_FRACTION)
                                .aspectRatio(1f)
                        ) {
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
                }
                Spacer(Modifier.weight(BOTTOM_GAP_WEIGHT))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerProgressBar(
    musicController: MusicController,
    duration: Long
) {
    val currentPosition by musicController.currentPosition.collectAsState()
    var isDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    val sliderRange = 0f..duration.toFloat().coerceAtLeast(1f)
    val sliderInteraction = remember { MutableInteractionSource() }

    // Sync sliderValue with currentPosition when not dragging
    LaunchedEffect(currentPosition, isDragging) {
        if (!isDragging) {
            sliderValue = currentPosition.toFloat().coerceIn(sliderRange)
        }
    }

    LaunchedEffect(sliderInteraction) {
        sliderInteraction.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> isDragging = true
                is DragInteraction.Stop, is DragInteraction.Cancel -> isDragging = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        val sliderColors = SliderDefaults.colors(
            thumbColor = Color.Transparent,
            disabledThumbColor = Color.Transparent,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
        )
        val activeTrackColor = MaterialTheme.colorScheme.primary
        val inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)

        Slider(
            modifier = Modifier.height(36.dp),
            value = sliderValue,
            onValueChange = { newValue ->
                isDragging = true
                sliderValue = newValue.coerceIn(sliderRange)
            },
            onValueChangeFinished = {
                isDragging = false
                musicController.seekTo(sliderValue.toLong())
            },
            valueRange = sliderRange,
            interactionSource = sliderInteraction,
            colors = sliderColors,
            thumb = {
                Box(modifier = Modifier.size(28.dp))
            },
            track = { sliderState ->
                val fraction = if (sliderRange.endInclusive > 0) {
                    sliderState.value / sliderRange.endInclusive
                } else 0f

                Track(
                    activeRange = 0f..fraction,
                    activeTrackColor = activeTrackColor,
                    inactiveTrackColor = inactiveTrackColor,
                    activeTickColor = activeTrackColor.copy(alpha = 0.6f),
                    inactiveTickColor = inactiveTrackColor.copy(alpha = 0.6f),
                    tickFractions = emptyList(),
                    trackHeight = 10.dp,
                    horizontalExpansion = 50f
                )
            }
        )

        // Time Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(sliderValue.toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

private fun modLabel(mod: PlaybackMod): String = when (mod) {
    PlaybackMod.NONE -> "NM"
    PlaybackMod.DOUBLE_TIME -> "DT"
    PlaybackMod.NIGHT_CORE -> "NC"
}

private fun modMenuLabel(mod: PlaybackMod): String = when (mod) {
    PlaybackMod.NONE -> "NO MOD"
    PlaybackMod.DOUBLE_TIME -> "DOUBLE TIME"
    PlaybackMod.NIGHT_CORE -> "NIGHT CORE"
}
