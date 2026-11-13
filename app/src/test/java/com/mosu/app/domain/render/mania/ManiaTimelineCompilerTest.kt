package com.mosu.app.domain.render.mania

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManiaTimelineCompilerTest {
    @Test
    fun preservesNoteTimingColumnsAndHoldDurations() {
        val timeline = ManiaTimelineCompiler.compile(
            parsed(
                timingPoints = listOf(timingPoint(0.0, 500.0)),
                hitObjects = listOf(
                    hitObject(1_000.0, 1_000.0, column = 0),
                    hitObject(1_250.0, 1_750.0, column = 3, isHold = true)
                )
            )
        )

        assertEquals(2, timeline.notes.size)
        assertEquals(1_000.0, timeline.notes[0].startTimeMs, 0.0001)
        assertEquals(0.0, timeline.notes[0].durationMs, 0.0001)
        assertEquals(3, timeline.notes[1].column)
        assertEquals(500.0, timeline.notes[1].durationMs, 0.0001)
        assertTrue(timeline.notes[1].isHold)
        assertEquals(1_000.0, timeline.firstObjectStartMs, 0.0001)
        assertEquals(1_750.0, timeline.lastObjectEndMs, 0.0001)
    }

    @Test
    fun choosesBeatLengthByWeightedActiveDuration() {
        val beatLength = ManiaTimelineCompiler.mostCommonBeatLength(
            timingPoints = listOf(
                timelinePoint(0.0, 500.0),
                timelinePoint(1_000.0, 400.0)
            ),
            lastObjectEndMs = 3_000.0
        )

        assertEquals(400.0, beatLength, 0.0001)
    }

    @Test
    fun compilesInheritedScrollMultipliers() {
        val timeline = ManiaTimelineCompiler.compile(
            parsed(
                timingPoints = listOf(
                    timingPoint(0.0, 500.0),
                    timingPoint(1_000.0, -50.0, uninherited = false),
                    timingPoint(2_000.0, 400.0)
                ),
                hitObjects = listOf(hitObject(3_000.0, 3_000.0))
            )
        )

        assertEquals(3, timeline.scrollControlPoints.size)
        assertEquals(1.0, timeline.scrollControlPoints[0].multiplier, 0.0001)
        assertEquals(2.0, timeline.scrollControlPoints[1].multiplier, 0.0001)
        assertEquals(1.25, timeline.scrollControlPoints[2].multiplier, 0.0001)
    }

    @Test
    fun inheritedMultiplierUsesOneHundredDividedByNegativeBeatLength() {
        val timeline = ManiaTimelineCompiler.compile(
            parsed(
                timingPoints = listOf(
                    timingPoint(0.0, 500.0),
                    timingPoint(500.0, -100.0, uninherited = false)
                ),
                hitObjects = listOf(hitObject(1_000.0, 1_000.0))
            )
        )

        assertEquals(1.0, timeline.scrollControlPoints[1].multiplier, 0.0001)
    }

    @Test
    fun uninheritedTimingPointResetsInheritedMultiplier() {
        val timeline = ManiaTimelineCompiler.compile(
            parsed(
                timingPoints = listOf(
                    timingPoint(0.0, 500.0),
                    timingPoint(500.0, -50.0, uninherited = false),
                    timingPoint(1_000.0, 500.0)
                ),
                hitObjects = listOf(hitObject(2_000.0, 2_000.0))
            )
        )

        assertEquals(2.0, timeline.scrollControlPoints[1].multiplier, 0.0001)
        assertEquals(1.0, timeline.scrollControlPoints[2].multiplier, 0.0001)
    }

    @Test
    fun duplicateTimingPointTimestampsUseTheLastPoint() {
        val timeline = ManiaTimelineCompiler.compile(
            parsed(
                timingPoints = listOf(
                    timingPoint(0.0, 500.0),
                    timingPoint(0.0, 400.0)
                ),
                hitObjects = listOf(hitObject(2_000.0, 2_000.0))
            )
        )

        assertEquals(1, timeline.scrollControlPoints.size)
        assertEquals(0.0, timeline.scrollControlPoints.single().timeMs, 0.0001)
        assertEquals(1.0, timeline.scrollControlPoints.single().multiplier, 0.0001)
        assertEquals(
            listOf(0.0, 1_600.0, 3_200.0),
            timeline.barlines.map { it.timeMs }
        )
    }

    @Test
    fun generatesBarlinesFromUninheritedTimingAndMeterChanges() {
        val timeline = ManiaTimelineCompiler.compile(
            parsed(
                timingPoints = listOf(
                    timingPoint(0.0, 500.0, meter = 4),
                    timingPoint(500.0, -50.0, uninherited = false),
                    timingPoint(2_000.0, 400.0, meter = 3)
                ),
                hitObjects = listOf(hitObject(4_000.0, 4_000.0))
            )
        )

        assertEquals(
            listOf(0.0, 2_000.0, 3_200.0, 4_400.0),
            timeline.barlines.map { it.timeMs }
        )
        assertEquals(
            listOf(true, true, false, false),
            timeline.barlines.map { it.major }
        )
    }

    @Test
    fun omitFirstBarLineSkipsTheTimingPointBar() {
        val timeline = ManiaTimelineCompiler.compile(
            parsed(
                timingPoints = listOf(
                    timingPoint(0.0, 500.0, meter = 4, omitFirstBarLine = true)
                ),
                hitObjects = listOf(hitObject(4_000.0, 4_000.0))
            )
        )

        assertFalse(timeline.barlines.any { it.timeMs == 0.0 })
        assertEquals(2_000.0, timeline.barlines.first().timeMs, 0.0001)
        assertTrue(timeline.barlines.first().major)
    }

    @Test
    fun defaultTimeRangeMatchesLazerScrollSpeed() {
        val timeline = ManiaTimelineCompiler.compile(
            parsed(hitObjects = listOf(hitObject(1_000.0, 1_000.0)))
        )

        assertEquals(
            729.627_029_581_850_5,
            timeline.timeRangeForHitPosition(110f),
            0.0001
        )
    }

    @Test
    fun verticalPositionPlacesCurrentNotesAtTheHitTarget() {
        val timeline = ManiaTimelineCompiler.compile(
            parsed(hitObjects = listOf(hitObject(1_000.0, 1_000.0)))
        )
        val timeRange = timeline.timeRangeForHitPosition(110f)

        assertEquals(
            914f,
            timeline.verticalPositionAt(1_000.0, 1_000.0, timeRange, 110f),
            0.0001f
        )
        assertTrue(
            timeline.verticalPositionAt(1_500.0, 1_000.0, timeRange, 110f) < 914f
        )
        assertTrue(
            timeline.verticalPositionAt(500.0, 1_000.0, timeRange, 110f) > 914f
        )
    }

    private fun parsed(
        timingPoints: List<ManiaRawTimingPoint> = listOf(timingPoint(0.0, 500.0)),
        hitObjects: List<ManiaRawHitObject> = listOf(hitObject(1_000.0, 1_000.0))
    ) = ParsedManiaMap(
        beatmapSetId = 42L,
        beatmapId = 10L,
        difficultyName = "Hard",
        audioFilename = "audio.mp3",
        keyCount = 4,
        formatVersion = 14,
        timingPoints = timingPoints,
        hitObjects = hitObjects
    )

    private fun timingPoint(
        timeMs: Double,
        beatLengthMs: Double,
        meter: Int = 4,
        uninherited: Boolean = true,
        omitFirstBarLine: Boolean = false
    ) = ManiaRawTimingPoint(
        timeMs = timeMs,
        beatLengthMs = beatLengthMs,
        meter = meter,
        uninherited = uninherited,
        omitFirstBarLine = omitFirstBarLine
    )

    private fun timelinePoint(timeMs: Double, beatLengthMs: Double) =
        ManiaTimelineTimingPoint(
            timeMs = timeMs,
            beatLengthMs = beatLengthMs,
            meter = 4,
            uninherited = true,
            omitFirstBarLine = false
        )

    private fun hitObject(
        startTimeMs: Double,
        endTimeMs: Double,
        column: Int = 0,
        isHold: Boolean = false
    ) = ManiaRawHitObject(
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        column = column,
        isHold = isHold
    )
}
