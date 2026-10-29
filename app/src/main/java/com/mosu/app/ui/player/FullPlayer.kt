package com.mosu.app.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Fullscreen
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.media3.common.Player
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
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
import com.mosu.app.domain.rating.BeatmapStarRatingCatalog
import com.mosu.app.domain.rating.BeatmapStarRatingService
import com.mosu.app.domain.rating.DifficultyRatingQuery
import com.mosu.app.player.MusicController
import com.mosu.app.player.PlaybackMod
import com.mosu.app.ui.components.StarRatingBadge
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

data class LandscapePlayerMorph(
    val progress: Float,
    val compactSize: DpSize,
    val fullscreenSize: DpSize
)

internal data class LandscapePlayerDragPosition(
    val sheetOffsetPx: Float,
    val expansionProgress: Float
)

internal data class LandscapePlayerMorphBounds(
    val width: Dp,
    val height: Dp,
    val leftInset: Dp,
    val topInset: Dp
)

/**
 * Advances the two stages of the landscape player gesture from one pointer delta.
 *
 * Dragging left first opens the compact card, then expands it. Dragging right reverses
 * that order exactly. The expansion is deliberately a little faster than the finger so
 * the card reaches fullscreen without an excessive swipe, while every visual element
 * still shares this single progress value.
 */
internal fun advanceLandscapePlayerDrag(
    position: LandscapePlayerDragPosition,
    deltaPx: Float,
    collapsedOffsetPx: Float,
    expansionDistancePx: Float,
    expansionGain: Float
): LandscapePlayerDragPosition {
    val collapsedOffset = collapsedOffsetPx.coerceAtLeast(0f)
    var sheetOffset = position.sheetOffsetPx.coerceIn(0f, collapsedOffset)
    var progress = position.expansionProgress.coerceIn(0f, 1f)
    if (!deltaPx.isFinite() || deltaPx == 0f) {
        return LandscapePlayerDragPosition(sheetOffset, progress)
    }

    val distance = expansionDistancePx.coerceAtLeast(1f)
    val gain = expansionGain.takeIf { it.isFinite() && it > 0f } ?: 1f
    var remaining = deltaPx

    if (remaining < 0f) {
        if (sheetOffset > 0f) {
            val consumed = remaining.coerceAtLeast(-sheetOffset)
            sheetOffset += consumed
            remaining -= consumed
        }
        if (remaining < 0f) {
            progress = (
                progress - remaining * gain / distance
                ).coerceIn(0f, 1f)
        }
    } else {
        if (progress > 0f) {
            val dragDistanceForProgress = progress * distance / gain
            val consumed = remaining.coerceAtMost(dragDistanceForProgress)
            progress = (
                progress - consumed * gain / distance
                ).coerceIn(0f, 1f)
            remaining -= consumed
        }
        if (remaining > 0f) {
            sheetOffset = (sheetOffset + remaining).coerceIn(0f, collapsedOffset)
        }
    }

    return LandscapePlayerDragPosition(sheetOffset, progress)
}

/**
 * Returns the right-edge-aligned card bounds for the shared compact/fullscreen progress.
 *
 * Width and height use the same fraction. Because the card is vertically centered, the
 * top and bottom insets therefore also advance by that same fraction, keeping the
 * horizontal and vertical expansion visually synchronized.
 */
internal fun landscapePlayerMorphBounds(
    compactSize: DpSize,
    fullscreenSize: DpSize,
    progress: Float
): LandscapePlayerMorphBounds {
    val fraction = progress.coerceIn(0f, 1f)
    val width = lerp(compactSize.width, fullscreenSize.width, fraction)
    val height = lerp(compactSize.height, fullscreenSize.height, fraction)
    return LandscapePlayerMorphBounds(
        width = width,
        height = height,
        leftInset = fullscreenSize.width - width,
        topInset = (fullscreenSize.height - height) / 2f
    )
}

internal data class LandscapeMorphRect(
    val left: Dp,
    val top: Dp,
    val width: Dp,
    val height: Dp
) {
    fun lerp(other: LandscapeMorphRect, fraction: Float): LandscapeMorphRect =
        LandscapeMorphRect(
            left = lerp(left, other.left, fraction),
            top = lerp(top, other.top, fraction),
            width = lerp(width, other.width, fraction),
            height = lerp(height, other.height, fraction)
        )
}

internal data class LandscapeMorphRects(
    val artwork: LandscapeMorphRect,
    val title: LandscapeMorphRect,
    val difficulty: LandscapeMorphRect,
    val rendererToggle: LandscapeMorphRect,
    val controls: LandscapeMorphRect,
    val progress: LandscapeMorphRect,
    val collapse: LandscapeMorphRect,
    val fullscreen: LandscapeMorphRect
)
// Horizontal/landscape layout: top band vs. bottom progress band shares of the height,
// the info column's internal split, and a generous cap so tablets don't stretch edge to edge.
private const val TOP_BAND_WEIGHT = 0.85f      // cover + info share of the height
private const val PROGRESS_BAND_WEIGHT = 0.15f  // progress bar share of the height
// Equal flexible spacers above and below the horizontal content keep the block vertically
// centered while the column keeps taking up the full size.
private const val H_CENTER_SPACER_WEIGHT = 0.05f
private const val H_INFO_TOP_WEIGHT = 1f      // space above the title in the info column
private val HorizontalMaxContentWidth = 960.dp
private val HorizontalMetadataControlGap = 12.dp
// Above a 3:2 aspect ratio the progress bar moves into the info column so the (now
// full-height) cover band can grow instead of bleeding height into an empty progress band.
private const val WIDE_HORIZONTAL_RATIO = 1.5f
// Horizontal modes size the transport buttons from the info column's width (the space left
// of the cover art) rather than the row height, which balloons them on short/wide screens.
private const val H_SIDE_BUTTON_WIDTH_FRACTION = 0.18f
private const val H_PLAY_BUTTON_WIDTH_FRACTION = 0.20f
// The compact fullscreen action fades out before its slot converges with the collapse
// action. Removing its pointer node at that point prevents the two overlapping controls
// from competing for the same taps during and after expansion.
private const val FULLSCREEN_ACTION_FADE_END = 0.2f
private const val PREVIEW_ASPECT_RATIO = 7f / 6f
private const val COMPACT_PROGRESS_WIDTH_FRACTION = 0.90f
private const val FULL_PROGRESS_WIDTH_FRACTION = 0.97f
private val PreviewEdgeInset = 8.dp
private val PreviewFeatherWidth = 60.dp
private val PreviewCompactInnerFeather = 10.dp
private val PreviewShadowOffsetY = 4.dp
private val PreviewCornerRadius = 36.dp
private const val PREVIEW_CANVAS_ALPHA = 0.82f
private const val PREVIEW_SHADOW_ALPHA = 0.50f
private const val PREVIEW_CANVAS_EDGE_OPACITY = 0f
private const val PREVIEW_SHADOW_RAMP_END = 0.78f
private const val SHADOW_COLOR_SAMPLE_SIZE = 48
private const val SHADOW_MAX_BRIGHTNESS = 0.34f
private val DefaultPreviewShadowColor = Color(0xFF05050A)

