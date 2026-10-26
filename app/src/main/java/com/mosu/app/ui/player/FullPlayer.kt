package com.mosu.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.mosu.app.R
import com.mosu.app.data.SettingsManager
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.domain.render.OsuDifficultyMap
import com.mosu.app.domain.render.OsuMapPreviewLoader
import com.mosu.app.domain.render.OsuPlayfieldSize
import com.mosu.app.domain.render.OsuPreviewCatalog
import com.mosu.app.domain.render.OsuRenderTimeline
import com.mosu.app.domain.render.OsuRenderer
import com.mosu.app.domain.render.OsuSkin
import com.mosu.app.domain.render.OsuSkinLoader
import com.mosu.app.domain.render.SmoothPlaybackClock
import com.mosu.app.player.MusicController
import com.mosu.app.player.PlaybackMod
import com.mosu.app.ui.components.Track
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlinx.coroutines.flow.collect

// -- Responsive layout ---------------------------------------------------------
// The full player was designed against a 1216 x 2640 px panel (~2.874 density,
// i.e. ~423 x 918 dp). Instead of hard-coding dp sizes tied to that one panel, the
// vertical regions below are weighted so they scale with whatever height the device
// actually provides. All weights are relative to the cover art's weight of 1.
private const val COVER_WEIGHT = 1f          // cover art slot (grows with the screen)
private const val CONTROLS_WEIGHT = 0.25f    // controls row (≈90 dp on the reference panel)

private const val CONTROL_WIDTH = 0.9f
private const val BOTTOM_GAP_WEIGHT = 0.11f  // bottom breathing room (≈55 dp on the reference panel)
// Buttons are sized as a fraction of the control row's height so they scale with it.
private const val SIDE_BUTTON_HEIGHT_FRACTION = 0.52f // ≈48 dp on the reference panel
private const val PLAY_BUTTON_HEIGHT_FRACTION = 0.78f // ≈72 dp on the reference panel
private const val PLAY_ICON_FRACTION = 0.66f          // ≈48 dp glyph inside the play button
// The Mod/shuffle keys render small content; their boxes hug that ink so SpaceEvenly
// spreads the visible controls evenly rather than the larger empty boxes.
private val ModeIconSize = 24.dp
// Keeps the (centered) content column phone-sized on wider displays instead of
// stretching edge to edge.
private val MaxContentWidth = 480.dp
// Horizontal/landscape layout: top band vs. bottom progress band shares of the height,
// the info column's internal split, and a generous cap so tablets don't stretch edge to edge.
private const val TOP_BAND_WEIGHT = 0.85f      // cover + info share of the height
private const val PROGRESS_BAND_WEIGHT = 0.15f  // progress bar share of the height
// Equal flexible spacers above and below the horizontal content keep the block vertically
// centered while the column keeps taking up the full size.
private const val H_CENTER_SPACER_WEIGHT = 0.05f
private const val H_INFO_TOP_WEIGHT = 1f      // space above the title in the info column
private val HorizontalMaxContentWidth = 960.dp
// Above a 3:2 aspect ratio the progress bar moves into the info column so the (now
// full-height) cover band can grow instead of bleeding height into an empty progress band.
private const val WIDE_HORIZONTAL_RATIO = 1.5f
// Horizontal modes size the transport buttons from the info column's width (the space left
// of the cover art) rather than the row height, which balloons them on short/wide screens.
private const val H_SIDE_BUTTON_WIDTH_FRACTION = 0.18f
private const val H_PLAY_BUTTON_WIDTH_FRACTION = 0.20f
private const val PREVIEW_ASPECT_RATIO = 7f / 6f
private val PreviewEdgeInset = 8.dp
private val PreviewBorderWidth = 1.dp

