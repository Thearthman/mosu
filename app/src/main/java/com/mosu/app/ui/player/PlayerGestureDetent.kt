package com.mosu.app.ui.player

import kotlin.math.abs
import kotlin.math.exp

internal const val PLAYER_VERTICAL_CRITICAL_EDGE_FRACTION = 0.35f
internal const val PLAYER_LANDSCAPE_CRITICAL_EDGE_FRACTION = 0.40f
internal const val PLAYER_CRITICAL_POINT_NEAR =
    PLAYER_VERTICAL_CRITICAL_EDGE_FRACTION
internal const val PLAYER_CRITICAL_POINT_FAR =
    1f - PLAYER_VERTICAL_CRITICAL_EDGE_FRACTION
internal const val PLAYER_DETENT_TOWARD_GAIN = 0.12f
internal const val PLAYER_DETENT_CATCH_UP_SPEED = 2.4f
internal const val PLAYER_LANDSCAPE_DETENT_CATCH_UP_SPEED = 3.6f
internal const val PLAYER_LANDSCAPE_CATCH_UP_OVERSHOOT_MINI_WIDTH_FRACTION = 0.5f
internal const val PLAYER_FLICK_VELOCITY_PX_PER_SECOND = 1_000f

private const val DETENT_FALLOFF_SIGMA = 0.22f
private const val DETENT_EDGE_FADE = 0.01f
private const val DETENT_INTEGRATION_STEP = 0.02f
private const val MAX_CATCH_UP_FRAME_SECONDS = 0.05f

internal enum class PlayerGestureDirection {
    EXPANDING,
    COLLAPSING
}

internal data class PlayerDetentAdvance(
    val progress: Float,
    val consumedRawDelta: Float,
    val rawProgress: Float
)

internal fun playerGestureCriticalPoint(
    direction: PlayerGestureDirection,
    criticalEdgeFraction: Float = PLAYER_VERTICAL_CRITICAL_EDGE_FRACTION
): Float {
    val edgeFraction = playerCriticalEdgeFractionOr(
        criticalEdgeFraction,
        PLAYER_VERTICAL_CRITICAL_EDGE_FRACTION
    )
    return when (direction) {
        PlayerGestureDirection.EXPANDING -> edgeFraction
        PlayerGestureDirection.COLLAPSING -> 1f - edgeFraction
    }
}

internal fun playerGestureDirectionForStart(progress: Float): PlayerGestureDirection {
    val normalized = playerProgressOrZero(progress)
    return if (normalized < 0.5f) {
        PlayerGestureDirection.EXPANDING
    } else {
        PlayerGestureDirection.COLLAPSING
    }
}

internal fun playerGestureDirectionForProgressChange(
    startProgress: Float,
    targetProgress: Float
): PlayerGestureDirection {
    val start = playerProgressOrZero(startProgress)
    val target = playerProgressOrZero(targetProgress)
    return when {
        target > start -> PlayerGestureDirection.EXPANDING
        target < start -> PlayerGestureDirection.COLLAPSING
        else -> playerGestureDirectionForStart(start)
    }
}

/**
 * Returns the visual progress delta's sensitivity around the direction's critical point.
 *
 * The critical point is measured in finger progress, not visual player progress. Moving the
 * finger toward that physical point is resisted. The resistance falls off with distance and
 * is normalized to normal sensitivity at the gesture's starting edge. Movement after the
 * committed side is handled by the time-based catch-up follower.
 */
internal fun playerDetentGain(
    fingerProgress: Float,
    direction: PlayerGestureDirection,
    fingerDelta: Float,
    criticalEdgeFraction: Float = PLAYER_VERTICAL_CRITICAL_EDGE_FRACTION
): Float {
    if (
        !fingerProgress.isFinite() ||
        !fingerDelta.isFinite() ||
        fingerDelta == 0f
    ) {
        return 1f
    }

    val normalized = fingerProgress.coerceIn(0f, 1f)
    val criticalPoint = playerGestureCriticalPoint(
        direction,
        criticalEdgeFraction
    )
    val distance = normalized - criticalPoint
    val falloff = exp(
        -(distance * distance) /
            (2f * DETENT_FALLOFF_SIGMA * DETENT_FALLOFF_SIGMA)
    )
    val startEdgeDistance = when (direction) {
        PlayerGestureDirection.EXPANDING -> normalized
        PlayerGestureDirection.COLLAPSING -> 1f - normalized
    }
    val edgeFade = (startEdgeDistance / DETENT_EDGE_FADE).coerceIn(0f, 1f)
    val strength = falloff * edgeFade
    val movingTowardCriticalPoint =
        (fingerDelta > 0f && normalized < criticalPoint) ||
            (fingerDelta < 0f && normalized > criticalPoint)

    return when {
        movingTowardCriticalPoint ->
            1f - (1f - PLAYER_DETENT_TOWARD_GAIN) * strength
        else -> 1f
    }
}