private data class OsuPreviewData(
    val mediaId: Long,
    val catalog: OsuPreviewCatalog,
    val selectedDifficulty: String,
    val starRatings: BeatmapStarRatingCatalog = BeatmapStarRatingCatalog.Empty,
    val timeline: OsuRenderTimeline?,
    val timelineLoadAttempted: Boolean = false
)

private fun BeatmapStarRatingCatalog.ratingFor(difficulty: OsuDifficultyMap): Float? =
    ratingFor(
        DifficultyRatingQuery(
            beatmapId = difficulty.beatmapId,
            mode = difficulty.mode,
            difficultyName = difficulty.difficultyName
        )
    )

private suspend fun loadDominantShadowColor(
    context: Context,
    artworkUri: Any
): Color? = withContext(Dispatchers.IO) {
    runCatching {
        val request = ImageRequest.Builder(context)
            .data(artworkUri)
            .size(SHADOW_COLOR_SAMPLE_SIZE, SHADOW_COLOR_SAMPLE_SIZE)
            .allowHardware(false)
            .build()
        val bitmap = (context.imageLoader.execute(request) as? SuccessResult)
            ?.drawable
            ?.toBitmap()
            ?: return@runCatching null

        // Quantize RGB to 4 bits per channel and use the most populated bin. This is
        // intentionally the modal background colour rather than the average, which
        // tends to turn multi-coloured covers into a muddy grey.
        val counts = IntArray(16 * 16 * 16)
        val redSums = LongArray(counts.size)
        val greenSums = LongArray(counts.size)
        val blueSums = LongArray(counts.size)
        var sampledPixels = 0

        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (pixel ushr 24 < 32) continue
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                val bin = ((red shr 4) shl 8) or ((green shr 4) shl 4) or (blue shr 4)
                counts[bin]++
                redSums[bin] += red.toLong()
                greenSums[bin] += green.toLong()
                blueSums[bin] += blue.toLong()
                sampledPixels++
            }
        }
        if (sampledPixels == 0) return@runCatching null

        var modalBin = 0
        for (index in 1 until counts.size) {
            if (counts[index] > counts[modalBin]) modalBin = index
        }
        val modalCount = counts[modalBin]
        if (modalCount == 0) return@runCatching null

        val red = redSums[modalBin].toFloat() / modalCount / 255f
        val green = greenSums[modalBin].toFloat() / modalCount / 255f
        val blue = blueSums[modalBin].toFloat() / modalCount / 255f
        val brightestChannel = maxOf(red, green, blue)
        val brightnessScale = if (brightestChannel > 0f) {
            (SHADOW_MAX_BRIGHTNESS / brightestChannel).coerceAtMost(1f)
        } else {
            0f
        }
        Color(
            red = red * brightnessScale,
            green = green * brightnessScale,
            blue = blue * brightnessScale
        )
    }.getOrNull()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayer(
    musicController: MusicController,
    settingsManager: SettingsManager,
    db: AppDatabase,
    isExpanded: Boolean,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    landscapeMorph: LandscapePlayerMorph? = null,
    onEnterFullscreen: () -> Unit = {}
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
    val ratingService = remember(db) { BeatmapStarRatingService(db) }
    var previewData by remember { mutableStateOf<OsuPreviewData?>(null) }
    var catalogUnavailableMediaId by remember { mutableStateOf<Long?>(null) }
    var skin by remember { mutableStateOf<OsuSkin?>(null) }
    var skinLoadAttempted by remember { mutableStateOf(false) }

    LaunchedEffect(
        preferenceLoaded,
        rendererEnabled,
        currentMediaId,
        previewLoader,
        ratingService
    ) {
        val mediaId = currentMediaId
        if (!preferenceLoaded || !rendererEnabled || mediaId == null) {
            return@LaunchedEffect
        }
        val existing = previewData?.takeIf { it.mediaId == mediaId }
        if (previewData?.mediaId != mediaId) previewData = null
        catalogUnavailableMediaId = null
        val catalog = existing?.catalog
            ?: previewLoader.loadCatalog(mediaId)
            ?: run {
                if (currentMediaId == mediaId) catalogUnavailableMediaId = mediaId
                return@LaunchedEffect
            }
        val cachedRatings = ratingService.getCachedRatings(catalog.beatmapSetId)
        val selectedDifficulty = existing?.selectedDifficulty
            ?: catalog.resolveSavedDifficulty(
                settingsManager.getSelectedDifficulty(catalog.selectionKey)
            )
        previewData = OsuPreviewData(
            mediaId = mediaId,
            catalog = catalog,
            selectedDifficulty = selectedDifficulty,
            starRatings = cachedRatings,
            timeline = existing?.timeline,
            timelineLoadAttempted = existing?.timelineLoadAttempted ?: false
        )

        val requiredDifficulties = catalog.difficulties.map { difficulty ->
            DifficultyRatingQuery(
                beatmapId = difficulty.beatmapId,
                mode = difficulty.mode,
                difficultyName = difficulty.difficultyName
            )
        }
        if (requiredDifficulties.any { !cachedRatings.hasRatingFor(it) }) {
            val refreshed = ratingService.refreshMissingRatings(
                beatmapSetId = catalog.beatmapSetId,
                requiredDifficulties = requiredDifficulties
            )
            val latest = previewData
            if (
                latest?.mediaId == mediaId &&
                latest.catalog.beatmapSetId == catalog.beatmapSetId
            ) {
                previewData = latest.copy(starRatings = refreshed)
            }
        }
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
            previewData = latest.copy(
                timeline = timeline,
                timelineLoadAttempted = true
            )
        }
    }

    LaunchedEffect(preferenceLoaded, rendererEnabled, isExpanded) {
        if (!preferenceLoaded || !rendererEnabled || !isExpanded || skin != null) {
            return@LaunchedEffect
        }
        skin = withContext(Dispatchers.IO) {
            runCatching { OsuSkinLoader.load(context) }.getOrNull()
        }
        skinLoadAttempted = true
    }

    val activePreview = previewData?.takeIf { it.mediaId == currentMediaId }
    val previewUnavailable = when {
        !rendererEnabled -> true
        currentMediaId == null -> true
        activePreview == null -> catalogUnavailableMediaId == currentMediaId
        activePreview.timeline == null -> activePreview.timelineLoadAttempted
        skin == null -> skinLoadAttempted
        else -> false
    }
    val onRendererEnabledChange: (Boolean) -> Unit = { enabled ->
        scope.launch { settingsManager.saveRendererEnabled(enabled) }
    }
    val onDifficultySelected: (String) -> Unit = { difficultyName ->
        val current = previewData
        if (current != null && current.mediaId == currentMediaId) {
            previewData = current.copy(
                selectedDifficulty = difficultyName,
                timeline = null,
                timelineLoadAttempted = false
            )
            scope.launch {
                settingsManager.saveSelectedDifficulty(
                    songKey = current.catalog.selectionKey,
                    difficultyName = difficultyName
                )
            }
        }
    }

    if (nowPlaying != null) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .consumeUnhandledTaps(isExpanded),
            contentAlignment = Alignment.TopCenter
        ) {
            val isLandscapeMorph = landscapeMorph != null
            val isHorizontal =
                !isLandscapeMorph && maxWidth.value > (maxHeight.value * 1.2f)
            val isWideHorizontal =
                isHorizontal && (
                    maxWidth.value > maxHeight.value * WIDE_HORIZONTAL_RATIO
                )

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
            val compactWeight = 1f -
                (landscapeMorph?.progress ?: 1f).coerceIn(0f, 1f)
            val backgroundColor = MaterialTheme.colorScheme.background
            fun scrimAlpha(compactAlpha: Float, fullscreenAlpha: Float) =
                fullscreenAlpha + (compactAlpha - fullscreenAlpha) * compactWeight
            val backgroundScrim = Brush.verticalGradient(
                colors = listOf(
                    backgroundColor.copy(alpha = scrimAlpha(0.45f, 0.5f)),
                    backgroundColor.copy(alpha = scrimAlpha(0.6f, 0.9f)),
                    backgroundColor.copy(alpha = scrimAlpha(0.75f, 1f))
                )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundScrim)
            )

            val artworkUri = nowPlaying?.artworkUri
            val title = nowPlaying?.title?.toString() ?: "Unknown Title"
            val artist = nowPlaying?.artist?.toString() ?: "Unknown Artist"
            if (landscapeMorph != null) {
                MorphingLandscapePlayerLayout(
                    musicController = musicController,
                    isPlaying = isPlaying,
                    playbackMod = playbackMod,
                    duration = duration,
                    artworkUri = artworkUri,
                    title = title,
                    artist = artist,
                    onCollapse = onCollapse,
                    onEnterFullscreen = onEnterFullscreen,
                    isExpanded = isExpanded,
                    currentMediaId = currentMediaId,
                    rendererEnabled = rendererEnabled,
                    previewUnavailable = previewUnavailable,
                    previewData = activePreview,
                    skin = skin,
                    onRendererEnabledChange = onRendererEnabledChange,
                    onDifficultySelected = onDifficultySelected,
                    morph = landscapeMorph,
                    modifier = Modifier
                )
            } else if (isHorizontal) {
                HorizontalPlayerLayout(
                    musicController = musicController,
                    isPlaying = isPlaying,
                    playbackMod = playbackMod,
                    duration = duration,
                    artworkUri = artworkUri,
                    title = title,
                    artist = artist,
                    onCollapse = onCollapse,
                    collapseDescription = "Collapse",
                    isExpanded = isExpanded,
                    currentMediaId = currentMediaId,
                    rendererEnabled = rendererEnabled,
                    previewUnavailable = previewUnavailable,
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
                    previewUnavailable = previewUnavailable,
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
 * The expanded player is drawn above the rest of the screen, but Compose only hit-tests
 * nodes that handle pointer input. Without this node, taps on non-interactive areas such
 * as the artwork renderer fall through to controls on the screen underneath.
 *
 * The gesture detector waits on the Main pass, so clickable descendants consume their
 * taps first and continue to work normally.
 */
private fun Modifier.consumeUnhandledTaps(enabled: Boolean): Modifier =
    if (enabled) {
        pointerInput(Unit) {
            detectTapGestures(onTap = {})
        }
    } else {
        this
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
    previewUnavailable: Boolean,
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
                currentMediaId = currentMediaId,
                landscape = false,
                rendererEnabled = rendererEnabled,
                previewUnavailable = previewUnavailable
            )
        }

        Spacer(modifier = Modifier.height(50.dp))

        TrackInfo(
            title = title,
            artist = artist,
            rendererEnabled = rendererEnabled,
            selectedDifficulty = previewData?.selectedDifficulty,
            difficulties = previewData?.catalog?.difficulties.orEmpty(),
            starRatings = previewData?.starRatings ?: BeatmapStarRatingCatalog.Empty,
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
 * One landscape player composable for the whole compact-to-fullscreen gesture.
 *
 * Both endpoint layouts are reduced to rectangles in the card's coordinate space. Every
 * element then uses the same normalized progress, so the card border and its contents
 * expand together instead of one layout being cross-faded into another.
 */
@Composable
private fun MorphingLandscapePlayerLayout(
    musicController: MusicController,
    isPlaying: Boolean,
    playbackMod: PlaybackMod,
    duration: Long,
    artworkUri: Any?,
    title: String,
    artist: String,
    onCollapse: () -> Unit,
    onEnterFullscreen: () -> Unit,
    isExpanded: Boolean,
    currentMediaId: Long?,
    rendererEnabled: Boolean,
    previewUnavailable: Boolean,
    previewData: OsuPreviewData?,
    skin: OsuSkin?,
    onRendererEnabledChange: (Boolean) -> Unit,
    onDifficultySelected: (String) -> Unit,
    morph: LandscapePlayerMorph,
    modifier: Modifier = Modifier
) {
    val progress = morph.progress.coerceIn(0f, 1f)
    val compactSize = morph.compactSize
    val fullscreenSize = morph.fullscreenSize
    val showDifficultySelector =
        rendererEnabled && previewData?.catalog?.difficulties.orEmpty().size > 1
    val wideMode =
        fullscreenSize.width.value > fullscreenSize.height.value * WIDE_HORIZONTAL_RATIO

    val compactRects = remember(compactSize, showDifficultySelector) {
        compactLandscapeMorphRects(
            compactSize = compactSize,
            showDifficultySelector = showDifficultySelector
        )
    }
    val fullscreenRects = remember(fullscreenSize, wideMode, rendererEnabled, previewUnavailable) {
        fullscreenLandscapeMorphRects(
            size = fullscreenSize,
            wideMode = wideMode,
            artworkAspectRatio = if (rendererEnabled && !previewUnavailable) {
                PREVIEW_ASPECT_RATIO
            } else {
                1f
            }
        )
    }
    val rects = LandscapeMorphRects(
        artwork = compactRects.artwork.lerp(fullscreenRects.artwork, progress),
        title = compactRects.title.lerp(fullscreenRects.title, progress),
        difficulty = compactRects.difficulty.lerp(fullscreenRects.difficulty, progress),
        rendererToggle = compactRects.rendererToggle.lerp(
            fullscreenRects.rendererToggle,
            progress
        ),
        controls = compactRects.controls.lerp(fullscreenRects.controls, progress),
        progress = compactRects.progress.lerp(fullscreenRects.progress, progress),
        collapse = compactRects.collapse.lerp(fullscreenRects.collapse, progress),
        fullscreen = compactRects.fullscreen.lerp(fullscreenRects.fullscreen, progress)
    )
    val selectedDifficulty = previewData?.selectedDifficulty
        ?: previewData?.catalog?.difficulties?.firstOrNull()?.difficultyName

    Box(modifier = modifier.fillMaxSize()) {
        MorphSlot(rects.artwork) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                PlayerArtwork(
                    artworkUri = artworkUri,
                    timeline = previewData?.timeline,
                    skin = skin,
                    musicController = musicController,
                    isExpanded = isExpanded,
                    currentMediaId = currentMediaId,
                    landscape = true,
                    rendererEnabled = rendererEnabled,
                    previewUnavailable = previewUnavailable,
                    fillSlot = true,
                    innerFeather = lerp(PreviewCompactInnerFeather, 0.dp, progress)
                )
            }
        }

        MorphSlot(rects.title) {
            MorphingTrackMetadata(
                title = title,
                artist = artist,
                progress = progress
            )
        }

        MorphSlot(rects.difficulty) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (showDifficultySelector && selectedDifficulty != null) {
                    DifficultySelector(
                        selectedDifficulty = selectedDifficulty,
                        difficulties = previewData?.catalog?.difficulties.orEmpty(),
                        starRatings = previewData?.starRatings
                            ?: BeatmapStarRatingCatalog.Empty,
                        onDifficultySelected = onDifficultySelected,
                        compact = rects.difficulty.width < 96.dp
                    )
                }
            }
        }

        MorphSlot(rects.rendererToggle) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                RendererToggle(
                    enabled = rendererEnabled,
                    onEnabledChange = onRendererEnabledChange
                )
            }
        }

        MorphSlot(rects.controls) {
            PlayerControls(
                musicController = musicController,
                isPlaying = isPlaying,
                playbackMod = playbackMod,
                sizeByWidth = false,
                morphProgress = progress,
                modifier = Modifier.fillMaxSize()
            )
        }

        MorphSlot(rects.progress) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                PlayerProgressBar(
                    musicController = musicController,
                    duration = duration,
                    sliderHeight = lerp(26.dp, 36.dp, progress),
                    widthFraction = COMPACT_PROGRESS_WIDTH_FRACTION +
                        (FULL_PROGRESS_WIDTH_FRACTION - COMPACT_PROGRESS_WIDTH_FRACTION) *
                        progress,
                    trackBoundsAlignment = 1f - progress
                )
            }
        }

        // Compose the fullscreen action first and remove it before the slots overlap.
        // The collapse action is drawn and hit-tested last throughout the morph, so it
        // always owns the shared endpoint.
        if (progress < FULLSCREEN_ACTION_FADE_END) {
            val visibility = 1f - progress / FULLSCREEN_ACTION_FADE_END
            MorphSlot(rects.fullscreen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = visibility
                            scaleX = 0.65f + visibility * 0.35f
                            scaleY = 0.65f + visibility * 0.35f
                        }
                ) {
                    IconButton(
                        onClick = onEnterFullscreen,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Enter fullscreen",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        MorphSlot(rects.collapse) {
            IconButton(
                onClick = onCollapse,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = if (progress >= 0.5f) {
                        "Exit fullscreen"
                    } else {
                        "Collapse player"
                    },
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .size(28.dp)
                        .rotate(progress * 90f)
                )
            }
        }
    }
}

