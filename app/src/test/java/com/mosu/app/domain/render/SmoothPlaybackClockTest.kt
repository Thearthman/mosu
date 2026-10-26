package com.mosu.app.domain.render

import org.junit.Assert.assertEquals
import org.junit.Test

class SmoothPlaybackClockTest {
    @Test
    fun predictsPositionBetweenMediaSamples() {
        val clock = SmoothPlaybackClock()
        clock.reset(1_000L, frameNanos(0.0))

        assertEquals(
            1_016.0,
            clock.update(frameNanos(16.0), 1_016L, playbackSpeed = 1f),
            0.0001
        )
    }

    @Test
    fun appliesDoubleTimeSpeedToPrediction() {
        val clock = SmoothPlaybackClock()
        clock.reset(1_000L, frameNanos(0.0))
        clock.update(frameNanos(0.0), 1_000L, playbackSpeed = 1.5f)

        assertEquals(
            1_024.0,
            clock.update(frameNanos(16.0), 1_024L, playbackSpeed = 1.5f),
            0.0001
        )
    }

    @Test
    fun correctsSmallDriftWithoutJumping() {
        val clock = SmoothPlaybackClock()
        clock.reset(1_000L, frameNanos(0.0))

        assertEquals(
            1_016.10,
            clock.update(frameNanos(16.0), 1_018L, playbackSpeed = 1f),
            0.0001
        )
    }

    @Test
    fun snapsToLargeMediaClockCorrection() {
        val clock = SmoothPlaybackClock()
        clock.reset(1_000L, frameNanos(0.0))

        assertEquals(
            1_200.0,
            clock.update(frameNanos(16.0), 1_200L, playbackSpeed = 1f),
            0.0001
        )
    }

    @Test
    fun snapsImmediatelyWhenSeekingBackwards() {
        val clock = SmoothPlaybackClock()
        clock.reset(1_000L, frameNanos(0.0))

        assertEquals(
            400.0,
            clock.update(frameNanos(16.0), 400L, playbackSpeed = 1f),
            0.0001
        )
    }

    @Test
    fun staleMediaSampleNeverMovesRenderClockBackwards() {
        val clock = SmoothPlaybackClock()
        clock.reset(1_000L, frameNanos(0.0))
        val first = clock.update(frameNanos(16.0), 1_016L, playbackSpeed = 1f)
        val second = clock.update(frameNanos(32.0), 1_014L, playbackSpeed = 1f)

        assertEquals(1_016.0, first, 0.0001)
        assertEquals(1_032.0, second, 0.0001)
    }

    @Test
    fun largerStaleMediaSampleDoesNotRewindTheRenderClock() {
        val clock = SmoothPlaybackClock()
        clock.reset(1_000L, frameNanos(0.0))
        val first = clock.update(frameNanos(16.0), 1_016L, playbackSpeed = 1f)
        val second = clock.update(frameNanos(32.0), 960L, playbackSpeed = 1f)

        assertEquals(1_016.0, first, 0.0001)
        assertEquals(1_032.0, second, 0.0001)
    }

    @Test
    fun repeatedStaleSamplesKeepTheRenderClockAdvancing() {
        val clock = SmoothPlaybackClock()
        clock.reset(1_000L, frameNanos(0.0))

        assertEquals(
            1_016.0,
            clock.update(frameNanos(16.0), 1_016L, playbackSpeed = 1f),
            0.0001
        )
        assertEquals(
            1_032.0,
            clock.update(frameNanos(32.0), 1_016L, playbackSpeed = 1f),
            0.0001
        )
        assertEquals(
            1_048.0,
            clock.update(frameNanos(48.0), 1_016L, playbackSpeed = 1f),
            0.0001
        )
    }

    @Test
    fun ignoresQuantisationScaleDriftWithinTheDeadband() {
        val clock = SmoothPlaybackClock()
        clock.reset(1_000L, frameNanos(0.0))

        assertEquals(
            1_016.0,
            clock.update(frameNanos(16.0), 1_017L, playbackSpeed = 1f),
            0.0001
        )
    }

    @Test
    fun snapsAfterAStalledFrame() {
        val clock = SmoothPlaybackClock()
        clock.reset(1_000L, frameNanos(0.0))

        assertEquals(
            1_150.0,
            clock.update(frameNanos(150.0), 1_150L, playbackSpeed = 1f),
            0.0001
        )
    }

    @Test
    fun playbackSpeedChangesRebaseThePrediction() {
        val clock = SmoothPlaybackClock()
        clock.reset(1_000L, frameNanos(0.0))
        clock.update(frameNanos(0.0), 1_000L, playbackSpeed = 1.5f)

        assertEquals(
            1_024.0,
            clock.update(frameNanos(16.0), 1_024L, playbackSpeed = 1.5f),
            0.0001
        )
        assertEquals(
            1_040.0,
            clock.update(frameNanos(32.0), 1_040L, playbackSpeed = 1f),
            0.0001
        )
        assertEquals(
            1_056.0,
            clock.update(frameNanos(48.0), 1_056L, playbackSpeed = 1f),
            0.0001
        )
    }

    private fun frameNanos(milliseconds: Double): Long =
        (milliseconds * 1_000_000.0).toLong()
}