private data class OsuPreviewData(
    val mediaId: Long,
    val catalog: OsuPreviewCatalog,
    val selectedDifficulty: String,
    val timeline: OsuRenderTimeline?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayer(
    musicController: MusicController,
    settingsManager: SettingsManager,
    db: AppDatabase,
    isExpanded: Boolean,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nowPlaying by musicController.nowPlaying.collectAsState()
    val isPlaying by musicController.isPlaying.collectAsState()
    val duration by musicController.duration.collectAsState()
    val playbackMod by musicController.playbackMod.collectAsState()
    val currentMediaId by musicController.currentMediaId.collectAsState()
    val rendererPreference by settingsManager.rendererEnabled.collectAsState(initial = null)
    val rendererEnabled = rendererPreference ?: true
    val preferenceLoaded = rendererPreference != null
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val previewLoader = remember(context, db) { OsuMapPreviewLoader(context, db) }
    var previewData by remember { mutableStateOf<OsuPreviewData?>(null) }
    var skin by remember { mutableStateOf<OsuSkin?>(null) }

    LaunchedEffect(
        preferenceLoaded,
        rendererEnabled,
        isExpanded,
        currentMediaId,
        previewLoader
    ) {
        val mediaId = currentMediaId
        if (!preferenceLoaded || !rendererEnabled || !isExpanded || mediaId == null) {
            return@LaunchedEffect
        }
        if (previewData?.mediaId == mediaId) return@LaunchedEffect
        if (previewData?.mediaId != mediaId) previewData = null
        val catalog = previewLoader.loadCatalog(mediaId) ?: return@LaunchedEffect
        previewData = OsuPreviewData(
            mediaId = mediaId,
            catalog = catalog,
            selectedDifficulty = catalog.defaultDifficultyName,
            timeline = null
        )
    }

    LaunchedEffect(
        previewData?.mediaId,
        previewData?.selectedDifficulty,
        rendererEnabled,
        isExpanded
    ) {
        val data = previewData ?: return@LaunchedEffect
        if (!preferenceLoaded || !rendererEnabled || !isExpanded || data.timeline != null) {
            return@LaunchedEffect
        }
        val timeline = previewLoader.loadTimeline(data.catalog, data.selectedDifficulty)
        val latest = previewData
        if (
            latest?.mediaId == data.mediaId &&
            latest.selectedDifficulty == data.selectedDifficulty
        ) {
            previewData = latest.copy(timeline = timeline)
        }
    }

    LaunchedEffect(preferenceLoaded, rendererEnabled, isExpanded) {
        if (!preferenceLoaded || !rendererEnabled || !isExpanded || skin != null) {
            return@LaunchedEffect
        }
        skin = withContext(Dispatchers.IO) {
            runCatching { OsuSkinLoader.load(context) }.getOrNull()
        }
    }

    val activePreview = previewData?.takeIf { it.mediaId == currentMediaId }
    val onRendererEnabledChange: (Boolean) -> Unit = { enabled ->
        scope.launch { settingsManager.saveRendererEnabled(enabled) }
    }
    val onDifficultySelected: (String) -> Unit = { difficultyName ->
        val current = previewData
        if (current != null && current.mediaId == currentMediaId) {
            previewData = current.copy(
                selectedDifficulty = difficultyName,
                timeline = null
            )
        }
    }

    if (nowPlaying != null) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            val isHorizontal = maxWidth.value > (maxHeight.value * 1.2f)
            val isWideHorizontal =
                isHorizontal && maxWidth.value > maxHeight.value * WIDE_HORIZONTAL_RATIO

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

            val artworkUri = nowPlaying?.artworkUri
            val title = nowPlaying?.title?.toString() ?: "Unknown Title"
            val artist = nowPlaying?.artist?.toString() ?: "Unknown Artist"
            if (isHorizontal) {
                HorizontalPlayerLayout(
                    musicController = musicController,
                    isPlaying = isPlaying,
                    playbackMod = playbackMod,
                    duration = duration,
                    artworkUri = artworkUri,
                    title = title,
                    artist = artist,
                    onCollapse = onCollapse,
                    isExpanded = isExpanded,
                    currentMediaId = currentMediaId,
                    rendererEnabled = rendererEnabled,
                    previewData = activePreview,
                    skin = skin,
                    onRendererEnabledChange = onRendererEnabledChange,
                    onDifficultySelected = onDifficultySelected,
                    wideMode = isWideHorizontal,
                    modifier = Modifier
                )
            } else {
                VerticalPlayerLayout(
                    musicController = musicController,
                    isPlaying = isPlaying,
                    playbackMod = playbackMod,
                    duration = duration,
                    artworkUri = artworkUri,
                    title = title,
                    artist = artist,
                    onCollapse = onCollapse,
                    isExpanded = isExpanded,
                    currentMediaId = currentMediaId,
                    rendererEnabled = rendererEnabled,
                    previewData = activePreview,
                    skin = skin,
                    onRendererEnabledChange = onRendererEnabledChange,
                    onDifficultySelected = onDifficultySelected,
                    modifier = Modifier
                )
            }
        }
    }
}

