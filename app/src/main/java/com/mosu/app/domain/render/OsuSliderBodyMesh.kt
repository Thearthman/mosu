package com.mosu.app.domain.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.roundToInt

/**
 * Draws a slider body as a union of round-capped centreline strokes.
 *
 * This follows the approach used by danser-go's slider renderer: the complete
 * body is rasterised as one rounded path first, rather than as independent
 * quads and corner wedges. Skia's path stroker then produces one continuous
 * contour at inner and outer corners, including at joins between different
 * curve types. The legacy shadow/border/accent cross-section is rendered by
 * drawing progressively narrower strokes into the same layer, outer to inner,
 * with source replacement.
 *
 * The [Path] and [Paint] instances are retained and reused to keep the frame
 * path allocation-free.
 */
internal class OsuSliderBodyMesh {
    private val centreline = Path()
    private val sourceXfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    private val layerPaint = Paint()

    private var lastX = 0f
    private var lastY = 0f
    private var boundsLeft = 0f
    private var boundsTop = 0f
    private var boundsRight = 0f
    private var boundsBottom = 0f

    fun draw(
        canvas: Canvas,
        paint: Paint,
        path: OsuSliderPath,
        trackedLength: Float,
        offset: RenderPoint,
        objectScale: Float,
        trackColour: Int,
        borderColour: Int,
        alpha: Float
    ): Boolean {
        if (trackedLength <= PATH_EPSILON || objectScale <= 0f || alpha <= 0f) return false
        if (!buildCentreline(path, trackedLength, offset)) return false

        val radius = OsuRenderObject.OSU_OBJECT_RADIUS * objectScale
        val previousStyle = paint.style
        val previousCap = paint.strokeCap
        val previousJoin = paint.strokeJoin
        val previousXfermode = paint.xfermode

        layerPaint.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        val layer = canvas.saveLayer(
            boundsLeft - radius - LAYER_PADDING,
            boundsTop - radius - LAYER_PADDING,
            boundsRight + radius + LAYER_PADDING,
            boundsBottom + radius + LAYER_PADDING,
            layerPaint
        )

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.xfermode = sourceXfermode

        // The shadow fades from transparent at the outer edge to black at the
        // legacy 59/64 radius boundary. Each narrower stroke replaces the
        // centre of the previous one, so self-overlaps never accumulate alpha.
        for (sample in 1..SHADOW_SAMPLES) {
            val position = OsuLegacySkinMetrics.SLIDER_SHADOW_PORTION *
                sample.toFloat() / SHADOW_SAMPLES
            drawBand(
                canvas = canvas,
                paint = paint,
                radius = radius,
                position = position,
                colour = OsuLegacySkinMetrics.sliderCrossSectionColour(
                    trackColour = trackColour,
                    borderColour = borderColour,
                    position = position
                )
            )
        }

        drawBand(
            canvas = canvas,
            paint = paint,
            radius = radius,
            position = OsuLegacySkinMetrics.SLIDER_SHADOW_PORTION,
            colour = borderColour
        )

        for (sample in 0 until BODY_SAMPLES) {
            val fraction = sample.toFloat() / BODY_SAMPLES
            val position = OsuLegacySkinMetrics.SLIDER_BORDER_PORTION +
                (1f - OsuLegacySkinMetrics.SLIDER_BORDER_PORTION) * fraction
            drawBand(
                canvas = canvas,
                paint = paint,
                radius = radius,
                position = position,
                colour = OsuLegacySkinMetrics.sliderBodyAccentColour(
                    trackColour = trackColour,
                    position = fraction
                )
            )
        }

        paint.style = previousStyle
        paint.strokeCap = previousCap
        paint.strokeJoin = previousJoin
        paint.xfermode = previousXfermode
        canvas.restoreToCount(layer)
        return true
    }

    private fun buildCentreline(
        path: OsuSliderPath,
        trackedLength: Float,
        offset: RenderPoint
    ): Boolean {
        centreline.reset()
        if (path.points.isEmpty() || path.length <= PATH_EPSILON) return false

        val limit = trackedLength.coerceIn(0f, path.length)
        if (limit <= PATH_EPSILON) return false

        val first = path.points.first()
        lastX = first.x + offset.x
        lastY = first.y + offset.y
        centreline.moveTo(lastX, lastY)
        boundsLeft = lastX
        boundsTop = lastY
        boundsRight = lastX
        boundsBottom = lastY

        var pointCount = 1
        for (index in 1 until path.points.size) {
            if (path.distances[index] > limit) {
                val end = path.positionAtDistance(limit)
                if (appendPoint(end.x + offset.x, end.y + offset.y)) pointCount++
                break
            }

            val point = path.points[index]
            if (appendPoint(point.x + offset.x, point.y + offset.y)) pointCount++
        }
        return pointCount >= 2
    }

    private fun appendPoint(x: Float, y: Float): Boolean {
        val dx = x - lastX
        val dy = y - lastY
        if (dx * dx + dy * dy <= PATH_EPSILON * PATH_EPSILON) return false

        centreline.lineTo(x, y)
        if (x < boundsLeft) boundsLeft = x
        if (y < boundsTop) boundsTop = y
        if (x > boundsRight) boundsRight = x
        if (y > boundsBottom) boundsBottom = y
        lastX = x
        lastY = y
        return true
    }

    private fun drawBand(
        canvas: Canvas,
        paint: Paint,
        radius: Float,
        position: Float,
        colour: Int
    ) {
        paint.strokeWidth = radius * 2f * (1f - position.coerceIn(0f, 1f))
        paint.color = colour
        canvas.drawPath(centreline, paint)
    }

    private companion object {
        const val SHADOW_SAMPLES = 4
        const val BODY_SAMPLES = 24
        const val PATH_EPSILON = 0.0001f
        const val LAYER_PADDING = 2f
    }
}
