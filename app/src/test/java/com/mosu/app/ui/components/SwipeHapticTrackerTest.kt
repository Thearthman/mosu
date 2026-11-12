package com.mosu.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeHapticTrackerTest {
    @Test
    fun directionLockEmitsGestureStartOnlyOnce() {
        val tracker = SwipeHapticTracker()
        tracker.onGestureStarted()

        assertEquals(SwipeHapticEvent.GESTURE_START, tracker.onDirectionLocked())
        assertNull(tracker.onDirectionLocked())
    }

    @Test
    fun thresholdActivationAndDeactivationAreEdgeTriggered() {
        val tracker = SwipeHapticTracker()
        tracker.onGestureStarted()
        tracker.onDirectionLocked()

        assertEquals(
            SwipeHapticEvent.THRESHOLD_ACTIVATE,
            tracker.onOffsetChanged(offsetPx = 120f, thresholdPx = 100f, meaningfulThresholdPx = 10f)
        )
        assertNull(
            tracker.onOffsetChanged(offsetPx = 140f, thresholdPx = 100f, meaningfulThresholdPx = 10f)
        )
        assertEquals(
            SwipeHapticEvent.THRESHOLD_DEACTIVATE,
            tracker.onOffsetChanged(offsetPx = 40f, thresholdPx = 100f, meaningfulThresholdPx = 10f)
        )
        assertNull(
            tracker.onOffsetChanged(offsetPx = 20f, thresholdPx = 100f, meaningfulThresholdPx = 10f)
        )
    }

    @Test
    fun committedReleaseEmitsCommit() {
        val tracker = SwipeHapticTracker()
        tracker.onGestureStarted()
        tracker.onDirectionLocked()
        tracker.onOffsetChanged(offsetPx = 150f, thresholdPx = 100f, meaningfulThresholdPx = 10f)

        assertEquals(
            SwipeHapticEvent.COMMIT,
            tracker.onRelease(committed = true, meaningfulThresholdPx = 10f)
        )
    }

    @Test
    fun rejectedReleaseEmitsReject() {
        val tracker = SwipeHapticTracker()
        tracker.onGestureStarted()
        tracker.onDirectionLocked()
        tracker.onOffsetChanged(offsetPx = 50f, thresholdPx = 100f, meaningfulThresholdPx = 10f)

        assertEquals(
            SwipeHapticEvent.REJECT,
            tracker.onRelease(committed = false, meaningfulThresholdPx = 10f)
        )
    }

    @Test
    fun tinyDragDoesNotEmitThresholdOrReleaseFeedback() {
        val tracker = SwipeHapticTracker()
        tracker.onGestureStarted()
        tracker.onDirectionLocked()

        assertNull(
            tracker.onOffsetChanged(offsetPx = 5f, thresholdPx = 100f, meaningfulThresholdPx = 10f)
        )
        assertNull(tracker.onRelease(committed = false, meaningfulThresholdPx = 10f))
    }
}