@Composable
private fun MorphSlot(
    rect: LandscapeMorphRect,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .offset(x = rect.left, y = rect.top)
            .size(
                width = rect.width.coerceAtLeast(1.dp),
                height = rect.height.coerceAtLeast(1.dp)
            )
    ) {
        content()
    }
}

@Composable
private fun MorphingTrackMetadata(
    title: String,
    artist: String,
    progress: Float
) {
    val compactTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Bold
    )
    val fullscreenTitleStyle = MaterialTheme.typography.headlineMedium.copy(
        fontWeight = FontWeight.Bold
    )
    val compactArtistStyle = MaterialTheme.typography.bodySmall
    val fullscreenArtistStyle = MaterialTheme.typography.titleMedium
    val titleStyle = lerpTextStyle(compactTitleStyle, fullscreenTitleStyle, progress)
    val artistStyle = lerpTextStyle(compactArtistStyle, fullscreenArtistStyle, progress)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            style = titleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = artist,
            style = artistStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}

private fun lerpTextStyle(
    start: androidx.compose.ui.text.TextStyle,
    stop: androidx.compose.ui.text.TextStyle,
    fraction: Float
): androidx.compose.ui.text.TextStyle = start.copy(
    fontSize = lerp(start.fontSize, stop.fontSize, fraction),
    lineHeight = lerp(start.lineHeight, stop.lineHeight, fraction),
    letterSpacing = lerp(start.letterSpacing, stop.letterSpacing, fraction)
)

