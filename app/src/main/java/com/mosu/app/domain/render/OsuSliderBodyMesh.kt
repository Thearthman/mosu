package com.mosu.app.domain.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Builds the same rounded ribbon geometry as osu.Framework's path draw node:
 * one quad per sampled centreline segment, wedges at corners, and semicircular
 * end caps. Vertex colours sample the legacy slider body's 1D cross-section
 * (shadow, border, accent gradient) directly, so no stacked strokes or seams
 * are introduced.
 *
 * All buffers are owned by this instance and reused across frames.
 */
internal class OsuSliderBodyMesh {
    private var centrelineX = FloatArray(INITIAL_CENTRELINE_CAPACITY)
    private var centrelineY = FloatArray(INITIAL_CENTRELINE_CAPACITY)
    private var centrelineCount = 0

    private var positions = FloatArray(INITIAL_VERTEX_CAPACITY * 2)
    private var colours = IntArray(INITIAL_VERTEX_CAPACITY)
    private var vertexCount = 0
    private var boundsLeft = 0f
    private var boundsTop = 0f
    private var boundsRight = 0f
    private var boundsBottom = 0f
    private val sourceXfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)

    private val bandStarts = floatArrayOf(
        0f,
        OsuLegacySkinMetrics.SLIDER_SHADOW_PORTION,
        OsuLegacySkinMetrics.SLIDER_BORDER_PORTION
    )
    private val bandEnds = floatArrayOf(
        OsuLegacySkinMetrics.SLIDER_SHADOW_PORTION,
        OsuLegacySkinMetrics.SLIDER_BORDER_PORTION,
        1f
    )
    private val bandStartColours = IntArray(SLIDER_BANDS)
    private val bandEndColours = IntArray(SLIDER_BANDS)

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
        if (!collectCentreline(path, trackedLength, offset)) return false

        vertexCount = 0
        boundsLeft = Float.POSITIVE_INFINITY
        boundsTop = Float.POSITIVE_INFINITY
        boundsRight = Float.NEGATIVE_INFINITY
        boundsBottom = Float.NEGATIVE_INFINITY
        prepareBandColours(trackColour, borderColour, alpha)

        val radius = OsuRenderObject.OSU_OBJECT_RADIUS * objectScale
        // Draw the complete outer band before moving inwards. This keeps the
        // cross-section correctly layered where two parts of a curve overlap.
        for (band in 0 until SLIDER_BANDS) {
            addSegmentQuads(radius, band)
            addJoinWedges(radius, band)
            addEndCaps(radius, band)
        }

        if (vertexCount == 0) return false
        // Path_DrawNode disables blending so semi-transparent join wedges and
        // self-overlaps do not accumulate opacity. Android has no depth buffer
        // here, so render the ribbon into one layer with source replacement and
        // composite that finished layer over the playfield.
        val layer = canvas.saveLayer(
            boundsLeft - LAYER_PADDING,
            boundsTop - LAYER_PADDING,
            boundsRight + LAYER_PADDING,
            boundsBottom + LAYER_PADDING,
            null
        )
        val previousXfermode = paint.xfermode
        paint.xfermode = sourceXfermode
        canvas.drawVertices(
            Canvas.VertexMode.TRIANGLES,
            vertexCount,
            positions,
            0,
            null,
            0,
            colours,
            0,
            null,
            0,
            0,
            paint
        )
        paint.xfermode = previousXfermode
        canvas.restoreToCount(layer)
        return true
    }

    private fun collectCentreline(
        path: OsuSliderPath,
        trackedLength: Float,
        offset: RenderPoint
    ): Boolean {
        centrelineCount = 0
        if (path.points.isEmpty() || path.length <= PATH_EPSILON) return false

        val limit = trackedLength.coerceIn(0f, path.length)
        if (limit <= PATH_EPSILON) return false

        val first = path.points.first()
        appendCentreline(first.x + offset.x, first.y + offset.y)

        for (index in 1 until path.points.size) {
            if (path.distances[index] > limit) {
                val end = path.positionAtDistance(limit)
                appendCentreline(end.x + offset.x, end.y + offset.y)
                break
            }

            val point = path.points[index]
            appendCentreline(point.x + offset.x, point.y + offset.y)
        }
        return centrelineCount >= 2
    }

    private fun appendCentreline(x: Float, y: Float) {
        if (centrelineCount > 0) {
            val dx = x - centrelineX[centrelineCount - 1]
            val dy = y - centrelineY[centrelineCount - 1]
            if (dx * dx + dy * dy <= PATH_EPSILON * PATH_EPSILON) return
        }
        ensureCentrelineCapacity(centrelineCount + 1)
        centrelineX[centrelineCount] = x
        centrelineY[centrelineCount] = y
        centrelineCount++
    }

    private fun prepareBandColours(
        trackColour: Int,
        borderColour: Int,
        alpha: Float
    ) {
        // `ColourAt()` is discontinuous at both inner boundaries: the shadow
        // ends before the border begins, and the border ends before the body
        // accent begins. Sampling the shared band boundary with the ordinary
        // <= comparisons would smear the previous band one segment inwards.
        bandStartColours[0] = OsuLegacySkinMetrics.sliderCrossSectionColour(
            trackColour = trackColour,
            borderColour = borderColour,
            position = 0f,
            opacity = alpha
        )
        bandEndColours[0] = OsuLegacySkinMetrics.sliderCrossSectionColour(
            trackColour = trackColour,
            borderColour = borderColour,
            position = OsuLegacySkinMetrics.SLIDER_SHADOW_PORTION,
            opacity = alpha
        )

        val border = OsuLegacySkinMetrics.withAlphaMultiplied(borderColour, alpha)
        bandStartColours[1] = border
        bandEndColours[1] = border

        bandStartColours[2] = OsuLegacySkinMetrics.sliderBodyAccentColour(
            trackColour = trackColour,
            position = 0f,
            opacity = alpha
        )
        bandEndColours[2] = OsuLegacySkinMetrics.sliderBodyAccentColour(
            trackColour = trackColour,
            position = 1f,
            opacity = alpha
        )
    }

    private fun addSegmentQuads(radius: Float, band: Int) {
        for (index in 0 until centrelineCount - 1) {
            val x0 = centrelineX[index]
            val y0 = centrelineY[index]
            val dx = centrelineX[index + 1] - x0
            val dy = centrelineY[index + 1] - y0
            val length = sqrt(dx * dx + dy * dy)
            if (length <= PATH_EPSILON) continue

            // osu.Framework uses this orthogonal as its "right" edge. Drawing
            // both sides produces one closed ribbon from the centreline.
            val nx = dy / length
            val ny = -dx / length
            addSegmentSide(index, nx, ny, 1f, radius, band)
            addSegmentSide(index, nx, ny, -1f, radius, band)
        }
    }

    private fun addSegmentSide(
        segmentIndex: Int,
        nx: Float,
        ny: Float,
        side: Float,
        radius: Float,
        band: Int
    ) {
        val x0 = centrelineX[segmentIndex]
        val y0 = centrelineY[segmentIndex]
        val x1 = centrelineX[segmentIndex + 1]
        val y1 = centrelineY[segmentIndex + 1]

        val outerDistance = radius * (1f - bandStarts[band])
        val innerDistance = radius * (1f - bandEnds[band])
        val outerColour = bandStartColours[band]
        val innerColour = bandEndColours[band]

        val sxOuter = x0 + nx * side * outerDistance
        val syOuter = y0 + ny * side * outerDistance
        val exOuter = x1 + nx * side * outerDistance
        val eyOuter = y1 + ny * side * outerDistance
        val sxInner = x0 + nx * side * innerDistance
        val syInner = y0 + ny * side * innerDistance
        val exInner = x1 + nx * side * innerDistance
        val eyInner = y1 + ny * side * innerDistance

        addTriangle(
            sxOuter, syOuter, outerColour,
            exOuter, eyOuter, outerColour,
            exInner, eyInner, innerColour
        )
        addTriangle(
            sxOuter, syOuter, outerColour,
            exInner, eyInner, innerColour,
            sxInner, syInner, innerColour
        )
    }

    private fun addJoinWedges(radius: Float, band: Int) {
        for (index in 1 until centrelineCount - 1) {
            val prevDx = centrelineX[index] - centrelineX[index - 1]
            val prevDy = centrelineY[index] - centrelineY[index - 1]
            val nextDx = centrelineX[index + 1] - centrelineX[index]
            val nextDy = centrelineY[index + 1] - centrelineY[index]
            val prevLength = sqrt(prevDx * prevDx + prevDy * prevDy)
            val nextLength = sqrt(nextDx * nextDx + nextDy * nextDy)
            if (prevLength <= PATH_EPSILON || nextLength <= PATH_EPSILON) continue

            val prevNx = prevDy / prevLength
            val prevNy = -prevDx / prevLength
            val nextNx = nextDy / nextLength
            val nextNy = -nextDx / nextLength
            val cross = prevDx * nextDy - prevDy * nextDx
            if (abs(cross) <= PATH_EPSILON) continue

            // The outside of a corner is the edge towards which both segment
            // directions rotate. Filling only that side avoids alpha overdraw
            // on the inside of the corner.
            val side = if (cross > 0f) 1f else -1f
            val startAngle = atan2(prevNy * side, prevNx * side)
            val endAngle = atan2(nextNy * side, nextNx * side)
            val sweep = normaliseAngle(endAngle - startAngle)
            if (abs(sweep) <= ANGLE_EPSILON) continue

            val steps = maxOf(1, ceil(abs(sweep) / MAX_ANGLE_STEP).toInt())
            addRadialArc(
                centreX = centrelineX[index],
                centreY = centrelineY[index],
                startAngle = startAngle,
                sweep = sweep,
                steps = steps,
                outerDistance = radius * (1f - bandStarts[band]),
                innerDistance = radius * (1f - bandEnds[band]),
                outerColour = bandStartColours[band],
                innerColour = bandEndColours[band]
            )
        }
    }

    private fun addEndCaps(radius: Float, band: Int) {
        val startDx = centrelineX[1] - centrelineX[0]
        val startDy = centrelineY[1] - centrelineY[0]
        val startLength = sqrt(startDx * startDx + startDy * startDy)
        if (startLength > PATH_EPSILON) {
            val startAngle = atan2(-startDx / startLength, startDy / startLength)
            addCap(
                centreIndex = 0,
                startAngle = startAngle,
                sweep = -PI.toFloat(),
                radius = radius,
                band = band
            )
        }

        val last = centrelineCount - 1
        val endDx = centrelineX[last] - centrelineX[last - 1]
        val endDy = centrelineY[last] - centrelineY[last - 1]
        val endLength = sqrt(endDx * endDx + endDy * endDy)
        if (endLength > PATH_EPSILON) {
            val endAngle = atan2(-endDx / endLength, endDy / endLength)
            addCap(
                centreIndex = last,
                startAngle = endAngle,
                sweep = PI.toFloat(),
                radius = radius,
                band = band
            )
        }
    }

    private fun addCap(
        centreIndex: Int,
        startAngle: Float,
        sweep: Float,
        radius: Float,
        band: Int
    ) {
        addRadialArc(
            centreX = centrelineX[centreIndex],
            centreY = centrelineY[centreIndex],
            startAngle = startAngle,
            sweep = sweep,
            steps = CAP_STEPS,
            outerDistance = radius * (1f - bandStarts[band]),
            innerDistance = radius * (1f - bandEnds[band]),
            outerColour = bandStartColours[band],
            innerColour = bandEndColours[band]
        )
    }

    private fun addRadialArc(
        centreX: Float,
        centreY: Float,
        startAngle: Float,
        sweep: Float,
        steps: Int,
        outerDistance: Float,
        innerDistance: Float,
        outerColour: Int,
        innerColour: Int
    ) {
        var previousOuterX = centreX + cos(startAngle) * outerDistance
        var previousOuterY = centreY + sin(startAngle) * outerDistance
        var previousInnerX = centreX + cos(startAngle) * innerDistance
        var previousInnerY = centreY + sin(startAngle) * innerDistance

        for (step in 1..steps) {
            val fraction = step.toFloat() / steps
            val angle = startAngle + sweep * fraction
            val outerX = centreX + cos(angle) * outerDistance
            val outerY = centreY + sin(angle) * outerDistance
            val innerX = centreX + cos(angle) * innerDistance
            val innerY = centreY + sin(angle) * innerDistance

            addTriangle(
                previousOuterX, previousOuterY, outerColour,
                outerX, outerY, outerColour,
                innerX, innerY, innerColour
            )
            addTriangle(
                previousOuterX, previousOuterY, outerColour,
                innerX, innerY, innerColour,
                previousInnerX, previousInnerY, innerColour
            )

            previousOuterX = outerX
            previousOuterY = outerY
            previousInnerX = innerX
            previousInnerY = innerY
        }
    }

    private fun addTriangle(
        x0: Float,
        y0: Float,
        colour0: Int,
        x1: Float,
        y1: Float,
        colour1: Int,
        x2: Float,
        y2: Float,
        colour2: Int
    ) {
        ensureVertexCapacity(vertexCount + 3)
        addVertex(x0, y0, colour0)
        addVertex(x1, y1, colour1)
        addVertex(x2, y2, colour2)
    }

    private fun addVertex(x: Float, y: Float, colour: Int) {
        val positionOffset = vertexCount * 2
        positions[positionOffset] = x
        positions[positionOffset + 1] = y
        colours[vertexCount] = colour
        if (x < boundsLeft) boundsLeft = x
        if (y < boundsTop) boundsTop = y
        if (x > boundsRight) boundsRight = x
        if (y > boundsBottom) boundsBottom = y
        vertexCount++
    }

    private fun ensureCentrelineCapacity(required: Int) {
        if (required <= centrelineX.size) return
        val capacity = maxOf(required, centrelineX.size * 2)
        centrelineX = centrelineX.copyOf(capacity)
        centrelineY = centrelineY.copyOf(capacity)
    }

    private fun ensureVertexCapacity(required: Int) {
        if (required <= colours.size) return
        val capacity = maxOf(required, colours.size * 2)
        positions = positions.copyOf(capacity * 2)
        colours = colours.copyOf(capacity)
    }

    private fun normaliseAngle(angle: Float): Float {
        var result = angle
        while (result > PI) result -= (PI * 2.0).toFloat()
        while (result < -PI) result += (PI * 2.0).toFloat()
        return result
    }

    private companion object {
        const val SLIDER_BANDS = 3
        const val CAP_STEPS = 24
        const val INITIAL_CENTRELINE_CAPACITY = 256
        const val INITIAL_VERTEX_CAPACITY = 2_048
        const val PATH_EPSILON = 0.0001f
        const val ANGLE_EPSILON = 0.0001f
        const val LAYER_PADDING = 2f
        val MAX_ANGLE_STEP = (PI / 24.0).toFloat()
    }
}
