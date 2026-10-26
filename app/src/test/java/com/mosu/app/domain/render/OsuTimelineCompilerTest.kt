package com.mosu.app.domain.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OsuTimelineCompilerTest {
    @Test
    fun newUninheritedTimingPointResetsInheritedSvMultiplier() {
        val timing = listOf(
            OsuTimingPoint(0.0, 500.0, 4, true, 1.0),
            OsuTimingPoint(1_000.0, -50.0, 4, false, 0.5),
            OsuTimingPoint(2_000.0, 400.0, 4, true, 1.0)
        )

        assertTiming(500.0, 1.0, OsuTimelineCompiler.timingStateAt(timing, 999.0))
        assertTiming(500.0, 0.5, OsuTimelineCompiler.timingStateAt(timing, 1_500.0))
        assertTiming(400.0, 1.0, OsuTimelineCompiler.timingStateAt(timing, 2_000.0))
    }

    @Test
    fun inheritedBpmMultiplierFollowsNegativeBeatLength() {
        assertEquals(
            1.0,
            OsuTimelineCompiler.inheritedBpmMultiplier(
                OsuRawTimingPoint(0.0, 500.0, 4, true)
            ),
            0.0001
        )
        assertEquals(
            0.5,
            OsuTimelineCompiler.inheritedBpmMultiplier(
                OsuRawTimingPoint(0.0, -50.0, 4, false)
            ),
            0.0001
        )
        assertEquals(
            1.75,
            OsuTimelineCompiler.inheritedBpmMultiplier(
                OsuRawTimingPoint(0.0, -175.0, 4, false)
            ),
            0.0001
        )
    }

    @Test
    fun compiledSliderUsesTimingPointSvAndPathLength() {
        val parsed = ParsedOsuMap(
            header = header(),
            timingPoints = listOf(
                OsuRawTimingPoint(0.0, 500.0, 4, true),
                OsuRawTimingPoint(500.0, -50.0, 4, false)
            ),
            hitObjects = listOf(
                OsuRawSlider(
                    timeMs = 1_000.0,
                    position = RenderPoint(100f, 100f),
                    newCombo = true,
                    comboOffset = 0,
                    curveType = OsuCurveType.LINEAR,
                    controlPoints = listOf(RenderPoint(200f, 100f)),
                    spanCount = 2,
                    pathLength = 100f
                )
            ),
            comboColours = emptyList(),
            sliderTrackOverride = null,
            sliderBorder = null
        )

        val slider = OsuTimelineCompiler.compile(parsed, 42L).objects.single() as OsuSliderObject
        assertEquals(178.571428, slider.spanDurationMs, 0.0001)
        assertEquals(357.142857, slider.endTimeMs - slider.startTimeMs, 0.0001)
        assertEquals(100f, slider.path.length, 0.0001f)
    }

    @Test
    fun comboNumbersColoursAndSpinnerBoundariesAreCompiled() {
        val parsed = ParsedOsuMap(
            header = header(),
            timingPoints = listOf(OsuRawTimingPoint(0.0, 500.0, 4, true)),
            hitObjects = listOf(
                OsuRawCircle(1_000.0, RenderPoint(10f, 10f), false, 0),
                OsuRawCircle(1_100.0, RenderPoint(30f, 30f), false, 0),
                OsuRawCircle(1_200.0, RenderPoint(50f, 50f), true, 2),
                OsuRawCircle(1_300.0, RenderPoint(70f, 70f), false, 0),
                OsuRawSpinner(1_400.0, 1_600.0, false, 0),
                OsuRawCircle(1_700.0, RenderPoint(90f, 90f), false, 0)
            ),
            comboColours = emptyList(),
            sliderTrackOverride = null,
            sliderBorder = null
        )

        val objects = OsuTimelineCompiler.compile(parsed, 42L).objects
        assertEquals(listOf(1, 2, 1, 2, 3, 1), objects.map { it.comboNumber })
        assertEquals(listOf(0, 0, 3, 3, 3, 4), objects.map { it.comboColourIndex })
    }

    @Test
    fun closeStackedObjectsReceiveStackOffsets() {
        val parsed = ParsedOsuMap(
            header = header(approachRate = 9f),
            timingPoints = listOf(OsuRawTimingPoint(0.0, 500.0, 4, true)),
            hitObjects = listOf(
                OsuRawCircle(1_000.0, RenderPoint(100f, 100f), false, 0),
                OsuRawCircle(1_100.0, RenderPoint(100.5f, 100.5f), false, 0),
                OsuRawCircle(1_200.0, RenderPoint(200f, 200f), false, 0)
            ),
            comboColours = emptyList(),
            sliderTrackOverride = null,
            sliderBorder = null
        )

        val objects = OsuTimelineCompiler.compile(parsed, 42L).objects
        assertEquals(0, objects[0].stackHeight)
        assertEquals(1, objects[1].stackHeight)
        assertEquals(0, objects[2].stackHeight)
        assertTrue(objects[1].stackedPosition.x < objects[1].position.x)
        assertTrue(objects[1].stackedPosition.y < objects[1].position.y)
    }

    @Test
    fun nonStandardMapsAndMismatchedSetIdsAreRejected() {
        val nonStandard = ParsedOsuMap(
            header = header(mode = 1),
            timingPoints = emptyList(),
            hitObjects = emptyList(),
            comboColours = emptyList(),
            sliderTrackOverride = null,
            sliderBorder = null
        )
        val mismatch = nonStandard.copy(header = header(beatmapSetId = 99))

        assertThrowsIllegalArgument { OsuTimelineCompiler.compile(nonStandard, 42L) }
        assertThrowsIllegalArgument { OsuTimelineCompiler.compile(mismatch, 42L) }
    }

    private fun assertTiming(
        expectedBeatLength: Double,
        expectedMultiplier: Double,
        actual: Pair<Double, Double>
    ) {
        assertEquals(expectedBeatLength, actual.first, 0.0001)
        assertEquals(expectedMultiplier, actual.second, 0.0001)
    }

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("Expected IllegalArgumentException")
    }

    private fun header(
        beatmapSetId: Long = 42L,
        mode: Int = 0,
        approachRate: Float = 9f
    ) = OsuMapHeader(
        beatmapSetId = beatmapSetId,
        mode = mode,
        audioFilename = "audio.mp3",
        difficultyName = "Hard",
        approachRate = approachRate,
        overallDifficulty = 7f,
        circleSize = 4f,
        sliderMultiplier = 1.4f,
        stackLeniency = 0.7f
    )
}
