package com.mosu.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollbarHapticTrackerTest {
    @Test
    fun itemChangesAreIgnoredUntilAnActiveDragStarts() {
        val tracker = ScrollbarHapticTracker()

        assertFalse(tracker.onItemIndexChanged(2))
    }

    @Test
    fun firstBoundaryTicksButStartingAndRepeatedIndicesDoNot() {
        val tracker = ScrollbarHapticTracker()
        tracker.onDragStarted(initialItemIndex = 2)

        assertFalse(tracker.onItemIndexChanged(2))
        assertTrue(tracker.onItemIndexChanged(3))
        assertFalse(tracker.onItemIndexChanged(3))
    }

    @Test
    fun multiIndexJumpStillEmitsOnlyOneTick() {
        val tracker = ScrollbarHapticTracker()
        tracker.onDragStarted()

        assertTrue(tracker.onItemIndexChanged(8))
        assertFalse(tracker.onItemIndexChanged(8))
    }

    @Test
    fun nullIndicesAndInactiveDragsDoNotTick() {
        val tracker = ScrollbarHapticTracker()
        tracker.onDragStarted()

        assertFalse(tracker.onItemIndexChanged(null))

        tracker.onDragStopped()
        assertFalse(tracker.onItemIndexChanged(4))
    }

    @Test
    fun resetAllowsTheNextDragToTickAtItsFirstIndex() {
        val tracker = ScrollbarHapticTracker()
        tracker.onDragStarted()
        assertTrue(tracker.onItemIndexChanged(1))
        tracker.onDragStopped()

        tracker.onDragStarted()
        assertTrue(tracker.onItemIndexChanged(1))
    }
}
