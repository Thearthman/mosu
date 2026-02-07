package com.mosu.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun Track(
    activeRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    activeTrackColor: Color,
    inactiveTrackColor: Color,
    activeTickColor: Color = activeTrackColor,
    inactiveTickColor: Color = inactiveTrackColor,
    tickFractions: List<Float> = emptyList(),
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
            sliderStart.x + (sliderEnd.x - sliderStart.x) * activeRange.endInclusive,
            center.y
        )
        val sliderValueStart = Offset(
            sliderStart.x + (sliderEnd.x - sliderStart.x) * activeRange.start,
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
        if (tickFractions.isNotEmpty()) {
            tickFractions.groupBy { fraction ->
                fraction > activeRange.endInclusive ||
                        fraction < activeRange.start
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

@Composable
fun DraggableScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // Use a Channel to throttle/latest-only scroll requests for smoothness
    val scrollChannel = remember { Channel<Pair<Int, Int>>(Channel.CONFLATED) }
    
    LaunchedEffect(Unit) {
        scrollChannel.receiveAsFlow().collectLatest { (index, offset) ->
            state.scrollToItem(index, offset)
        }
    }

    val scrollbarInfo by remember {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            
            if (visibleItemsInfo.isEmpty() || totalItemsCount == 0) {
                return@derivedStateOf null
            }

            val viewportHeight = layoutInfo.viewportSize.height.toFloat()
            val unifiedItemHeightPx = with(density) { 60.dp.toPx() }
            
            // Calculate how many items "fit" in the viewport once
            val estimatedVisibleItems = viewportHeight / unifiedItemHeightPx
            
            // Slider height: ratio of estimated visible items to total items
            val sliderHeight = (viewportHeight * (estimatedVisibleItems / totalItemsCount.toFloat()))
                .coerceIn(with(density) { 40.dp.toPx() }, viewportHeight)

            if (sliderHeight >= viewportHeight) return@derivedStateOf null

            // Progress: ratio of first visible item index to total items
            val firstItem = visibleItemsInfo.first()
            val firstItemIndex = firstItem.index
            val firstItemOffset = -firstItem.offset.toFloat()
            
            val currentDecimalIndex = firstItemIndex + (firstItemOffset / unifiedItemHeightPx)
            val progress = (currentDecimalIndex / totalItemsCount).coerceIn(0f, 1f)
            
            val maxScrollbarOffset = viewportHeight - sliderHeight
            val scrollbarOffset = progress * maxScrollbarOffset

            ScrollbarInfo(
                sliderHeight = sliderHeight,
                scrollbarOffset = scrollbarOffset,
                totalItemsCount = totalItemsCount,
                maxScrollbarOffset = maxScrollbarOffset,
                itemSizePx = unifiedItemHeightPx
            )
        }
    }

    scrollbarInfo?.let { info ->
        // Sync scrollbar position when not dragging
        LaunchedEffect(info.scrollbarOffset, isDragging) {
            if (!isDragging) {
                dragOffset = info.scrollbarOffset
            }
        }

        Box(
            modifier = modifier
                .fillMaxHeight()
                .width(30.dp)
                .draggable(
                    orientation = Orientation.Vertical,
                    onDragStarted = { isDragging = true },
                    onDragStopped = { isDragging = false },
                    state = rememberDraggableState { delta ->
                        if (info.maxScrollbarOffset > 0) {
                            dragOffset = (dragOffset + delta).coerceIn(0f, info.maxScrollbarOffset)
                            val newProgress = dragOffset / info.maxScrollbarOffset
                            
                            // Map progress to index and offset
                            val targetDecimalIndex = newProgress * info.totalItemsCount
                            val targetIndex = targetDecimalIndex.toInt().coerceIn(0, info.totalItemsCount - 1)
                            val fractionalPart = targetDecimalIndex - targetIndex
                            val targetItemOffset = (fractionalPart * info.itemSizePx).roundToInt()
                            
                            scrollChannel.trySend(targetIndex to targetItemOffset)
                        }
                    }
                )
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .align(Alignment.TopCenter)
                    .graphicsLayer { translationY = dragOffset }
                    .width(4.dp)
                    .height(with(density) { info.sliderHeight.toDp() })
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (isDragging) 0.6f else 0.3f
                        )
                    )
            )
        }
    }
}

private data class ScrollbarInfo(
    val sliderHeight: Float,
    val scrollbarOffset: Float,
    val totalItemsCount: Int,
    val maxScrollbarOffset: Float,
    val itemSizePx: Float
)