/**
 * Applies a detent to a raw progress delta while tracking how much raw movement was used.
 *
 * The consumed distance lets a two-stage landscape drag hand its exact remainder to the
 * next stage instead of treating the warped visual progress as raw pointer travel. The
 * active critical point is evaluated against [rawProgress], so resistance changes the
 * player's visual movement without moving the point where the finger itself crosses the
 * detent. Once a call crosses that point, visual movement stops until the caller advances
 * the time-based catch-up follower.
 */
internal fun advancePlayerDetent(
    progress: Float,
    rawDelta: Float,
    direction: PlayerGestureDirection,
    rawProgress: Float = progress,
    criticalEdgeFraction: Float = PLAYER_VERTICAL_CRITICAL_EDGE_FRACTION
): PlayerDetentAdvance {
    var current = playerProgressOrZero(progress)
    var raw = playerProgressOrZero(rawProgress)
    if (!rawDelta.isFinite() || rawDelta == 0f) {
        return PlayerDetentAdvance(current, 0f, raw)
    }

    var remaining = rawDelta
    var consumed = 0f
    val criticalPoint = playerGestureCriticalPoint(
        direction,
        criticalEdgeFraction
    )
    var crossedDuringCall = false
    while (abs(remaining) > 1e-6f) {
        if (crossedDuringCall) {
            val nextRaw = (raw + remaining).coerceIn(0f, 1f)
            val rawStep = nextRaw - raw
            if (abs(rawStep) <= 1e-6f) break
            raw = nextRaw
            consumed += rawStep
            remaining -= rawStep
            continue
        }

        val step = remaining.coerceIn(
            -DETENT_INTEGRATION_STEP,
            DETENT_INTEGRATION_STEP
        )
        if (
            (step > 0f && raw >= 1f && current >= 1f) ||
            (step < 0f && raw <= 0f && current <= 0f)
        ) {
            break
        }
        val rawCandidate = (raw + step).coerceIn(0f, 1f)
        val crossingStep = when (direction) {
            PlayerGestureDirection.EXPANDING ->
                if (raw < criticalPoint && rawCandidate >= criticalPoint) {
                    criticalPoint - raw
                } else {
                    null
                }
            PlayerGestureDirection.COLLAPSING ->
                if (raw > criticalPoint && rawCandidate <= criticalPoint) {
                    criticalPoint - raw
                } else {
                    null
                }
        }
        if (crossingStep != null) {
            val visualStep =
                crossingStep * playerDetentGain(
                    raw,
                    direction,
                    step,
                    criticalEdgeFraction
                )
            current = (current + visualStep).coerceIn(0f, 1f)
            raw = criticalPoint
            consumed += crossingStep
            remaining -= crossingStep
            crossedDuringCall = true
            continue
        }

        val visualStep = (rawCandidate - raw) *
            playerDetentGain(
                raw,
                direction,
                step,
                criticalEdgeFraction
            )
        val visualCandidate = current + visualStep
        if (visualCandidate !in 0f..1f) {
            val room = if (step < 0f) current else 1f - current
            if (room <= 1e-6f || abs(visualStep) <= 1e-6f) break
            val consumedStep = step * (room / abs(visualStep)).coerceIn(0f, 1f)
            consumed += consumedStep
            raw = (raw + consumedStep).coerceIn(0f, 1f)
            current = if (step < 0f) 0f else 1f
            break
        }
        current = visualCandidate
        raw = rawCandidate
        consumed += step
        remaining -= step
    }

    return PlayerDetentAdvance(
        progress = current.coerceIn(0f, 1f),
        consumedRawDelta = consumed,
        rawProgress = raw.coerceIn(0f, 1f)
    )
}

/**
 * Advances visual progress toward the physical finger target at a fixed speed.
 *
 * This is intentionally independent of how far or in which direction the finger moved since
 * the previous frame. Only [elapsedSeconds] and [catchUpSpeed] determine how quickly the
 * distance lost to the detent is recovered.
 */
internal fun advancePlayerDetentCatchUp(
    progress: Float,
    targetProgress: Float,
    elapsedSeconds: Float,
    catchUpSpeed: Float = PLAYER_DETENT_CATCH_UP_SPEED
): Float {
    val current = playerProgressOrZero(progress)
    if (
        !targetProgress.isFinite() ||
        !elapsedSeconds.isFinite() ||
        !catchUpSpeed.isFinite() ||
        elapsedSeconds <= 0f ||
        catchUpSpeed <= 0f
    ) {
        return current
    }
    val target = playerProgressOrZero(targetProgress)
    val maxStep = catchUpSpeed *
        elapsedSeconds.coerceAtMost(MAX_CATCH_UP_FRAME_SECONDS)
    return when {
        target > current -> (current + maxStep).coerceAtMost(target)
        target < current -> (current - maxStep).coerceAtLeast(target)
        else -> current
    }
}

/**
 * Returns the gesture's catch-up target with a fixed screen-space overshoot.
 *
 * The overshoot is a fraction of the landscape mini-player's width, then converted through
 * [stageDistancePx] so both the mini-to-compact and compact-to-fullscreen stages overshoot
 * by the same physical distance regardless of how much travel each stage covers.
 */
