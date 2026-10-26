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
}