internal fun compactLandscapeMorphRects(
    compactSize: DpSize,
    showDifficultySelector: Boolean
): LandscapeMorphRects {
    val horizontalPadding = 8.dp
    val verticalPadding = 6.dp
    val bottomContentInset = 24.dp
    val actionSize = 36.dp
    // The collapse and fullscreen actions float over the artwork instead of reserving
    // an empty header row above it.
    val headerHeight = 0.dp
    val headerGap = 0.dp
    val artworkGap = 4.dp
    val metadataHeight = 42.dp
    val sectionGap = 2.dp
    val progressHeight = 42.dp
    val controlsGap = 4.dp
    val controlsHeight = 44.dp
    val selectorHeight = 28.dp
    val selectorGap = 4.dp
    val toggleSize = 34.dp
    val contentWidth = (compactSize.width - horizontalPadding * 2f).coerceAtLeast(1.dp)
    val contentLeft = horizontalPadding
    val contentRight = contentLeft + contentWidth
    val contentTop = verticalPadding
    // The top controls now float over the artwork. Reclaim the removed header space
    // below the transport row so the metadata and controls move up with the cover.
    val contentBottom = (
        compactSize.height - verticalPadding - bottomContentInset
        ).coerceAtLeast(1.dp)

    // Follow the vertical player from top to bottom: collapse control, artwork,
    // metadata, progress, then transport. The fixed rows are sized for the compact
    // card, while artwork takes all remaining height and keeps the renderer aspect
    // ratio instead of being squeezed to a single-digit-height slot.
    val controlsTop = (contentBottom - controlsHeight).coerceAtLeast(contentTop + 1.dp)
    val progressTop = (
        controlsTop - controlsGap - progressHeight
        ).coerceAtLeast(contentTop + 1.dp)
    val trackTop = (
        progressTop - sectionGap - metadataHeight
        ).coerceAtLeast(contentTop + headerHeight + headerGap + 1.dp)
    // Keep equal breathing room above and below the artwork between the floating
    // top actions and the metadata row.
    val artworkSlotTop = contentTop + actionSize + artworkGap / 2f
    val artworkSlotBottom =
        (trackTop - artworkGap / 2f).coerceAtLeast(artworkSlotTop)
    val artworkSlotHeight = artworkSlotBottom - artworkSlotTop
    val artworkWidth = (
        artworkSlotHeight * PREVIEW_ASPECT_RATIO
        ).coerceAtMost(contentWidth).coerceAtLeast(1.dp)
    val artworkHeight = artworkWidth / PREVIEW_ASPECT_RATIO
    val artworkLeft = (compactSize.width - artworkWidth) / 2f
    val artworkTop = artworkSlotTop + (artworkSlotHeight - artworkHeight) / 2f

    val selectorWidth = if (showDifficultySelector) {
        (contentWidth * 0.34f).coerceIn(54.dp, 76.dp)
    } else {
        0.dp
    }
    val difficultyRight = contentRight - toggleSize - selectorGap
    val difficultyLeft = (difficultyRight - selectorWidth).coerceAtLeast(contentLeft)
    // Match the centered progress bar's left edge so the two rows read as one column.
    val titleLeft = contentLeft + contentWidth * (
        1f - COMPACT_PROGRESS_WIDTH_FRACTION
        ) / 2f
    val titleWidth = (
        (
            if (showDifficultySelector) difficultyLeft - selectorGap
            else contentRight - toggleSize - selectorGap
            ) - titleLeft
        ).coerceAtLeast(1.dp)
    val collapseLeft = (compactSize.width - actionSize) / 2f

    return LandscapeMorphRects(
        artwork = LandscapeMorphRect(
            left = artworkLeft,
            top = artworkTop,
            width = artworkWidth,
            height = artworkHeight
        ),
        title = LandscapeMorphRect(
            left = titleLeft,
            top = trackTop,
            width = titleWidth,
            height = metadataHeight
        ),
        difficulty = LandscapeMorphRect(
            left = difficultyLeft,
            top = trackTop + (metadataHeight - selectorHeight) / 2f,
            width = selectorWidth,
            height = selectorHeight
        ),
        rendererToggle = LandscapeMorphRect(
            left = contentRight - toggleSize,
            top = trackTop + (metadataHeight - toggleSize) / 2f,
            width = toggleSize,
            height = toggleSize
        ),
        controls = LandscapeMorphRect(
            left = contentLeft,
            top = controlsTop,
            width = contentWidth,
            height = controlsHeight
        ),
        progress = LandscapeMorphRect(
            left = contentLeft,
            top = progressTop,
            width = contentWidth,
            height = progressHeight
        ),
        collapse = LandscapeMorphRect(
            left = collapseLeft,
            top = contentTop,
            width = actionSize,
            height = actionSize
        ),
        fullscreen = LandscapeMorphRect(
            left = contentRight - actionSize,
            top = contentTop,
            width = actionSize,
            height = actionSize
        )
    )
}

