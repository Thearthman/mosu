package com.mosu.app.domain.render

import java.util.ArrayDeque
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure slider-path approximation matching osu!lazer's legacy slider pipeline
 * at commit 4e11ab19feb423c905cf98d9b8fa34700a8aeffa.
 */
object OsuSliderGeometry {
    // osu.Framework's PathApproximator constants.
    private const val BEZIER_TOLERANCE = 0.25f
    private const val CATMULL_DETAIL = 50
    private const val CIRCULAR_ARC_TOLERANCE = 0.1f
    private const val GEOMETRY_EPSILON = 0.0001f
    private const val PATH_JOIN_EPSILON = 0.001f

    /**
     * Compatibility entry point for a path containing one explicit segment.
     */
    fun build(
        start: RenderPoint,
        curveType: OsuCurveType,
        controlPoints: List<RenderPoint>,
        declaredLength: Float
    ): OsuSliderPath = build(
        pathControlPoints = convertLegacyPoints(
            points = listOf(start) + controlPoints,
            type = curveType
        ),
        declaredLength = declaredLength
    )

    /**
     * Builds an immutable path from typed control points. A non-null curve type
     * starts a new segment; the typed point is shared with the preceding
     * segment, exactly as in lazer's `SliderPath.calculatePath()`.
     */
    fun build(
        pathControlPoints: List<OsuPathControlPoint>,
        declaredLength: Float
    ): OsuSliderPath {
        val calculated = calculatePath(pathControlPoints)
        val expectedDistance = declaredLength.takeIf { it > 0f && it.isFinite() }
        return calculateLength(calculated, expectedDistance)
    }

    private fun calculatePath(
        controlPoints: List<OsuPathControlPoint>
    ): List<RenderPoint> {
        if (controlPoints.isEmpty()) return emptyList()

        val calculated = mutableListOf<RenderPoint>()
        var segmentStart = 0
        for (index in controlPoints.indices) {
            if (controlPoints[index].curveType == null && index < controlPoints.lastIndex) {
                continue
            }

            val segmentPoints = controlPoints
                .subList(segmentStart, index + 1)
                .map { it.position }
            val type = controlPoints[segmentStart].curveType ?: OsuCurveType.LINEAR
            val subPath = calculateSubPath(segmentPoints, type).toMutableList()

            // Circular arcs and Catmull-Rom samples are generated from
            // trigonometric/polynomial approximations and can miss their
            // declared knots by a few float ulps. Snap the endpoints before
            // joining so consecutive segments share an exact vertex.
            if (subPath.isNotEmpty()) {
                subPath[0] = segmentPoints.first()
                subPath[subPath.lastIndex] = segmentPoints.last()
            }

            // Explicit segments share their boundary vertex. Joining the
            // approximated sub-paths without duplicating that vertex is what
            // keeps the rendered body continuous at mixed-curve joins. An
            // epsilon comparison is required because the two sub-paths are
            // independently sampled and may differ by a few float ulps.
            val previousEnd = calculated.lastOrNull()
            val skipFirst = previousEnd != null &&
                subPath.isNotEmpty() &&
                previousEnd.distanceTo(subPath.first()) <= PATH_JOIN_EPSILON
            if (skipFirst) {
                subPath[0] = previousEnd!!
            }
            val firstSubIndex = if (skipFirst) 1 else 0
            for (subIndex in firstSubIndex until subPath.size) {
                calculated += subPath[subIndex]
            }
            segmentStart = index
        }
        return calculated
    }

    private fun calculateSubPath(
        points: List<RenderPoint>,
        type: OsuCurveType
    ): List<RenderPoint> = when (type) {
        OsuCurveType.LINEAR -> points
        OsuCurveType.BEZIER -> bezier(points)
        OsuCurveType.PERFECT_CIRCLE -> perfectCircle(points)
        OsuCurveType.CATMULL -> catmull(points)
    }