internal fun landscapePlayerCatchUpTarget(
    rawProgress: Float,
    direction: PlayerGestureDirection,
    stageDistancePx: Float,
    miniPlayerWidthPx: Float,
    overshootMiniWidthFraction: Float =
        PLAYER_LANDSCAPE_CATCH_UP_OVERSHOOT_MINI_WIDTH_FRACTION
): Float {
    val raw = playerProgressOrZero(rawProgress)
    val stageDistance = if (stageDistancePx.isFinite()) {
        stageDistancePx.coerceAtLeast(0f)
    } else {
        0f
    }
    if (stageDistance <= 0f) return raw
    val miniPlayerWidth = if (miniPlayerWidthPx.isFinite()) {
        miniPlayerWidthPx.coerceAtLeast(0f)
    } else {
        0f
    }
    val widthFraction = if (overshootMiniWidthFraction.isFinite()) {
        overshootMiniWidthFraction.coerceAtLeast(0f)
    } else {
        PLAYER_LANDSCAPE_CATCH_UP_OVERSHOOT_MINI_WIDTH_FRACTION
    }
    val overshootProgress =
        miniPlayerWidth * widthFraction / stageDistance
    return when (direction) {
        PlayerGestureDirection.EXPANDING ->
            (raw + overshootProgress).coerceAtMost(1f)
        PlayerGestureDirection.COLLAPSING ->
            (raw - overshootProgress).coerceAtLeast(0f)
    }
}

internal fun advancePlayerRawProgress(
    progress: Float,
    rawDelta: Float
): Float {
    if (!rawDelta.isFinite()) return playerProgressOrZero(progress)
    return (playerProgressOrZero(progress) + rawDelta).coerceIn(0f, 1f)
}

internal fun didCrossPlayerCriticalPoint(
    previousProgress: Float,
    currentProgress: Float,
    criticalPoint: Float
): Boolean {
    if (
        !previousProgress.isFinite() ||
        !currentProgress.isFinite() ||
        !criticalPoint.isFinite()
    ) {
        return false
    }
    val threshold = criticalPoint.coerceIn(0f, 1f)
    return (
        previousProgress < threshold &&
            currentProgress >= threshold
        ) || (
        previousProgress > threshold &&
            currentProgress <= threshold
        )
}

internal fun resolvePlayerExpandedOnRelease(
    progress: Float,
    velocityPxPerSecond: Float,
    direction: PlayerGestureDirection,
    criticalEdgeFraction: Float = PLAYER_VERTICAL_CRITICAL_EDGE_FRACTION
): Boolean {
    if (velocityPxPerSecond.isFinite()) {
        if (velocityPxPerSecond < -PLAYER_FLICK_VELOCITY_PX_PER_SECOND) return true
        if (velocityPxPerSecond > PLAYER_FLICK_VELOCITY_PX_PER_SECOND) return false
    }

    val normalized = playerProgressOrZero(progress)
    val criticalPoint = playerGestureCriticalPoint(
        direction,
        criticalEdgeFraction
    )
    return when (direction) {
        PlayerGestureDirection.EXPANDING -> normalized >= criticalPoint
        PlayerGestureDirection.COLLAPSING -> normalized > criticalPoint
    }
}

internal fun playerProgressFromSheetOffset(
    sheetOffsetPx: Float,
    collapsedOffsetPx: Float
): Float {
    if (
        !sheetOffsetPx.isFinite() ||
        !collapsedOffsetPx.isFinite() ||
        collapsedOffsetPx <= 0f
    ) {
        return 0f
    }
    return (1f - sheetOffsetPx / collapsedOffsetPx).coerceIn(0f, 1f)
}

internal fun playerSheetOffsetFromProgress(
    progress: Float,
    collapsedOffsetPx: Float
): Float {
    if (!collapsedOffsetPx.isFinite() || collapsedOffsetPx <= 0f) return 0f
    val normalized = playerProgressOrZero(progress)
    return (1f - normalized) * collapsedOffsetPx
}

internal class PlayerCriticalPointHapticTracker {
    private var session = 0
    private var criticalPoint: Float? = null
    private var previousProgress: Float? = null

    fun begin(progress: Float, criticalPoint: Float): Int {
        session += 1
        this.criticalPoint = criticalPoint
        previousProgress = progress
        return session
    }

    fun end(activeSession: Int) {
        if (activeSession != session) return
        criticalPoint = null
        previousProgress = null
    }

    fun observe(progress: Float): Boolean {
        val threshold = criticalPoint ?: return false
        val previous = previousProgress
        previousProgress = progress
        if (previous == null) return false
        return didCrossPlayerCriticalPoint(previous, progress, threshold)
    }
}

private fun playerProgressOrZero(progress: Float): Float {
    return if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
}

private fun playerCriticalEdgeFractionOr(
    criticalEdgeFraction: Float,
    fallback: Float
): Float {
    return if (criticalEdgeFraction.isFinite()) {
        criticalEdgeFraction.coerceIn(0f, 0.5f)
    } else {
        fallback
    }
}