private fun fullscreenLandscapeMorphRects(
    size: DpSize,
    wideMode: Boolean,
    artworkAspectRatio: Float
): LandscapeMorphRects {
    val contentWidth = size.width.coerceAtMost(HorizontalMaxContentWidth)
    val contentLeft = (size.width - contentWidth) / 2f
    val innerLeft = contentLeft + 24.dp
    val innerRight = contentLeft + contentWidth - 24.dp
    val innerWidth = (innerRight - innerLeft).coerceAtLeast(1.dp)
    val innerTop = 12.dp
    val innerHeight = (size.height - 24.dp).coerceAtLeast(1.dp)
    val spacerWeightHeight = innerHeight * (H_CENTER_SPACER_WEIGHT / 1.1f)
    val rowTop = innerTop + spacerWeightHeight
    val rowHeight = if (wideMode) {
        innerHeight * (1f / 1.1f)
    } else {
        innerHeight * (TOP_BAND_WEIGHT / 1.1f)
    }
    val rowBottom = rowTop + rowHeight
    val calculatedArtworkWidth = rowHeight * artworkAspectRatio
    val artworkWidth = calculatedArtworkWidth.coerceAtMost(innerWidth)
    val artworkHeight = artworkWidth / artworkAspectRatio
    val artworkTop = rowTop + (rowHeight - artworkHeight) / 2f
    val artwork = LandscapeMorphRect(
        left = innerLeft,
        top = artworkTop,
        width = artworkWidth,
        height = artworkHeight
    )
    val infoLeft = artwork.left + artwork.width + 24.dp
    val infoWidth = (innerRight - infoLeft).coerceAtLeast(1.dp)
    val infoRight = infoLeft + infoWidth
    val controlsHeight = (infoWidth * H_PLAY_BUTTON_WIDTH_FRACTION).coerceAtLeast(1.dp)
    // The difficulty/renderer row sits above the single-line metadata block. The metadata
    // slot ends exactly HorizontalMetadataControlGap above the transport controls.
    val metadataToControlsGap = HorizontalMetadataControlGap
    val metadataHeight = 64.dp
    val actionRowHeight = 34.dp
    val actionRowGap = 6.dp
    val trackHeight = actionRowHeight + actionRowGap + metadataHeight
    val progressHeight = 52.dp
    val collapseSize = 48.dp
    val collapse = LandscapeMorphRect(
        left = infoLeft + (infoWidth - collapseSize) / 2f,
        top = rowTop,
        width = collapseSize,
        height = collapseSize
    )

    val controls: LandscapeMorphRect
    val progress: LandscapeMorphRect
    val track: LandscapeMorphRect
    if (wideMode) {
        progress = LandscapeMorphRect(
            left = infoLeft,
            top = rowBottom - progressHeight,
            width = infoWidth,
            height = progressHeight
        )
        controls = LandscapeMorphRect(
            left = infoLeft,
            top = progress.top - 20.dp - controlsHeight,
            width = infoWidth,
            height = controlsHeight
        )
        track = LandscapeMorphRect(
            left = infoLeft,
            top = controls.top - metadataToControlsGap - trackHeight,
            width = infoWidth,
            height = trackHeight
        )
    } else {
        controls = LandscapeMorphRect(
            left = infoLeft,
            top = rowBottom - controlsHeight,
            width = infoWidth,
            height = controlsHeight
        )
        track = LandscapeMorphRect(
            left = infoLeft,
            top = controls.top - metadataToControlsGap - trackHeight,
            width = infoWidth,
            height = trackHeight
        )
        val progressBandHeight = innerHeight * (PROGRESS_BAND_WEIGHT / 1.1f)
        progress = LandscapeMorphRect(
            left = innerLeft,
            top = rowBottom + progressBandHeight - progressHeight,
            width = innerWidth,
            height = progressHeight
        )
    }

    val selectorWidth = (infoWidth * 0.62f).coerceIn(120.dp, 196.dp)
    val toggleWidth = 34.dp
    val actionRowTop = track.top
    val metadataTop = actionRowTop + actionRowHeight + actionRowGap
    val toggleLeft = infoRight - toggleWidth
    val selectorRight = toggleLeft - 8.dp
    val selectorAvailableWidth = (selectorRight - infoLeft).coerceAtLeast(1.dp)
    val fittedSelectorWidth = selectorWidth.coerceAtMost(selectorAvailableWidth)
    val selectorLeft = selectorRight - fittedSelectorWidth
    return LandscapeMorphRects(
        artwork = artwork,
        title = LandscapeMorphRect(
            left = infoLeft,
            top = metadataTop,
            width = infoWidth,
            height = metadataHeight
        ),
        difficulty = LandscapeMorphRect(
            left = selectorLeft,
            top = actionRowTop + (actionRowHeight - 32.dp) / 2f,
            width = fittedSelectorWidth,
            height = 32.dp
        ),
        rendererToggle = LandscapeMorphRect(
            left = toggleLeft,
            top = actionRowTop + (actionRowHeight - toggleWidth) / 2f,
            width = toggleWidth,
            height = toggleWidth
        ),
        controls = controls,
        progress = progress,
        collapse = collapse,
        fullscreen = collapse
    )
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
    previewUnavailable: Boolean,
    previewData: OsuPreviewData?,
    skin: OsuSkin?,
    onRendererEnabledChange: (Boolean) -> Unit,
    onDifficultySelected: (String) -> Unit,
    wideMode: Boolean,
    modifier: Modifier = Modifier,
    collapseDescription: String = "Collapse"
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


            // Keep the artwork slot measured independently of its content so renderer
            // loading cannot stretch the controls. Album view remains square, while the
            // renderer preview uses its wider playfield aspect ratio.
            val artworkAspectRatio = if (rendererEnabled && !previewUnavailable) {
                PREVIEW_ASPECT_RATIO
            } else {
                1f
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(artworkAspectRatio),
                contentAlignment = Alignment.Center
            ) {
                PlayerArtwork(
                    artworkUri = artworkUri,
                    timeline = previewData?.timeline,
                    skin = skin,
                    musicController = musicController,
                    isExpanded = isExpanded,
                    currentMediaId = currentMediaId,
                    landscape = true,
                    rendererEnabled = rendererEnabled,
                    previewUnavailable = previewUnavailable
                )
            }
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
                        contentDescription = collapseDescription,
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
                        starRatings = previewData?.starRatings ?: BeatmapStarRatingCatalog.Empty,
                        onRendererEnabledChange = onRendererEnabledChange,
                        onDifficultySelected = onDifficultySelected,
                        selectorAboveTitle = true
                    )
                    Spacer(modifier = Modifier.height(HorizontalMetadataControlGap))
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
                        starRatings = previewData?.starRatings ?: BeatmapStarRatingCatalog.Empty,
                        onRendererEnabledChange = onRendererEnabledChange,
                        onDifficultySelected = onDifficultySelected,
                        selectorAboveTitle = true
                    )
                    Spacer(modifier = Modifier.height(HorizontalMetadataControlGap))
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
    currentMediaId: Long?,
    landscape: Boolean,
    rendererEnabled: Boolean,
    previewUnavailable: Boolean,
    fillSlot: Boolean = false,
    innerFeather: Dp = 0.dp
) {
    // FullPlayer stays composed behind the library so the sheet can animate,
    // but nothing in its artwork slot should draw or drive frames while the
    // sheet is fully collapsed.
    if (!isExpanded) return
    if (previewUnavailable) {
        CoverArt(
            artworkUri = artworkUri,
            modifier = if (landscape) Modifier.fillMaxHeight() else Modifier
        )
        return
    }

    val context = LocalContext.current
    // A nullable sentinel distinguishes "not sampled yet" from a genuine fallback.
    // The outer layout already reserves the slot, so it can stay visually empty until
    // the real shadow colour is ready instead of popping from dark to sampled.
    var shadowColor by remember(artworkUri) {
        mutableStateOf<Color?>(null)
    }
    LaunchedEffect(artworkUri) {
        shadowColor = if (artworkUri == null) {
            DefaultPreviewShadowColor
        } else {
            loadDominantShadowColor(context, artworkUri) ?: DefaultPreviewShadowColor
        }
    }

    val sizeModifier = when {
        fillSlot -> Modifier.fillMaxSize()
        landscape -> Modifier
            .fillMaxHeight()
            .aspectRatio(PREVIEW_ASPECT_RATIO)
        else -> Modifier
            .fillMaxWidth()
            .aspectRatio(PREVIEW_ASPECT_RATIO)
    }
    val sampledShadowColor = shadowColor ?: return

    val readyTimeline = timeline
    val readySkin = skin
    if (!rendererEnabled || readyTimeline == null || readySkin == null) {
        // The raised renderer surface is a stable loading placeholder. It deliberately
        // contains no artwork, avoiding the old cover flash without making the row reflow.
        Box(modifier = sizeModifier.rendererPreviewFrame(sampledShadowColor))
        return
    }

    DisposableEffect(musicController) {
        musicController.startPositionSampling()
        onDispose {
            musicController.stopPositionSampling()
        }
    }

    var mapTimeMs by remember(readyTimeline, currentMediaId) {
        mutableDoubleStateOf(musicController.currentPositionMs().toDouble())
    }
    LaunchedEffect(isExpanded, readyTimeline, currentMediaId) {
        if (!isExpanded) return@LaunchedEffect
        while (isActive) {
            withFrameNanos {
                // This is the renderer's single source of time. No interpolation:
                // playback, pause, resume, seeks, and DT/NC all arrive through the
                // same Media3 playback clock.
                mapTimeMs = musicController.currentPositionMs().toDouble()
            }
        }
    }

    val renderer = remember(readySkin) { OsuRenderer(readySkin) }

    Box(
        modifier = sizeModifier.rendererPreviewFrame(sampledShadowColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            renderer.draw(
                canvas = drawContext.canvas.nativeCanvas,
                timeline = readyTimeline,
                mapTimeMs = mapTimeMs,
                playfieldSize = OsuPlayfieldSize(size.width, size.height),
                edgeInsetPx = PreviewEdgeInset.toPx()
            )
            drawRendererInnerFeather(innerFeather)
        }
    }
}