    private fun bezier(points: List<RenderPoint>): List<RenderPoint> {
        if (points.size < 2) return points

        val output = ArrayList<RenderPoint>(points.size * 4)
        val toFlatten = ArrayDeque<List<RenderPoint>>()
        toFlatten.addLast(points)

        while (toFlatten.isNotEmpty()) {
            val parent = toFlatten.removeLast()
            if (bezierIsFlatEnough(parent)) {
                appendBezierApproximation(parent, output)
                continue
            }

            val (left, right) = subdivideBezier(parent)
            // Stack order preserves the curve's direction.
            toFlatten.addLast(right)
            toFlatten.addLast(left)
        }

        output += points.last()
        return output
    }

    private fun bezierIsFlatEnough(points: List<RenderPoint>): Boolean {
        for (index in 1 until points.lastIndex) {
            val x = points[index - 1].x - 2f * points[index].x + points[index + 1].x
            val y = points[index - 1].y - 2f * points[index].y + points[index + 1].y
            if (x * x + y * y > BEZIER_TOLERANCE * BEZIER_TOLERANCE * 4f) {
                return false
            }
        }
        return true
    }

    private fun subdivideBezier(
        points: List<RenderPoint>
    ): Pair<List<RenderPoint>, List<RenderPoint>> {
        val left = MutableList(points.size) { points.first() }
        val right = MutableList(points.size) { points.last() }
        left[0] = points.first()
        right[points.lastIndex] = points.last()

        var row = points
        for (level in 1 until points.size) {
            val next = ArrayList<RenderPoint>(row.size - 1)
            for (index in 0 until row.lastIndex) {
                next += row[index].lerp(row[index + 1], 0.5f)
            }
            row = next
            left[level] = row.first()
            right[points.lastIndex - level] = row.last()
        }
        return left to right
    }

    /**
     * osu.Framework's De Casteljau-based optimal piecewise approximation for a
     * Bezier whose control polygon is already flat enough.
     */
    private fun appendBezierApproximation(
        points: List<RenderPoint>,
        output: MutableList<RenderPoint>
    ) {
        val count = points.size
        if (count < 2) {
            if (output.isEmpty()) output += points
            return
        }

        val (left, right) = subdivideBezier(points)
        val combined = ArrayList<RenderPoint>(count * 2 - 1)
        combined += left
        for (index in 0 until count - 1) {
            combined += right[index + 1]
        }

        output += points.first()
        for (index in 1 until count - 1) {
            val combinedIndex = index * 2
            output += (
                combined[combinedIndex - 1] +
                    combined[combinedIndex] * 2f +
                    combined[combinedIndex + 1]
                ) * 0.25f
        }
    }

    private fun perfectCircle(points: List<RenderPoint>): List<RenderPoint> {
        // osu!standard's perfect curve is only an arc for exactly three
        // distinct controls. Everything else uses the Bezier fallback.
        if (points.size != 3) return bezier(points)

        val arc = CircularArcProperties(points)
        if (!arc.isValid) return bezier(points)

        val amountPoints = if (2f * arc.radius <= CIRCULAR_ARC_TOLERANCE) {
            2
        } else {
            max(
                2,
                ceil(
                    arc.thetaRange /
                        (2.0 * acos(1.0 - CIRCULAR_ARC_TOLERANCE / arc.radius))
                ).toInt()
            )
        }
        if (amountPoints >= 1_000) return bezier(points)

        return List(amountPoints) { index ->
            val fraction = index.toDouble() / (amountPoints - 1)
            val theta = arc.thetaStart + arc.direction * fraction * arc.thetaRange
            RenderPoint(
                arc.centre.x + cos(theta).toFloat() * arc.radius,
                arc.centre.y + sin(theta).toFloat() * arc.radius
            )
        }
    }

