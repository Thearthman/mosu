package com.mosu.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistReorderHapticTrackerTest {
    @Test
    fun dragWithoutMovementDoesNotEmitDropFeedback() {
        val tracker = PlaylistReorderHapticTracker()
        tracker.onDragStarted()

        assertFalse(tracker.onDragFinished())
    }

    @Test
    fun movedDragEmitsDropFeedbackOnlyOnce() {
        val tracker = PlaylistReorderHapticTracker()
        tracker.onDragStarted()
        tracker.onTargetMoved()

        assertTrue(tracker.onDragFinished())
        assertFalse(tracker.onDragFinished())
    }

    @Test
    fun cancelledDragResetsMovementState() {
        val tracker = PlaylistReorderHapticTracker()
        tracker.onDragStarted()
        tracker.onTargetMoved()
        tracker.onDragCancelled()

        assertFalse(tracker.onDragFinished())
    }
}
