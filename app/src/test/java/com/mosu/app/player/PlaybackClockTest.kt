package com.mosu.app.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackClockTest {
    @Test
    fun stationaryClockDoesNotProjectDuringBuffering() {
        assertFalse(
            hasPlaybackClockAdvanced(
                previousPositionMs = 10_000L,
                previousCompletionTimeMs = 1_000L,
                nextPositionMs = 10_000L,
                nextCompletionTimeMs = 1_050L,
                speed = 1f
            )
        )
    }

    @Test
    fun tinyClockDriftIsTreatedAsAStall() {
        assertFalse(
            hasPlaybackClockAdvanced(
                previousPositionMs = 10_000L,
                previousCompletionTimeMs = 1_000L,
                nextPositionMs = 10_002L,
                nextCompletionTimeMs = 1_200L,
                speed = 1f
            )
        )
    }

    @Test
    fun normalPlaybackAdvancesTheProjection() {
        assertTrue(
            hasPlaybackClockAdvanced(
                previousPositionMs = 10_000L,
                previousCompletionTimeMs = 1_000L,
                nextPositionMs = 10_050L,
                nextCompletionTimeMs = 1_050L,
                speed = 1f
            )
        )
    }

    @Test
    fun doubleTimeAdvancesTheProjectionAtItsPlaybackRate() {
        assertTrue(
            hasPlaybackClockAdvanced(
                previousPositionMs = 10_000L,
                previousCompletionTimeMs = 1_000L,
                nextPositionMs = 10_075L,
                nextCompletionTimeMs = 1_050L,
                speed = 1.5f
            )
        )
    }

    @Test
    fun duplicateTimestampIsNeverProjected() {
        assertFalse(
            hasPlaybackClockAdvanced(
                previousPositionMs = 10_000L,
                previousCompletionTimeMs = 1_000L,
                nextPositionMs = 10_050L,
                nextCompletionTimeMs = 1_000L,
                speed = 1f
            )
        )
    }

    @Test
    fun staleSampleBeforeForwardSeekIsRejected() {
        assertFalse(
            hasPlaybackSeekReachedTarget(
                fromPositionMs = 10_000L,
                targetPositionMs = 30_000L,
                requestedAtMs = 1_000L,
                samplePositionMs = 10_050L,
                sampleCompletionTimeMs = 1_050L,
                speed = 1f
            )
        )
    }

    @Test
    fun sampleAfterForwardSeekIsAccepted() {
        assertTrue(
            hasPlaybackSeekReachedTarget(
                fromPositionMs = 10_000L,
                targetPositionMs = 30_000L,
                requestedAtMs = 1_000L,
                samplePositionMs = 30_020L,
                sampleCompletionTimeMs = 1_050L,
                speed = 1f
            )
        )
    }

    @Test
    fun staleSampleBeforeBackwardSeekIsRejected() {
        assertFalse(
            hasPlaybackSeekReachedTarget(
                fromPositionMs = 30_000L,
                targetPositionMs = 10_000L,
                requestedAtMs = 1_000L,
                samplePositionMs = 29_950L,
                sampleCompletionTimeMs = 1_050L,
                speed = 1f
            )
        )
    }

    @Test
    fun sampleAfterBackwardSeekMayHaveAdvancedFromItsTarget() {
        assertTrue(
            hasPlaybackSeekReachedTarget(
                fromPositionMs = 30_000L,
                targetPositionMs = 10_000L,
                requestedAtMs = 1_000L,
                samplePositionMs = 10_030L,
                sampleCompletionTimeMs = 1_050L,
                speed = 1f
            )
        )
    }
}
