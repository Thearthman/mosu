package com.mosu.app.ui.components

import kotlin.math.abs

/**
 * Emits at most one scrollbar tick per item boundary crossed during a single drag.
 */
internal class ScrollbarHapticTracker {
    private var active = false
    private var lastItemIndex: Int? = null

    fun onDragStarted(initialItemIndex: Int? = null) {
        active = true
        lastItemIndex = initialItemIndex
    }

    fun onItemIndexChanged(itemIndex: Int?): Boolean {
        if (!active || itemIndex == null || itemIndex == lastItemIndex) return false
        lastItemIndex = itemIndex
        return true
    }

    fun onDragStopped() {
        active = false
        lastItemIndex = null
    }
}

internal enum class SwipeHapticEvent {
    GESTURE_START,
    THRESHOLD_ACTIVATE,
    THRESHOLD_DEACTIVATE,
    COMMIT,
    REJECT
}

/**
 * Tracks gesture boundaries independently from the swipe-to-dismiss decision logic.
 */
internal class SwipeHapticTracker {
    private var directionLocked = false
    private var thresholdActive = false
    private var maxOffsetPx = 0f

    fun onGestureStarted() {
        directionLocked = false
        thresholdActive = false
        maxOffsetPx = 0f
    }

    fun onDirectionLocked(): SwipeHapticEvent? {
        if (directionLocked) return null
        directionLocked = true
        return SwipeHapticEvent.GESTURE_START
    }

    fun onOffsetChanged(
        offsetPx: Float,
        thresholdPx: Float,
        meaningfulThresholdPx: Float = 0f
    ): SwipeHapticEvent? {
        if (!directionLocked) return null
        maxOffsetPx = maxOf(maxOffsetPx, abs(offsetPx))
        if (maxOffsetPx < meaningfulThresholdPx) return null
        if (thresholdPx <= 0f) return null

        val isActive = abs(offsetPx) >= thresholdPx
        if (isActive == thresholdActive) return null
        thresholdActive = isActive
        return if (isActive) {
            SwipeHapticEvent.THRESHOLD_ACTIVATE
        } else {
            SwipeHapticEvent.THRESHOLD_DEACTIVATE
        }
    }

    fun onRelease(
        committed: Boolean,
        meaningfulThresholdPx: Float = 0f
    ): SwipeHapticEvent? {
        val meaningful = directionLocked && maxOffsetPx >= meaningfulThresholdPx
        directionLocked = false
        thresholdActive = false
        maxOffsetPx = 0f
        if (!meaningful) return null
        return if (committed) SwipeHapticEvent.COMMIT else SwipeHapticEvent.REJECT
    }
}

/**
 * Tracks whether a playlist drag changed the ordering before it was dropped.
 */
internal class PlaylistReorderHapticTracker {
    private var active = false
    private var moved = false

    fun onDragStarted() {
        active = true
        moved = false
    }

    fun onTargetMoved() {
        if (active) moved = true
    }

    fun onDragFinished(): Boolean {
        val emitDrop = active && moved
        onDragCancelled()
        return emitDrop
    }

    fun onDragCancelled() {
        active = false
        moved = false
    }
}
