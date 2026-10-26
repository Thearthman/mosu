package com.mosu.app.domain.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OsuRenderTypesTest {
    @Test
    fun approachRateUsesTheVerifiedBranchesAtFive() {
        assertEquals(1260.0, approachRateToPreemptMs(4.5f), 0.0001)
        assertEquals(1200.0, approachRateToPreemptMs(5f), 0.0001)
        assertEquals(555.0, approachRateToPreemptMs(9.3f), 0.0001)
    }

    @Test
    fun approachRatePreemptIsFloored() {
        assertEquals(1799.0, approachRateToPreemptMs(0.001f), 0.0001)
        assertEquals(1185.0, approachRateToPreemptMs(5.1f), 0.0001)
    }

    @Test
    fun fadeInIsCappedAtFourHundredMilliseconds() {
        assertEquals(400.0, preemptToFadeInMs(1200.0), 0.0001)
        assertEquals(400.0, preemptToFadeInMs(450.0), 0.0001)
        assertEquals(88.8888, preemptToFadeInMs(100.0), 0.001)
    }

    @Test
    fun sliderDurationMatchesVelocityFormula() {
        assertEquals(
            357.142857,
            sliderDurationMs(1.4f, 500.0, 1.0, 100f, 1),
            0.0001
        )
        assertEquals(
            178.571428,
            sliderDurationMs(1.4f, 500.0, 0.5, 100f, 1),
            0.0001
        )
        assertEquals(
            714.285714,
            sliderDurationMs(1.4f, 500.0, 1.0, 100f, 2),
            0.0001
        )
    }

    @Test
    fun activeObjectQueryReturnsOverlappingObjectsBackToFront() {
        val first = OsuCircleObject(
            startTimeMs = 1_000.0,
            position = RenderPoint(10f, 10f),
            stackHeight = 0,
            comboNumber = 1,
            comboColourIndex = 0,
            objectScale = 1f,
            preemptMs = 600.0,
            fadeInMs = 400.0
        )
        val second = OsuCircleObject(
            startTimeMs = 1_200.0,
            position = RenderPoint(20f, 20f),
            stackHeight = 0,
            comboNumber = 2,
            comboColourIndex = 0,
            objectScale = 1f,
            preemptMs = 600.0,
            fadeInMs = 400.0
        )
        val timeline = timelineWithObjects(listOf(first, second))

        assertEquals(emptyList<Int>(), activeComboNumbers(timeline, 399.0))
        assertEquals(listOf(1), activeComboNumbers(timeline, 400.0))
        assertEquals(listOf(2, 1), activeComboNumbers(timeline, 800.0))
        assertEquals(listOf(2, 1), activeComboNumbers(timeline, 1_239.0))
        assertEquals(listOf(2), activeComboNumbers(timeline, 1_241.0))
    }

    @Test
    fun sliderBodySnakesInBeforeActivation() {
        val slider = slider(spanCount = 2, spanDurationMs = 1_000.0)

        assertEquals(OsuSliderBodyRange(0f, 0f), slider.bodyRangeAt(-600.0))
        assertEquals(OsuSliderBodyRange(0f, 0.5f), slider.bodyRangeAt(-500.0))
        assertEquals(OsuSliderBodyRange(0f, 1f), slider.bodyRangeAt(-400.0))
        assertEquals(OsuSliderBodyRange(0f, 1f), slider.bodyRangeAt(0.0))
    }

    @Test
    fun sliderBodyStaysFullUntilTheFinalSpan() {
        val slider = slider(spanCount = 3, spanDurationMs = 1_000.0)

        assertEquals(OsuSliderBodyRange(0f, 1f), slider.bodyRangeAt(1_000.0))
        assertEquals(OsuSliderBodyRange(0f, 1f), slider.bodyRangeAt(1_999.0))
    }

    @Test
    fun forwardFinalSpanRemovesTheAlreadySlidSection() {
        val slider = slider(spanCount = 3, spanDurationMs = 1_000.0)

        assertEquals(OsuSliderBodyRange(0.5f, 1f), slider.bodyRangeAt(2_500.0))
        assertEquals(OsuSliderBodyRange(0.9f, 1f), slider.bodyRangeAt(2_900.0))
    }

    @Test
    fun reversedFinalSpanRemovesTheAlreadySlidSection() {
        val slider = slider(spanCount = 2, spanDurationMs = 1_000.0)

        assertEquals(OsuSliderBodyRange(0f, 0.5f), slider.bodyRangeAt(1_500.0))
        val nearCompletion = slider.bodyRangeAt(1_900.0)
        assertEquals(0f, nearCompletion.startFraction, 0.0001f)
        assertEquals(0.1f, nearCompletion.endFraction, 0.0001f)
    }

    @Test
    fun singleSpanSliderSnakesOutFromItsHead() {
        val slider = slider(spanCount = 1, spanDurationMs = 1_000.0)

        assertEquals(OsuSliderBodyRange(0.25f, 1f), slider.bodyRangeAt(250.0))
        assertEquals(OsuSliderBodyRange(0.75f, 1f), slider.bodyRangeAt(750.0))
    }

    @Test
    fun sliderBodyRangeIsEmptyAtCompletion() {
        val forward = slider(spanCount = 1, spanDurationMs = 1_000.0)
        val reverse = slider(spanCount = 2, spanDurationMs = 1_000.0)

        val forwardRange = forward.bodyRangeAt(1_000.0)
        val reverseRange = reverse.bodyRangeAt(2_000.0)
        assertEquals(forwardRange.startFraction, forwardRange.endFraction, 0.0001f)
        assertEquals(reverseRange.startFraction, reverseRange.endFraction, 0.0001f)
    }

    @Test
    fun evenSliderRepeatsReverseAtTheDeclaredEnd() {
        val slider = slider(spanCount = 2, spanDurationMs = 1_000.0)

        assertEquals(0f, slider.pathPositionAt(0.0).x, 0.001f)
        assertEquals(100f, slider.pathPositionAt(1_000.0).x, 0.001f)
        assertEquals(50f, slider.pathPositionAt(1_500.0).x, 0.001f)
        assertEquals(0f, slider.pathPositionAt(2_000.0).x, 0.001f)
    }

    @Test
    fun oddSliderRepeatsFinishForward() {
        val slider = slider(spanCount = 3, spanDurationMs = 1_000.0)

        assertEquals(0f, slider.pathPositionAt(2_000.0).x, 0.001f)
        assertEquals(50f, slider.pathPositionAt(2_500.0).x, 0.001f)
        assertEquals(100f, slider.pathPositionAt(3_000.0).x, 0.001f)
    }

    private fun slider(spanCount: Int, spanDurationMs: Double): OsuSliderObject {
        val path = OsuSliderPath(
            points = listOf(RenderPoint(0f, 0f), RenderPoint(100f, 0f)),
            distances = listOf(0f, 100f),
            length = 100f
        )
        return OsuSliderObject(
            startTimeMs = 0.0,
            endTimeMs = spanDurationMs * spanCount,
            position = RenderPoint(0f, 0f),
            stackHeight = 0,
            comboNumber = 1,
            comboColourIndex = 0,
            objectScale = 1f,
            preemptMs = 600.0,
            fadeInMs = 400.0,
            path = path,
            spanCount = spanCount,
            spanDurationMs = spanDurationMs
        )
    }

    private fun timelineWithObjects(objects: List<OsuRenderObject>): OsuRenderTimeline =
        OsuRenderTimeline(
            beatmapSetId = 1L,
            difficultyName = "Test",
            audioFilename = "audio.mp3",
            approachRate = 9f,
            overallDifficulty = 8f,
            circleSize = 4f,
            preemptMs = 600.0,
            fadeInMs = 400.0,
            sliderMultiplier = 1.4f,
            objects = objects,
            timingPoints = emptyList(),
            comboColours = listOf(0xffffffff.toInt()),
            sliderTrackOverride = null,
            sliderBorder = null
        )

    private fun activeComboNumbers(
        timeline: OsuRenderTimeline,
        timeMs: Double
    ): List<Int> {
        val result = mutableListOf<Int>()
        timeline.forEachActiveObject(timeMs) { result += it.comboNumber }
        assertTrue(result.all { it > 0 })
        return result
    }
}