/**
 * Portrait layout: a single centered column (cover, title/artist, progress, controls) whose
 * regions are weighted so they scale with the display height. Kept at phone width by
 * [MaxContentWidth] so it doesn't stretch on wide displays.
 */
@Composable
private fun VerticalPlayerLayout(
    musicController: MusicController,
    isPlaying: Boolean,
    playbackMod: PlaybackMod,
    duration: Long,
    artworkUri: Any?,
    title: String,
    artist: String,
    onCollapse: () -> Unit,
    isExpanded: Boolean,
    currentMediaId: Long?,
    rendererEnabled: Boolean,
    previewData: OsuPreviewData?,
    skin: OsuSkin?,
    onRendererEnabledChange: (Boolean) -> Unit,
    onDifficultySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(CONTROL_WIDTH)
            .widthIn(max = MaxContentWidth)
            .padding(vertical = 10.dp, horizontal = 24.dp),
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

        Spacer(modifier = Modifier.height(5.dp))

        // Square cover art inside a weighted, full-height slot so it scales with the
        // leftover vertical space; the square itself is centered in that slot (a square
        // can't fill a slot that is taller than it is wide).
        Box(
            modifier = Modifier
                .weight(COVER_WEIGHT)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            PlayerArtwork(
                artworkUri = artworkUri,
                timeline = previewData?.timeline,
                skin = skin,
                musicController = musicController,
                isExpanded = isExpanded,
                isPlaying = isPlaying,
                currentMediaId = currentMediaId,
                landscape = false,
                rendererEnabled = rendererEnabled
            )
        }

        Spacer(modifier = Modifier.height(50.dp))

        TrackInfo(
            title = title,
            artist = artist,
            rendererEnabled = rendererEnabled,
            selectedDifficulty = previewData?.selectedDifficulty,
            difficulties = previewData?.catalog?.difficulties.orEmpty(),
            onRendererEnabledChange = onRendererEnabledChange,
            onDifficultySelected = onDifficultySelected
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Isolated Progress Bar and Time Labels
        PlayerProgressBar(
            musicController = musicController,
            duration = duration
        )

        Spacer(modifier = Modifier.height(30.dp))

        // Controls Row — weighted so the row's height scales with the screen.
        PlayerControls(
            musicController = musicController,
            isPlaying = isPlaying,
            playbackMod = playbackMod,
            sizeByWidth = false,
            modifier = Modifier.weight(CONTROLS_WEIGHT)
        )

        Spacer(Modifier.weight(BOTTOM_GAP_WEIGHT))
    }
}