    private fun catmull(points: List<RenderPoint>): List<RenderPoint> {
        if (points.size < 2) return points

        val result = ArrayList<RenderPoint>((points.size - 1) * CATMULL_DETAIL * 2)
        for (index in 0 until points.lastIndex) {
            val v1 = if (index > 0) points[index - 1] else points[index]
            val v2 = points[index]
            val v3 = points[index + 1]
            val v4 = if (index < points.size - 2) {
                points[index + 2]
            } else {
                v3 * 2f - v2
            }
            for (detail in 0 until CATMULL_DETAIL) {
                result += catmullFindPoint(
                    v1,
                    v2,
                    v3,
                    v4,
                    detail.toFloat() / CATMULL_DETAIL
                )
                result += catmullFindPoint(
                    v1,
                    v2,
                    v3,
                    v4,
                    (detail + 1).toFloat() / CATMULL_DETAIL
                )
            }
        }
        return result
    }

    private fun catmullFindPoint(
        v1: RenderPoint,
        v2: RenderPoint,
        v3: RenderPoint,
        v4: RenderPoint,
        t: Float
    ): RenderPoint {
        val t2 = t * t
        val t3 = t * t2
        return RenderPoint(
            0.5f * (
                2f * v2.x +
                    (-v1.x + v3.x) * t +
                    (2f * v1.x - 5f * v2.x + 4f * v3.x - v4.x) * t2 +
                    (-v1.x + 3f * v2.x - 3f * v3.x + v4.x) * t3
                ),
            0.5f * (
                2f * v2.y +
                    (-v1.y + v3.y) * t +
                    (2f * v1.y - 5f * v2.y + 4f * v3.y - v4.y) * t2 +
                    (-v1.y + 3f * v2.y - 3f * v3.y + v4.y) * t3
                )
        )
    }

    /**
     * Mirrors `SliderPath.calculateLength()`: trim longer paths and extend a
     * shorter final segment along its last direction.
     */
    private fun calculateLength(
        calculatedPath: List<RenderPoint>,
        expectedDistance: Float?
    ): OsuSliderPath {
        if (calculatedPath.isEmpty()) {
            return OsuSliderPath(emptyList(), emptyList(), 0f)
        }

        val path = calculatedPath.toMutableList()
        val cumulative = ArrayList<Float>(path.size)
        cumulative += 0f
        var calculatedLength = 0f
        for (index in 1 until path.size) {
            calculatedLength += path[index - 1].distanceTo(path[index])
            cumulative += calculatedLength
        }

        if (expectedDistance == null ||
            abs(calculatedLength - expectedDistance) <= GEOMETRY_EPSILON
        ) {
            return OsuSliderPath(path, cumulative, calculatedLength)
        }

        // osu!stable does not extend a path whose final two vertices coincide.
        if (expectedDistance > calculatedLength &&
            path.size >= 2 &&
            path[path.lastIndex] == path[path.lastIndex - 1]
        ) {
            return OsuSliderPath(path, cumulative, calculatedLength)
        }

        cumulative.removeAt(cumulative.lastIndex)
        var pathEndIndex = path.lastIndex

        if (calculatedLength > expectedDistance) {
            while (cumulative.isNotEmpty() && cumulative.last() >= expectedDistance) {
                cumulative.removeAt(cumulative.lastIndex)
                path.removeAt(pathEndIndex)
                pathEndIndex--
            }
        }

        if (pathEndIndex <= 0) {
            while (path.size > 1) path.removeAt(path.lastIndex)
            cumulative.clear()
            cumulative += 0f
            return OsuSliderPath(path, cumulative, 0f)
        }

        val previous = path[pathEndIndex - 1]
        val direction = unitDirection(path[pathEndIndex] - previous)
        path[pathEndIndex] = previous +
            direction * (expectedDistance - cumulative.last())
        cumulative += expectedDistance
        return OsuSliderPath(path, cumulative, cumulative.last())
    }

    private fun unitDirection(vector: RenderPoint): RenderPoint {
        val length = sqrt(vector.x * vector.x + vector.y * vector.y)
        return if (length <= GEOMETRY_EPSILON) {
            RenderPoint(0f, 0f)
        } else {
            RenderPoint(vector.x / length, vector.y / length)
        }
    }

