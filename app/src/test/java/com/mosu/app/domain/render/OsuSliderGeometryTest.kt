package com.mosu.app.domain.render

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OsuSliderGeometryTest {
    @Test
    fun linearPathIsTrimmedToDeclaredLength() {
        val path = OsuSliderGeometry.build(
            start = RenderPoint(0f, 0f),
            curveType = OsuCurveType.LINEAR,
            controlPoints = listOf(RenderPoint(100f, 0f)),
            declaredLength = 40f
        )

        assertEquals(40f, path.length, 0.0001f)
        assertEquals(40f, path.distances.last(), 0.0001f)
        assertEquals(20f, path.positionAtFraction(0.5f).x, 0.0001f)
        assertEquals(40f, path.positionAtFraction(1f).x, 0.0001f)
    }

    @Test
    fun declaredLengthCanExceedSampledGeometryLength() {
        val path = OsuSliderGeometry.build(
            start = RenderPoint(0f, 0f),
            curveType = OsuCurveType.LINEAR,
            controlPoints = listOf(RenderPoint(100f, 0f)),
            declaredLength = 120f
        )

        assertEquals(120f, path.length, 0.0001f)
        assertEquals(120f, path.distances.last(), 0.0001f)
        assertEquals(120f, path.points.last().x, 0.0001f)
        assertEquals(120f, path.positionAtDistance(120f).x, 0.0001f)
    }

    @Test
    fun quadraticBezierUsesItsControlPoint() {
        val path = OsuSliderGeometry.build(
            start = RenderPoint(0f, 0f),
            curveType = OsuCurveType.BEZIER,
            controlPoints = listOf(RenderPoint(50f, 50f), RenderPoint(100f, 0f)),
            declaredLength = 0f
        )
        val midpoint = path.positionAtFraction(0.5f)

        assertEquals(50f, midpoint.x, 0.5f)
        assertEquals(25f, midpoint.y, 0.5f)
        assertEquals(0f, path.points.first().y, 0.0001f)
        assertEquals(0f, path.points.last().y, 0.0001f)
    }

    @Test
    fun repeatedBezierControlPointStartsANewSegment() {
        val repeated = RenderPoint(50f, 50f)
        val path = OsuSliderGeometry.build(
            start = RenderPoint(0f, 0f),
            curveType = OsuCurveType.BEZIER,
            controlPoints = listOf(repeated, repeated, RenderPoint(100f, 0f)),
            declaredLength = 0f
        )

        assertTrue(path.points.any { it.distanceTo(repeated) < 0.001f })
        assertEquals(0f, path.points.first().x, 0.0001f)
        assertEquals(100f, path.points.last().x, 0.0001f)
    }

    @Test
    fun perfectCirclePassesThroughAllThreeControlsOnTheSelectedArc() {
        val path = OsuSliderGeometry.build(
            start = RenderPoint(0f, 0f),
            curveType = OsuCurveType.PERFECT_CIRCLE,
            controlPoints = listOf(RenderPoint(50f, 50f), RenderPoint(100f, 0f)),
            declaredLength = 0f
        )

        path.points.forEach { point ->
            val distanceFromCentre = hypot(point.x - 50f, point.y)
            assertEquals(50f, distanceFromCentre, 0.001f)
        }
        assertTrue(
            path.points.any {
                abs(it.x - 50f) < 1.5f && abs(it.y - 50f) < 1.5f
            }
        )
    }

    @Test
    fun degeneratePerfectCircleFallsBackWithoutFailing() {
        val path = OsuSliderGeometry.build(
            start = RenderPoint(0f, 0f),
            curveType = OsuCurveType.PERFECT_CIRCLE,
            controlPoints = listOf(RenderPoint(50f, 0f), RenderPoint(100f, 0f)),
            declaredLength = 100f
        )

        assertEquals(0f, path.points.first().x, 0.0001f)
        assertEquals(100f, path.points.last().x, 0.0001f)
        assertTrue(path.distances.last() >= 100f)
    }

    @Test
    fun catmullPathPassesThroughEveryControlPoint() {
        val controls = listOf(
            RenderPoint(50f, 50f),
            RenderPoint(100f, 0f),
            RenderPoint(150f, 50f)
        )
        val path = OsuSliderGeometry.build(
            start = RenderPoint(0f, 0f),
            curveType = OsuCurveType.CATMULL,
            controlPoints = controls,
            declaredLength = 0f
        )

        controls.forEach { control ->
            assertTrue(
                "Expected Catmull path to pass through $control",
                path.points.any { abs(it.x - control.x) < 0.001f && abs(it.y - control.y) < 0.001f }
            )
        }
    }

    @Test
    fun mixedBezierLinearBezierSegmentsShareExactBoundaryVertices() {
        val firstJoin = RenderPoint(100f, 40f)
        val secondJoin = RenderPoint(160f, 0f)
        val path = OsuSliderGeometry.build(
            pathControlPoints = listOf(
                OsuPathControlPoint(RenderPoint(0f, 0f), OsuCurveType.BEZIER),
                OsuPathControlPoint(RenderPoint(40f, 70f)),
                OsuPathControlPoint(RenderPoint(80f, 70f)),
                OsuPathControlPoint(firstJoin, OsuCurveType.LINEAR),
                OsuPathControlPoint(RenderPoint(130f, 10f)),
                OsuPathControlPoint(secondJoin, OsuCurveType.BEZIER),
                OsuPathControlPoint(RenderPoint(190f, -50f)),
                OsuPathControlPoint(RenderPoint(240f, 0f))
            ),
            declaredLength = 0f
        )

        assertSingleInteriorVertex(path.points, firstJoin)
        assertSingleInteriorVertex(path.points, secondJoin)
        assertTrue(path.points.size > 3)
    }

    @Test
    fun perfectCircleJoinsAPolylineAtTheSameBoundaryVertex() {
        val boundary = RenderPoint(100f, 0f)
        val path = OsuSliderGeometry.build(
            pathControlPoints = listOf(
                OsuPathControlPoint(RenderPoint(0f, 0f), OsuCurveType.PERFECT_CIRCLE),
                OsuPathControlPoint(RenderPoint(50f, 50f)),
                OsuPathControlPoint(boundary, OsuCurveType.LINEAR),
                OsuPathControlPoint(RenderPoint(150f, 50f))
            ),
            declaredLength = 0f
        )

        assertSingleInteriorVertex(path.points, boundary)
        assertTrue(path.points.size > 3)
    }

    private fun assertSingleInteriorVertex(points: List<RenderPoint>, expected: RenderPoint) {
        val indices = points.indices.filter { points[it] == expected }
        assertEquals("Expected one exact boundary vertex at $expected", 1, indices.size)
        assertTrue("Boundary vertex must not be the first path point", indices.single() > 0)
        assertTrue(
            "Boundary vertex must not be the last path point",
            indices.single() < points.lastIndex
        )
    }
}