private fun DrawScope.drawRendererInnerFeather(feather: Dp) {
    val featherPx = feather.toPx()
    if (featherPx <= 0f) return

    val edgeWidth = featherPx.coerceAtMost(size.minDimension / 2f)
    // Fade the canvas itself toward transparency at its outer boundary. DstIn
    // multiplies the existing surface alpha, so this feathers the rendered canvas
    // inward without painting a translucent strip over the game content. Split the
    // mask into straight edges and quarter-circle corners so its distance follows
    // the same rounded silhouette as the canvas instead of producing triangular
    // corner gradients.
    val cornerRadius = PreviewCornerRadius.toPx()
        .coerceAtMost(size.minDimension / 2f)
    val edgeMask = Color.White.copy(alpha = PREVIEW_CANVAS_EDGE_OPACITY)
    val opaqueMask = Color.White
    fun maskAtDistance(distancePx: Float): Color = Color.White.copy(
        alpha = PREVIEW_CANVAS_EDGE_OPACITY +
            (1f - PREVIEW_CANVAS_EDGE_OPACITY) *
            (distancePx / edgeWidth).coerceIn(0f, 1f)
    )

    val straightWidth = (size.width - cornerRadius * 2f).coerceAtLeast(0f)
    val straightHeight = (size.height - cornerRadius * 2f).coerceAtLeast(0f)

    if (straightWidth > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(edgeMask, opaqueMask),
                startY = 0f,
                endY = edgeWidth
            ),
            topLeft = Offset(cornerRadius, 0f),
            size = Size(straightWidth, edgeWidth),
            blendMode = BlendMode.DstIn
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(opaqueMask, edgeMask),
                startY = size.height - edgeWidth,
                endY = size.height
            ),
            topLeft = Offset(cornerRadius, size.height - edgeWidth),
            size = Size(straightWidth, edgeWidth),
            blendMode = BlendMode.DstIn
        )
    }

    if (straightHeight > 0f) {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(edgeMask, opaqueMask),
                startX = 0f,
                endX = edgeWidth
            ),
            topLeft = Offset(0f, cornerRadius),
            size = Size(edgeWidth, straightHeight),
            blendMode = BlendMode.DstIn
        )
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(opaqueMask, edgeMask),
                startX = size.width - edgeWidth,
                endX = size.width
            ),
            topLeft = Offset(size.width - edgeWidth, cornerRadius),
            size = Size(edgeWidth, straightHeight),
            blendMode = BlendMode.DstIn
        )
    }

    if (cornerRadius > 0f) {
        val innerRadius = (cornerRadius - edgeWidth).coerceAtLeast(0f)
        val innerStop = (innerRadius / cornerRadius).coerceIn(0f, 1f)
        fun cornerMask(center: Offset): Brush = if (innerRadius > 0f) {
            Brush.radialGradient(
                colorStops = arrayOf(
                    0f to opaqueMask,
                    innerStop to opaqueMask,
                    1f to edgeMask
                ),
                center = center,
                radius = cornerRadius
            )
        } else {
            Brush.radialGradient(
                colors = listOf(maskAtDistance(cornerRadius), edgeMask),
                center = center,
                radius = cornerRadius
            )
        }

        val cornerSize = Size(cornerRadius, cornerRadius)
        drawRect(
            brush = cornerMask(Offset(cornerRadius, cornerRadius)),
            topLeft = Offset.Zero,
            size = cornerSize,
            blendMode = BlendMode.DstIn
        )
        drawRect(
            brush = cornerMask(Offset(size.width - cornerRadius, cornerRadius)),
            topLeft = Offset(size.width - cornerRadius, 0f),
            size = cornerSize,
            blendMode = BlendMode.DstIn
        )
        drawRect(
            brush = cornerMask(Offset(cornerRadius, size.height - cornerRadius)),
            topLeft = Offset(0f, size.height - cornerRadius),
            size = cornerSize,
            blendMode = BlendMode.DstIn
        )
        drawRect(
            brush = cornerMask(
                Offset(size.width - cornerRadius, size.height - cornerRadius)
            ),
            topLeft = Offset(size.width - cornerRadius, size.height - cornerRadius),
            size = cornerSize,
            blendMode = BlendMode.DstIn
        )
    }
}