/**
 * Landscape/horizontal layout: square cover art top-left, title/artist + controls top-right
 * (the info column takes whatever width the square cover leaves). The square cover is
 * height-bound so it stays square (see the design note in the layout constants).
 *
 * Two variants, chosen by [wideMode]:
 *  - narrow (up to 3:2): the top band holds ~65% of the height and the progress bar spans a
 *    full-width band below it.
 *  - wide (over 3:2, e.g. 16:9): the top band takes the full height and the progress bar
 *    moves to the bottom of the info column, so the cover can grow instead of bleeding
 *    height into a mostly-empty progress band.
 */
@Composable
private fun HorizontalPlayerLayout(
    musicController: MusicController,
    isPlaying: Boolean,
    playbackMod: PlaybackMod,
    duration: Long,
    artworkUri: Any?,
    title: String,
    artist: String,
    onCollapse: () -> Unit,
    isExpanded: Boolean,
    currentMediaId: Long?,
    rendererEnabled: Boolean,
    previewData: OsuPreviewData?,
    skin: OsuSkin?,
    onRendererEnabledChange: (Boolean) -> Unit,
    onDifficultySelected: (String) -> Unit,
    wideMode: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier
            // Cap the width *before* filling so the filled column is centered by the
            // parent (TopCenter) instead of bleeding edge-to-edge on wide displays.
            .widthIn(max = HorizontalMaxContentWidth)
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Equal spacers above and below center the whole block vertically instead of
        // letting the cover band hug the top of the screen.
        Spacer(Modifier.weight(H_CENTER_SPACER_WEIGHT))

        // Top band: cover on the left, info + controls on the right. In wide mode the
        // band takes the full height and also hosts the progress bar (there is no
        // separate bottom band), leaving the cover room to grow.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (wideMode) 1f else TOP_BAND_WEIGHT),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Collapse handle in the top-right corner so it doesn't consume info-column space.


            PlayerArtwork(
                artworkUri = artworkUri,
                timeline = previewData?.timeline,
                skin = skin,
                musicController = musicController,
                isExpanded = isExpanded,
                isPlaying = isPlaying,
                currentMediaId = currentMediaId,
                landscape = true,
                rendererEnabled = rendererEnabled
            )
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(start = 24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)


                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(28.dp)
                    )
                }
                if (wideMode) {
                    // Wide mode: the info block sits toward the bottom of the column with
                    // the progress bar pinned to the very bottom (aligned with the cover).
                    Spacer(Modifier.weight(1f))
                    TrackInfo(
                        title = title,
                        artist = artist,
                        titleMaxLines = 2,
                        rendererEnabled = rendererEnabled,
                        selectedDifficulty = previewData?.selectedDifficulty,
                        difficulties = previewData?.catalog?.difficulties.orEmpty(),
                        onRendererEnabledChange = onRendererEnabledChange,
                        onDifficultySelected = onDifficultySelected
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    PlayerControls(
                        musicController = musicController,
                        isPlaying = isPlaying,
                        playbackMod = playbackMod,
                        sizeByWidth = true
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    PlayerProgressBar(
                        musicController = musicController,
                        duration = duration
                    )
                } else {
                    Spacer(Modifier.weight(H_INFO_TOP_WEIGHT))
                    TrackInfo(
                        title = title,
                        artist = artist,
                        titleMaxLines = 2,
                        rendererEnabled = rendererEnabled,
                        selectedDifficulty = previewData?.selectedDifficulty,
                        difficulties = previewData?.catalog?.difficulties.orEmpty(),
                        onRendererEnabledChange = onRendererEnabledChange,
                        onDifficultySelected = onDifficultySelected
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    PlayerControls(
                        musicController = musicController,
                        isPlaying = isPlaying,
                        playbackMod = playbackMod,
                        sizeByWidth = true
                    )
                }
            }
        }

        if (!wideMode) {
            // Bottom band: the progress bar spans the width and is pinned to the bottom of
            // its band so the band's spare height all sits above the bar instead of being
            // split into a phantom gap above and below it.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(PROGRESS_BAND_WEIGHT),
                contentAlignment = Alignment.BottomCenter
            ) {
                PlayerProgressBar(
                    musicController = musicController,
                    duration = duration
                )
            }
        }

        Spacer(Modifier.weight(H_CENTER_SPACER_WEIGHT))
    }

}

