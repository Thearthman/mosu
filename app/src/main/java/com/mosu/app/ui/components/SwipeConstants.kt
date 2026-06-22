package com.mosu.app.ui.components

import kotlin.math.abs

/**
 * Global swipe threshold configuration
 */
object SwipeThresholds {
    const val HORIZONTAL_LOCK_RATIO = 2.0f
    const val COMMIT_FRACTION = 0.40f
    const val MIN_COMMIT_THRESHOLD_DP = 96f
    const val MAX_COMMIT_THRESHOLD_DP = 180f

    fun hasHorizontalLock(dx: Float, dy: Float): Boolean {
        return abs(dx) >= abs(dy) * HORIZONTAL_LOCK_RATIO
    }

    fun commitThresholdPx(widthPx: Float, density: Float): Float {
        return (widthPx * COMMIT_FRACTION).coerceIn(
            MIN_COMMIT_THRESHOLD_DP * density,
            MAX_COMMIT_THRESHOLD_DP * density
        )
    }

    fun shouldCommit(offsetPx: Float, widthPx: Float, density: Float): Boolean {
        return abs(offsetPx) >= commitThresholdPx(widthPx, density)
    }
}

enum class BeatmapSwipeDirection {
    StartToEnd,
    EndToStart
}