private fun Modifier.rendererPreviewFrame(shadowColor: Color): Modifier = this
    .drawWithCache {
        // Keep the canvas at its original size and paint a directional shadow
        // outside it. The shadow is offset down and fades in from the top, so
        // the canvas reads as raised by light coming from above the phone.
        // The software bitmap is important: BlurMaskFilter is not reliably
        // honored by the hardware canvas used by the renderer.
        val coreWidth = size.width.toInt().coerceAtLeast(1)
        val coreHeight = size.height.toInt().coerceAtLeast(1)
        val featherPx = PreviewFeatherWidth.toPx()
        val blurRadiusPx = featherPx / 2f
        val shadowOffsetYPx = PreviewShadowOffsetY.toPx()
        val haloWidth = coreWidth + (featherPx * 2f).toInt()
        val haloHeight = coreHeight + (featherPx * 2f + shadowOffsetYPx).toInt()
        val coreBounds = RectF(
            featherPx - blurRadiusPx,
            featherPx + shadowOffsetYPx - blurRadiusPx,
            featherPx + coreWidth + blurRadiusPx,
            featherPx + coreHeight + shadowOffsetYPx + blurRadiusPx
        )
        val haloBitmap = Bitmap.createBitmap(
            haloWidth,
            haloHeight,
            Bitmap.Config.ARGB_8888
        )
        android.graphics.Canvas(haloBitmap).drawRoundRect(
            coreBounds,
            PreviewCornerRadius.toPx() + blurRadiusPx,
            PreviewCornerRadius.toPx() + blurRadiusPx,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = shadowColor.toArgb()
                maskFilter = BlurMaskFilter(
                    blurRadiusPx,
                    BlurMaskFilter.Blur.NORMAL
                )
            }
        )
        // Suppress the shadow above the canvas, then strengthen it toward the
        // bottom. Reaching full strength before the bottom edge lets the
        // directional feather begin higher up the sides instead of appearing
        // only at the final few pixels. DST_IN keeps the blurred silhouette
        // while applying this vertical opacity ramp.
        val shadowRampStartYPx = featherPx
        val shadowRampEndYPx = featherPx + coreHeight * PREVIEW_SHADOW_RAMP_END
        android.graphics.Canvas(haloBitmap).drawRect(
            0f,
            0f,
            haloWidth.toFloat(),
            haloHeight.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    shadowRampStartYPx,
                    0f,
                    shadowRampEndYPx,
                    intArrayOf(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.argb(115, 0, 0, 0),
                        android.graphics.Color.BLACK
                    ),
                    floatArrayOf(0f, 0.38f, 1f),
                    Shader.TileMode.CLAMP
                )
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
        )
        val halo = haloBitmap.asImageBitmap()
        val haloOffset = Offset(-featherPx, -featherPx)
        onDrawBehind {
            drawImage(
                image = halo,
                topLeft = haloOffset,
                alpha = PREVIEW_SHADOW_ALPHA
            )
        }
    }
    .graphicsLayer {
        alpha = PREVIEW_CANVAS_ALPHA
        compositingStrategy = CompositingStrategy.Offscreen
    }
    .clip(RoundedCornerShape(PreviewCornerRadius))
    .background(DefaultPreviewShadowColor)