    /**
     * Compatibility conversion for the old one-segment geometry entry point.
     * Parsed maps already carry typed control points and do not use this path.
     */
    private fun convertLegacyPoints(
        points: List<RenderPoint>,
        type: OsuCurveType
    ): List<OsuPathControlPoint> {
        if (points.isEmpty()) return emptyList()

        var resolvedType = type
        if (resolvedType == OsuCurveType.PERFECT_CIRCLE) {
            if (points.size != 3) {
                resolvedType = OsuCurveType.BEZIER
            } else if (isLinear(points[0], points[1], points[2])) {
                resolvedType = OsuCurveType.LINEAR
            }
        }

        val vertices = points.map { OsuPathControlPoint(it) }.toMutableList()
        vertices[0] = vertices[0].copy(curveType = resolvedType)
        val result = mutableListOf<OsuPathControlPoint>()
        var startIndex = 0
        var endIndex = 0
        while (++endIndex < vertices.size) {
            if (vertices[endIndex].position != vertices[endIndex - 1].position) continue
            if (resolvedType == OsuCurveType.CATMULL && endIndex > 1) continue
            if (endIndex == vertices.lastIndex) continue

            vertices[endIndex - 1] = vertices[endIndex - 1].copy(curveType = resolvedType)
            result += vertices.subList(startIndex, endIndex)
            startIndex = endIndex + 1
        }
        if (startIndex < endIndex) result += vertices.subList(startIndex, endIndex)
        return result
    }

    private fun isLinear(a: RenderPoint, b: RenderPoint, c: RenderPoint): Boolean {
        val cross = (b.y - a.y) * (c.x - a.x) - (b.x - a.x) * (c.y - a.y)
        return abs(cross) <= 0.001f
    }

    private data class CircularArcProperties(
        var isValid: Boolean,
        var thetaStart: Double,
        var thetaRange: Double,
        var direction: Double,
        var radius: Float,
        var centre: RenderPoint
    ) {
        constructor(points: List<RenderPoint>) : this(
            isValid = false,
            thetaStart = 0.0,
            thetaRange = 0.0,
            direction = 0.0,
            radius = 0f,
            centre = RenderPoint(0f, 0f)
        ) {
            val a = points[0]
            val b = points[1]
            val c = points[2]
            val determinant = 2f * (
                a.x * (b.y - c.y) +
                    b.x * (c.y - a.y) +
                    c.x * (a.y - b.y)
                )
            if (abs(determinant) <= GEOMETRY_EPSILON) return

            val aSquared = a.x * a.x + a.y * a.y
            val bSquared = b.x * b.x + b.y * b.y
            val cSquared = c.x * c.x + c.y * c.y
            val centre = RenderPoint(
                (
                    aSquared * (b.y - c.y) +
                        bSquared * (c.y - a.y) +
                        cSquared * (a.y - b.y)
                    ) / determinant,
                (
                    aSquared * (c.x - b.x) +
                        bSquared * (a.x - c.x) +
                        cSquared * (b.x - a.x)
                    ) / determinant
            )
            val startVector = a - centre
            val endVector = c - centre
            val radius = startVector.distanceTo(RenderPoint(0f, 0f))
            if (radius <= GEOMETRY_EPSILON) return

            val thetaStart = atan2(startVector.y.toDouble(), startVector.x.toDouble())
            var thetaEnd = atan2(endVector.y.toDouble(), endVector.x.toDouble())
            while (thetaEnd < thetaStart) thetaEnd += 2.0 * PI

            var thetaRange = thetaEnd - thetaStart
            var direction = 1.0
            val orthoAtoC = RenderPoint(c.y - a.y, -(c.x - a.x))
            val dot = orthoAtoC.x * (b.x - a.x) + orthoAtoC.y * (b.y - a.y)
            if (dot < 0f) {
                direction = -1.0
                thetaRange = 2.0 * PI - thetaRange
            }

            this.isValid = true
            this.thetaStart = thetaStart
            this.thetaRange = thetaRange
            this.direction = direction
            this.radius = radius
            this.centre = centre
        }
    }
}
