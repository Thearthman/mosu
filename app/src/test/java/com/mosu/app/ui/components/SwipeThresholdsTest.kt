package com.mosu.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeThresholdsTest {
    @Test
    fun shortHorizontalMovementBelowDistanceThresholdDoesNotCommit() {
        assertFalse(
            SwipeThresholds.shouldCommit(
                offsetPx = 80f,
                widthPx = 1_000f,
                density = 1f
            )
        )
    }

    @Test
    fun diagonalDragFailsHorizontalLock() {
        assertFalse(SwipeThresholds.hasHorizontalLock(dx = 100f, dy = 60f))
    }

    @Test
    fun verticalDragFailsHorizontalLock() {
        assertFalse(SwipeThresholds.hasHorizontalLock(dx = 24f, dy = 160f))
    }

    @Test
    fun balancedHorizontalDragBeyondThresholdCommits() {
        assertTrue(SwipeThresholds.hasHorizontalLock(dx = 220f, dy = 50f))
        assertTrue(
            SwipeThresholds.shouldCommit(
                offsetPx = 220f,
                widthPx = 400f,
                density = 1f
            )
        )
    }

    @Test
    fun commitThresholdClampsForNarrowAndWideRows() {
        assertEquals(
            96f,
            SwipeThresholds.commitThresholdPx(widthPx = 200f, density = 1f),
            0.001f
        )
        assertEquals(
            180f,
            SwipeThresholds.commitThresholdPx(widthPx = 1_000f, density = 1f),
            0.001f
        )
    }
}
