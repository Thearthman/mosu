package com.mosu.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

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
