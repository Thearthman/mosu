package com.mosu.app.domain.render

import kotlin.math.abs
import kotlin.math.max

/**
 * Converts Media3's quantised and occasionally stale media clock into a
 * frame-to-frame monotonic render clock.
 *
 * Between samples the clock predicts using the real frame delta multiplied by
 * the active playback speed. Small disagreements with Media3 are corrected
 * gradually; discontinuities (seeks, media changes, or a badly delayed sample)
 * snap immediately. Normal corrections never move backwards.
 */
class SmoothPlaybackClock(
    private val snapThresholdMs: Double = 160.0,
    private val correctionFraction: Double = 0.05,
    private val maximumFrameDeltaMs: Double = 100.0
) {
    private var initialised = false
    private var lastFrameNanos = 0L
    private var lastReportedPositionMs = 0L
    private var renderPositionMs = 0.0
    private var lastPlaybackSpeed = 1.0

    fun reset(
        reportedPositionMs: Long,
        frameNanos: Long,
        playbackSpeed: Float = 1f
    ) {
        val position = reportedPositionMs.coerceAtLeast(0L)
        initialised = true
        lastFrameNanos = frameNanos
        lastReportedPositionMs = position
        renderPositionMs = position.toDouble()
        lastPlaybackSpeed = normalisePlaybackSpeed(playbackSpeed)
    }

    /**
     * Returns the position to render for [frameNanos]. [playbackSpeed] is the
     * media speed (for example 1.5 for DT/NC), not wall-clock seconds.
     */
    fun update(
        frameNanos: Long,
        reportedPositionMs: Long,
        playbackSpeed: Float
    ): Double {
        val reported = reportedPositionMs.coerceAtLeast(0L)
        if (!initialised) {
            reset(reported, frameNanos)
            return renderPositionMs
        }

        val previousRenderPosition = renderPositionMs
        val elapsedMs = if (frameNanos >= lastFrameNanos) {
            (frameNanos - lastFrameNanos) / NANOS_PER_MILLI
        } else {
            0.0
        }
        val speed = normalisePlaybackSpeed(playbackSpeed)
        val predicted = previousRenderPosition + elapsedMs * speed
        val error = reported.toDouble() - predicted
        val hasFreshSample = reported > lastReportedPositionMs
        val reportedWentBackwards =
            lastReportedPositionMs - reported >= snapThresholdMs
        val frameGapTooLarge = elapsedMs > maximumFrameDeltaMs
        val speedChanged = abs(speed - lastPlaybackSpeed) > SPEED_CHANGE_EPSILON

        renderPositionMs = when {
            frameGapTooLarge ||
            reportedWentBackwards ||
                speedChanged ||
                error >= snapThresholdMs -> reported.toDouble()

            // Media3 can return the same millisecond sample for several
            // frames (or briefly regress by one tick). Keep advancing from
            // the monotonic frame clock instead of correcting toward a stale
            // sample and freezing/reversing the playfield.
            !hasFreshSample -> predicted

            else -> {
                // Integer millisecond timestamps naturally wobble by a small
                // amount frame to frame. Ignoring that quantisation avoids
                // visible micro-corrections while still converging on real
                // Media3 drift.
                if (abs(error) <= QUANTISATION_DEADBAND_MS) {
                    predicted
                } else {
                    max(
                        previousRenderPosition,
                        predicted + error * correctionFraction
                    )
                }
            }
        }

        lastFrameNanos = frameNanos
        lastReportedPositionMs = reported
        lastPlaybackSpeed = speed
        return renderPositionMs
    }

    private fun normalisePlaybackSpeed(playbackSpeed: Float): Double =
        if (playbackSpeed.isFinite() && playbackSpeed > 0f) {
            playbackSpeed.toDouble()
        } else {
            1.0
        }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000.0
        const val SPEED_CHANGE_EPSILON = 0.001
        const val QUANTISATION_DEADBAND_MS = 1.5
    }
}
