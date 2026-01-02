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
import java.util.Locale

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
    val currentPosition by musicController.currentPosition.collectAsState()
    val repeatMode by musicController.repeatMode.collectAsState()
    val shuffleModeEnabled by musicController.shuffleModeEnabled.collectAsState()
    val playbackMod by musicController.playbackMod.collectAsState()

    // For smooth seeking
    var isDragging by remember { mutableStateOf(false) }
    var modMenuExpanded by remember { mutableStateOf(false) }

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

    // Throttle position updates to reduce recompositions - only update when not dragging
    val throttledPosition by remember(currentPosition, isDragging) {
        derivedStateOf {
            if (isDragging) sliderValue.toFloat() else currentPosition.toFloat()
        }
    }

    LaunchedEffect(throttledPosition, sliderRange) {
        sliderValue = throttledPosition.coerceIn(sliderRange.start, sliderRange.endInclusive)
    }

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

                // Progress Bar
                val sliderColors = SliderDefaults.colors(
                    thumbColor = Color(0x00000000),
                    disabledThumbColor = Color(0x00000000),
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
                        sliderValue = newValue.coerceIn(sliderRange.start, sliderRange.endInclusive)
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        musicController.seekTo(sliderValue.toLong())
                    },
                    valueRange = sliderRange,
                    interactionSource = sliderInteraction,
                    colors = sliderColors,
                    thumb = {
                        SliderDefaults.Thumb(
                            interactionSource = sliderInteraction,
                            colors = sliderColors,
                            enabled = true,
                            thumbSize = DpSize(28.dp, 28.dp),
                            modifier = Modifier.alpha(0f)
                        )
                    },
                    track = { positions ->
                        Track(
                            sliderPositions = positions,
                            activeTrackColor = activeTrackColor,
                            inactiveTrackColor = inactiveTrackColor,
                            activeTickColor = activeTrackColor.copy(alpha = 0.6f),
                            inactiveTickColor = inactiveTrackColor.copy(alpha = 0.6f),
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

                Spacer(modifier = Modifier.height(30.dp))

                // Controls Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
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
                            modifier = Modifier.size(48.dp)
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

                    // Right cluster: Next + Mode
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
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
                        IconButton(onClick = {
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
                        }) {
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
                Spacer(modifier = Modifier.height(55.dp))
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

@Composable
fun Track(
    sliderPositions: SliderPositions,
    modifier: Modifier = Modifier,
    activeTrackColor: Color,
    inactiveTrackColor: Color,
    activeTickColor: Color = activeTrackColor,
    inactiveTickColor: Color = inactiveTrackColor,
    trackHeight: Dp = 15.dp,
    horizontalExpansion: Float = 0f
) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(trackHeight)
    ) {
        // Cache expensive calculations within Canvas context
        val tickSizePx = trackHeight.toPx() * 0.133f // Cache tick size relative to track height
        val isRtl = layoutDirection == LayoutDirection.Rtl
        val sliderLeft = Offset(0f - horizontalExpansion, center.y)
        val sliderRight = Offset(size.width + horizontalExpansion, center.y)
        val sliderStart = if (isRtl) sliderRight else sliderLeft
        val sliderEnd = if (isRtl) sliderLeft else sliderRight
        val trackStrokeWidth = size.height // Use canvas height directly

        // Draw inactive track
        drawLine(
            color = inactiveTrackColor,
            start = sliderStart,
            end = sliderEnd,
            strokeWidth = trackStrokeWidth,
            cap = StrokeCap.Round
        )

        // Draw active track
        val sliderValueEnd = Offset(
            sliderStart.x + (sliderEnd.x - sliderStart.x) * sliderPositions.activeRange.endInclusive,
            center.y
        )
        val sliderValueStart = Offset(
            sliderStart.x + (sliderEnd.x - sliderStart.x) * sliderPositions.activeRange.start,
            center.y
        )

        drawLine(
            color = activeTrackColor,
            start = sliderValueStart,
            end = sliderValueEnd,
            strokeWidth = trackStrokeWidth,
            cap = StrokeCap.Round
        )

        // Optimize tick rendering - only render if there are ticks
        if (sliderPositions.tickFractions.isNotEmpty()) {
            sliderPositions.tickFractions.groupBy { fraction ->
                fraction > sliderPositions.activeRange.endInclusive ||
                        fraction < sliderPositions.activeRange.start
            }.forEach { (outsideFraction, list) ->
                drawPoints(
                    points = list.map { Offset(lerp(sliderStart, sliderEnd, it).x, center.y) },
                    pointMode = PointMode.Points,
                    color = if (outsideFraction) inactiveTickColor else activeTickColor,
                    strokeWidth = tickSizePx,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