@Composable
private fun TrackInfo(
    title: String,
    artist: String,
    rendererEnabled: Boolean,
    selectedDifficulty: String?,
    difficulties: List<OsuDifficultyMap>,
    starRatings: BeatmapStarRatingCatalog,
    onRendererEnabledChange: (Boolean) -> Unit,
    onDifficultySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    titleMaxLines: Int = 1,
    selectorAboveTitle: Boolean = false,
    compact: Boolean = false
) {
    val showDifficultySelector = rendererEnabled && difficulties.size > 1
    val selectedDifficultyName = selectedDifficulty ?: difficulties.firstOrNull()?.difficultyName

    if (selectorAboveTitle) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showDifficultySelector && selectedDifficultyName != null) {
                    DifficultySelector(
                        selectedDifficulty = selectedDifficultyName,
                        difficulties = difficulties,
                        starRatings = starRatings,
                        onDifficultySelected = onDifficultySelected
                    )
                    Spacer(Modifier.width(8.dp))
                }
                RendererToggle(
                    enabled = rendererEnabled,
                    onEnabledChange = onRendererEnabledChange
                )
            }
            TrackMetadata(
                title = title,
                artist = artist,
                titleMaxLines = titleMaxLines,
                compact = compact,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackMetadata(
                title = title,
                artist = artist,
                titleMaxLines = titleMaxLines,
                compact = compact,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showDifficultySelector && selectedDifficultyName != null) {
                    DifficultySelector(
                        selectedDifficulty = selectedDifficultyName,
                        difficulties = difficulties,
                        starRatings = starRatings,
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
}

@Composable
private fun TrackMetadata(
    title: String,
    artist: String,
    titleMaxLines: Int,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            style = (if (compact) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.headlineMedium).copy(fontWeight = FontWeight.Bold),
            maxLines = titleMaxLines,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = artist,
            style = if (compact) MaterialTheme.typography.bodySmall
                else MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DifficultySelector(
    selectedDifficulty: String,
    difficulties: List<OsuDifficultyMap>,
    starRatings: BeatmapStarRatingCatalog,
    onDifficultySelected: (String) -> Unit,
    compact: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val sortedDifficulties = remember(difficulties, starRatings) {
        difficulties.sortedWith(
            compareBy<OsuDifficultyMap> { difficulty ->
                starRatings
                    .ratingFor(difficulty)
                    ?.takeIf(Float::isFinite)
                    ?: Float.POSITIVE_INFINITY
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.difficultyName }
        )
    }
    val selectedRating = difficulties
        .firstOrNull { it.difficultyName == selectedDifficulty }
        ?.let { difficulty -> starRatings.ratingFor(difficulty) }
    Box {
        Row(
            modifier = Modifier
                .widthIn(max = 196.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(
                    start = if (compact) 2.dp else 8.dp,
                    end = if (compact) 0.dp else 2.dp,
                    top = 5.dp,
                    bottom = 5.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StarRatingBadge(selectedRating)
            if (compact) {
                Spacer(Modifier.width(2.dp))
            } else {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = selectedDifficulty,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.player_select_difficulty),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.size(if (compact) 12.dp else 20.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            sortedDifficulties.forEach { difficulty ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StarRatingBadge(
                                starRatings.ratingFor(difficulty)
                            )
                            Text(
                                text = difficulty.difficultyName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
    modifier: Modifier = Modifier,
    morphProgress: Float? = null
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
        val sideButtonSize = if (morphProgress != null) {
            lerp(
                maxHeight * SIDE_BUTTON_HEIGHT_FRACTION,
                maxWidth * H_SIDE_BUTTON_WIDTH_FRACTION,
                morphProgress.coerceIn(0f, 1f)
            )
        } else if (sizeByWidth) {
            maxWidth * H_SIDE_BUTTON_WIDTH_FRACTION
        } else {
            maxHeight * SIDE_BUTTON_HEIGHT_FRACTION
        }
        val playButtonSize = if (morphProgress != null) {
            lerp(
                maxHeight * PLAY_BUTTON_HEIGHT_FRACTION,
                maxWidth * H_PLAY_BUTTON_WIDTH_FRACTION,
                morphProgress.coerceIn(0f, 1f)
            )
        } else if (sizeByWidth) {
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
    duration: Long,
    sliderHeight: Dp = 36.dp,
    widthFraction: Float = 0.97f,
    trackBoundsAlignment: Float = 0f
) {
    val currentPosition by musicController.currentPosition.collectAsState()
    var isDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    val sliderRange = 0f..duration.toFloat().coerceAtLeast(1f)
    val sliderInteraction = remember { MutableInteractionSource() }
    val alignedTrackHorizontalExpansion = with(LocalDensity.current) {
        val thumbSize = sliderHeight * (28f / 36f)
        (thumbSize / 2f - 5.dp).coerceAtLeast(0.dp).toPx()
    }
    val trackHorizontalExpansion = 50f +
        (alignedTrackHorizontalExpansion - 50f) * trackBoundsAlignment.coerceIn(0f, 1f)

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
        modifier = Modifier.fillMaxWidth(widthFraction.coerceIn(0f, 1f)),
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
            modifier = Modifier.height(sliderHeight),
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
                Box(modifier = Modifier.size(sliderHeight * (28f / 36f)))
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
                    horizontalExpansion = trackHorizontalExpansion
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