@Composable
private fun CoverArt(
    artworkUri: Any?,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = artworkUri,
        contentDescription = "Cover Art",
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun PlayerArtwork(
    artworkUri: Any?,
    timeline: OsuRenderTimeline?,
    skin: OsuSkin?,
    musicController: MusicController,
    isExpanded: Boolean,
    isPlaying: Boolean,
    currentMediaId: Long?,
    landscape: Boolean,
    rendererEnabled: Boolean
) {
    if (!rendererEnabled || timeline == null || skin == null) {
        CoverArt(artworkUri = artworkUri)
        return
    }

    var mapTimeMs by remember(timeline, currentMediaId) {
        mutableDoubleStateOf(musicController.currentPositionMs().toDouble())
    }
    val playbackClock = remember { SmoothPlaybackClock() }
    LaunchedEffect(isExpanded, isPlaying, timeline, currentMediaId) {
        if (!isExpanded) return@LaunchedEffect
        mapTimeMs = musicController.currentPositionMs().toDouble()
        if (isPlaying) {
            var firstFrame = true
            var handledSeekSequence = Long.MIN_VALUE
            while (isActive) {
                withFrameNanos { frameNanos ->
                    val reportedPositionMs = musicController.currentPositionMs()
                    val playbackSpeed = musicController.currentPlaybackSpeed()
                    val seek = musicController.playbackSeek.value?.takeIf {
                        it.sequence != handledSeekSequence && it.mediaId == currentMediaId
                    }
                    if (firstFrame || seek != null) {
                        // Rebase on the seek target in the very next frame. Waiting
                        // for Media3's next 300 ms progress sample makes the map
                        // visibly lag behind (or lead) the freshly-seeked audio.
                        val positionMs = seek?.positionMs ?: reportedPositionMs
                        playbackClock.reset(positionMs, frameNanos, playbackSpeed)
                        mapTimeMs = positionMs.toDouble()
                        if (seek != null) handledSeekSequence = seek.sequence
                        firstFrame = false
                    } else {
                        mapTimeMs = playbackClock.update(
                            frameNanos = frameNanos,
                            reportedPositionMs = reportedPositionMs,
                            playbackSpeed = playbackSpeed
                        )
                    }
                }
            }
        } else {
            // Keep a paused seek responsive without running a frame loop.
            musicController.currentPosition.collect { positionMs ->
                mapTimeMs = positionMs.toDouble()
            }
        }
    }

    val renderer = remember(skin) { OsuRenderer(skin) }
    val sizeModifier = if (landscape) {
        Modifier
            .fillMaxHeight()
            .aspectRatio(PREVIEW_ASPECT_RATIO)
    } else {
        Modifier
            .fillMaxWidth()
            .aspectRatio(PREVIEW_ASPECT_RATIO)
    }

    Box(
        modifier = sizeModifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF05050A))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            renderer.draw(
                canvas = drawContext.canvas.nativeCanvas,
                timeline = timeline,
                mapTimeMs = mapTimeMs,
                playfieldSize = OsuPlayfieldSize(size.width, size.height),
                edgeInsetPx = PreviewEdgeInset.toPx(),
                borderWidthPx = PreviewBorderWidth.toPx()
            )
        }
    }
}

@Composable
private fun TrackInfo(
    title: String,
    artist: String,
    rendererEnabled: Boolean,
    selectedDifficulty: String?,
    difficulties: List<OsuDifficultyMap>,
    onRendererEnabledChange: (Boolean) -> Unit,
    onDifficultySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    titleMaxLines: Int = 1
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
        Spacer(Modifier.width(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (difficulties.size > 1) {
                DifficultySelector(
                    selectedDifficulty = selectedDifficulty ?: difficulties.first().difficultyName,
                    difficulties = difficulties,
                    onDifficultySelected = onDifficultySelected
                )
            }
            RendererToggle(
                enabled = rendererEnabled,
                onEnabledChange = onRendererEnabledChange
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DifficultySelector(
    selectedDifficulty: String,
    difficulties: List<OsuDifficultyMap>,
    onDifficultySelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .widthIn(max = 156.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(start = 8.dp, end = 2.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedDifficulty,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.player_select_difficulty),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            difficulties.forEach { difficulty ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = difficulty.difficultyName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        expanded = false
                        if (difficulty.difficultyName != selectedDifficulty) {
                            onDifficultySelected(difficulty.difficultyName)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RendererToggle(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    IconButton(
        onClick = { onEnabledChange(!enabled) },
        modifier = Modifier.size(34.dp)
    ) {
        Icon(
            imageVector = if (enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
            contentDescription = stringResource(
                if (enabled) R.string.player_show_cover else R.string.player_show_renderer
            ),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerControls(
    musicController: MusicController,
    isPlaying: Boolean,
    playbackMod: PlaybackMod,
    sizeByWidth: Boolean,
    modifier: Modifier = Modifier
) {
    val repeatMode by musicController.repeatMode.collectAsState()
    val shuffleModeEnabled by musicController.shuffleModeEnabled.collectAsState()
    var modMenuExpanded by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Vertical mode sizes buttons off the row height; horizontal modes size them off the
        // info column's width (the space left of the cover art) so they stay proportional
        // even when the row is short and wide.
        val sideButtonSize = if (sizeByWidth) {
            maxWidth * H_SIDE_BUTTON_WIDTH_FRACTION
        } else {
            maxHeight * SIDE_BUTTON_HEIGHT_FRACTION
        }
        val playButtonSize = if (sizeByWidth) {
            maxWidth * H_PLAY_BUTTON_WIDTH_FRACTION
        } else {
            maxHeight * PLAY_BUTTON_HEIGHT_FRACTION
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The five controls sit at their natural rendered size and SpaceEvenly spreads
            // the gaps equally. Centering them in equal-width slots instead added empty
            // padding around the small Mod/Mode keys, pushing them away from the
            // transport keys.
            ModControl(
                playbackMod = playbackMod,
                expanded = modMenuExpanded,
                onExpandedChange = { modMenuExpanded = it },
                onSelect = { musicController.setPlaybackMod(it) }
            )
            TransportButton(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                tint = MaterialTheme.colorScheme.onBackground,
                size = sideButtonSize,
                onClick = { musicController.skipToPrevious() }
            )
            PlayPauseButton(
                size = playButtonSize,
                isPlaying = isPlaying,
                onClick = { musicController.togglePlayPause() }
            )
            TransportButton(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next",
                tint = MaterialTheme.colorScheme.onBackground,
                size = sideButtonSize,
                onClick = { musicController.skipToNext() }
            )
            ModeButton(
                musicController = musicController,
                repeatMode = repeatMode,
                shuffleModeEnabled = shuffleModeEnabled
            )
        }
    }
}
//
///**
// * Legacy (removed to minimize the complexity of the player) Wide horizontal variant (aspect ratio above 3:2): transport controls on the first row and
// * the Mod / shuffle-cycle controls on a second row, so nothing is crowded out horizontally.
// * The Mod key is shown inline ("Mod: NM"). Buttons are still sized from the info column width.
// */
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//private fun WidePlayerControls(
//    musicController: MusicController,
//    isPlaying: Boolean,
//    playbackMod: PlaybackMod,
//    modifier: Modifier = Modifier
//) {
//    val repeatMode by musicController.repeatMode.collectAsState()
//    val shuffleModeEnabled by musicController.shuffleModeEnabled.collectAsState()
//    var modMenuExpanded by remember { mutableStateOf(false) }
//
//    BoxWithConstraints(
//        modifier = modifier.fillMaxWidth(),
//        contentAlignment = Alignment.Center
//    ) {
//        val sideButtonSize = maxWidth * H_SIDE_BUTTON_WIDTH_FRACTION
//        val playButtonSize = maxWidth * H_PLAY_BUTTON_WIDTH_FRACTION
//
//        Column(modifier = Modifier.fillMaxWidth()) {
//            // Row 2: Mod (inline) on the left, shuffle/cycle on the right.
//            Row(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.SpaceBetween,
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                ModControl(
//                    playbackMod = playbackMod,
//                    expanded = modMenuExpanded,
//                    onExpandedChange = { modMenuExpanded = it },
//                    onSelect = { musicController.setPlaybackMod(it) }
//                )
//                ModeButton(
//                    musicController = musicController,
//                    repeatMode = repeatMode,
//                    shuffleModeEnabled = shuffleModeEnabled
//                )
//            }
//            Spacer(Modifier.height(10.dp))
//
//            // Row 1: previous / play-pause / next, centered.
//            Row(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.SpaceEvenly,
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                TransportButton(
//                    imageVector = Icons.Default.SkipPrevious,
//                    contentDescription = "Previous",
//                    tint = MaterialTheme.colorScheme.onBackground,
//                    size = sideButtonSize,
//                    onClick = { musicController.skipToPrevious() }
//                )
//                Spacer(Modifier.width(sideButtonSize * 0.5f))
//                PlayPauseButton(
//                    size = playButtonSize,
//                    isPlaying = isPlaying,
//                    onClick = { musicController.togglePlayPause() }
//                )
//                Spacer(Modifier.width(sideButtonSize * 0.5f))
//                TransportButton(
//                    imageVector = Icons.Default.SkipNext,
//                    contentDescription = "Next",
//                    tint = MaterialTheme.colorScheme.onBackground,
//                    size = sideButtonSize,
//                    onClick = { musicController.skipToNext() }
//                )
//            }
//        }
//    }
//}

@Composable
private fun TransportButton(
    imageVector: ImageVector,
    contentDescription: String,
    tint: Color,
    size: Dp,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size)
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun PlayPauseButton(
    size: Dp,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.fillMaxSize(PLAY_ICON_FRACTION)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModControl(
    playbackMod: PlaybackMod,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (PlaybackMod) -> Unit,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false
) {
    val modTint = when (playbackMod) {
        PlaybackMod.NIGHT_CORE -> MaterialTheme.colorScheme.primary
        PlaybackMod.DOUBLE_TIME -> Color(0xFFFFD059)
        else -> MaterialTheme.colorScheme.onBackground
    }
    // Wraps its own content so the box hugs the text; SpaceEvenly then distributes the
    // actual rendered sizes instead of measuring a larger empty box.
    Box(modifier, contentAlignment = Alignment.Center) {
        if (horizontal) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onExpandedChange(true) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mod: ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                Text(
                    text = modLabel(playbackMod),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = modTint
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .clickable { onExpandedChange(true) }
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
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            PlaybackMod.entries.forEach { mod ->
                DropdownMenuItem(
                    modifier = Modifier.alpha(0.9f).fillMaxSize(),
                    text = { Text(
                        text = modMenuLabel(mod),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = when (mod) {
                            PlaybackMod.NIGHT_CORE -> MaterialTheme.colorScheme.primary
                            PlaybackMod.DOUBLE_TIME -> Color(0xFFFFD059)
                            else -> MaterialTheme.colorScheme.onBackground
                        },
                    ) },
                    onClick = {
                        onSelect(mod)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

@Composable
private fun ModeButton(
    musicController: MusicController,
    repeatMode: Int,
    shuffleModeEnabled: Boolean
) {
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
        modifier = Modifier.size(ModeIconSize)
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

    Column(
        modifier = Modifier.fillMaxWidth(0.97f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
