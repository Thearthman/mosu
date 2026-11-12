package com.mosu.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PlayerGestureDetentTest {
    @Test
    fun criticalPointIsDirectional() {
        assertEquals(
            0.28f,
            PLAYER_VERTICAL_CRITICAL_EDGE_FRACTION,
            0.0001f
        )
        assertEquals(
            0.28f,
            playerGestureCriticalPoint(PlayerGestureDirection.EXPANDING),
            0.0001f
        )
        assertEquals(
            0.72f,
            playerGestureCriticalPoint(PlayerGestureDirection.COLLAPSING),
            0.0001f
        )
        assertEquals(
            PLAYER_CRITICAL_POINT_NEAR,
            playerGestureCriticalPoint(PlayerGestureDirection.EXPANDING),
            0.0001f
        )
        assertEquals(
            PLAYER_CRITICAL_POINT_FAR,
            playerGestureCriticalPoint(PlayerGestureDirection.COLLAPSING),
            0.0001f
        )
        assertEquals(
            0.40f,
            playerGestureCriticalPoint(
                PlayerGestureDirection.EXPANDING,
                PLAYER_LANDSCAPE_CRITICAL_EDGE_FRACTION
            ),
            0.0001f
        )
        assertEquals(
            0.60f,
            playerGestureCriticalPoint(
                PlayerGestureDirection.COLLAPSING,
                PLAYER_LANDSCAPE_CRITICAL_EDGE_FRACTION
            ),
            0.0001f
        )
    }

    @Test
    fun detentSlowsMovementTowardBothCriticalPoints() {
        val expanding = playerDetentGain(
            fingerProgress = PLAYER_CRITICAL_POINT_NEAR - 0.001f,
            direction = PlayerGestureDirection.EXPANDING,
            fingerDelta = 0.01f
        )
        val collapsing = playerDetentGain(
            fingerProgress = PLAYER_CRITICAL_POINT_FAR + 0.001f,
            direction = PlayerGestureDirection.COLLAPSING,
            fingerDelta = -0.01f
        )

        assertEquals(PLAYER_DETENT_TOWARD_GAIN, expanding, 0.01f)
        assertEquals(PLAYER_DETENT_TOWARD_GAIN, collapsing, 0.01f)
    }

    @Test
    fun detentUsesNormalSensitivityAfterCrossing() {
        val expanding = playerDetentGain(
            fingerProgress = PLAYER_CRITICAL_POINT_NEAR,
            direction = PlayerGestureDirection.EXPANDING,
            fingerDelta = 0.01f
        )
        val collapsing = playerDetentGain(
            fingerProgress = PLAYER_CRITICAL_POINT_FAR,
            direction = PlayerGestureDirection.COLLAPSING,
            fingerDelta = -0.01f
        )

        assertEquals(1f, expanding, 0.0001f)
        assertEquals(1f, collapsing, 0.0001f)
    }

    @Test
    fun resistanceBuildsWellBeforeTheCriticalPoint() {
        val atStartOfScroll = playerDetentGain(
            fingerProgress = 0.02f,
            direction = PlayerGestureDirection.EXPANDING,
            fingerDelta = 0.01f
        )
        val early = playerDetentGain(
            fingerProgress = PLAYER_CRITICAL_POINT_NEAR - 0.18f,
            direction = PlayerGestureDirection.EXPANDING,
            fingerDelta = 0.01f
        )
        val near = playerDetentGain(
            fingerProgress = PLAYER_CRITICAL_POINT_NEAR - 0.12f,
            direction = PlayerGestureDirection.EXPANDING,
            fingerDelta = 0.01f
        )

        assertTrue(atStartOfScroll < 0.80f)
        assertTrue(early < 0.80f)
        assertTrue(near < early)
    }

    @Test
    fun physicalFingerProgressKeepsItsFullDistanceThroughTheDetent() {
        val advance = advancePlayerDetent(
            progress = 0f,
            rawDelta = 0.1f,
            direction = PlayerGestureDirection.EXPANDING,
            rawProgress = 0f
        )

        assertEquals(0.1f, advance.rawProgress, 0.0001f)
        assertEquals(0.1f, advance.consumedRawDelta, 0.0001f)
        assertTrue(advance.progress < advance.rawProgress)
    }

    @Test
    fun fingerProgressCrossesBeforeTimeBasedCatchUp() {
        var expandingProgress = 0f
        var expandingRawProgress = 0f
        var expandingCrossed = false
        var expandingVisualAtCrossing = 1f
        var expandingGapAtCrossing = 0f
        repeat(100) {
            val previousRawProgress = expandingRawProgress
            val next = advancePlayerDetent(
                progress = expandingProgress,
                rawDelta = 0.01f,
                direction = PlayerGestureDirection.EXPANDING,
                rawProgress = expandingRawProgress
            )
            expandingProgress = next.progress
            expandingRawProgress = next.rawProgress
            if (
                previousRawProgress < PLAYER_CRITICAL_POINT_NEAR &&
                expandingRawProgress >= PLAYER_CRITICAL_POINT_NEAR
            ) {
                expandingCrossed = true
                expandingVisualAtCrossing = expandingProgress
                expandingGapAtCrossing =
                    expandingRawProgress - expandingProgress
                assertTrue(
                    expandingProgress <
                        PLAYER_CRITICAL_POINT_NEAR
                )
                assertEquals(
                    PLAYER_CRITICAL_POINT_NEAR,
                    expandingRawProgress,
                    0.01f
                )
                    assertTrue(expandingRawProgress > expandingProgress)
                }
            assertTrue(expandingProgress <= expandingRawProgress + 0.0001f)
        }
        assertTrue(expandingCrossed)
        assertTrue(
            expandingVisualAtCrossing <
                PLAYER_CRITICAL_POINT_NEAR
        )
        assertTrue(expandingGapAtCrossing > 0.05f)
        repeat(20) {
            expandingProgress = advancePlayerDetentCatchUp(
                progress = expandingProgress,
                targetProgress = expandingRawProgress,
                elapsedSeconds = 1f / 60f
            )
        }
        assertTrue(
            abs(expandingRawProgress - expandingProgress) < 0.01f
        )

        var collapsingProgress = 1f
        var collapsingRawProgress = 1f
        var collapsingCrossed = false
        var collapsingVisualAtCrossing = 0f
        var collapsingGapAtCrossing = 0f
        repeat(100) {
            val previousRawProgress = collapsingRawProgress
            val next = advancePlayerDetent(
                progress = collapsingProgress,
                rawDelta = -0.01f,
                direction = PlayerGestureDirection.COLLAPSING,
                rawProgress = collapsingRawProgress
            )
            collapsingProgress = next.progress
            collapsingRawProgress = next.rawProgress
            if (
                previousRawProgress > PLAYER_CRITICAL_POINT_FAR &&
                collapsingRawProgress <= PLAYER_CRITICAL_POINT_FAR
            ) {
                collapsingCrossed = true
                collapsingVisualAtCrossing = collapsingProgress
                collapsingGapAtCrossing =
                    collapsingProgress - collapsingRawProgress
                assertTrue(
                    collapsingProgress >
                        PLAYER_CRITICAL_POINT_FAR
                )
                assertEquals(
                    PLAYER_CRITICAL_POINT_FAR,
                    collapsingRawProgress,
                    0.01f
                )
                    assertTrue(collapsingProgress > collapsingRawProgress)
                }
            assertTrue(collapsingProgress >= collapsingRawProgress - 0.0001f)
        }
        assertTrue(collapsingCrossed)
        assertTrue(
            collapsingVisualAtCrossing >
                PLAYER_CRITICAL_POINT_FAR
        )
        assertTrue(collapsingGapAtCrossing > 0.05f)
        repeat(20) {
            collapsingProgress = advancePlayerDetentCatchUp(
                progress = collapsingProgress,
                targetProgress = collapsingRawProgress,
                elapsedSeconds = 1f / 60f
            )
        }
        assertTrue(
            abs(collapsingRawProgress - collapsingProgress) < 0.01f
        )
    }

    @Test
    fun catchUpDistanceDependsOnlyOnSpeedAndElapsedTime() {
        val elapsedSeconds = 1f / 60f
        val nearTarget = advancePlayerDetentCatchUp(
            progress = 0.1f,
            targetProgress = 0.11f,
            elapsedSeconds = elapsedSeconds
        )
        val farTarget = advancePlayerDetentCatchUp(
            progress = 0.1f,
            targetProgress = 0.9f,
            elapsedSeconds = elapsedSeconds
        )

        assertEquals(
            PLAYER_DETENT_CATCH_UP_SPEED * elapsedSeconds,
            farTarget - 0.1f,
            0.0001f
        )
        assertEquals(0.11f, nearTarget, 0.0001f)
        assertEquals(
            elapsedSeconds * PLAYER_DETENT_CATCH_UP_SPEED * 2f,
            advancePlayerDetentCatchUp(
                progress = 0.1f,
                targetProgress = 0.9f,
                elapsedSeconds = elapsedSeconds,
                catchUpSpeed = PLAYER_DETENT_CATCH_UP_SPEED * 2f
            ) - 0.1f,
            0.0001f
        )
        assertTrue(
            PLAYER_LANDSCAPE_DETENT_CATCH_UP_SPEED >
                PLAYER_DETENT_CATCH_UP_SPEED
        )
        assertEquals(
            elapsedSeconds * PLAYER_LANDSCAPE_DETENT_CATCH_UP_SPEED,
            advancePlayerDetentCatchUp(
                progress = 0.1f,
                targetProgress = 0.9f,
                elapsedSeconds = elapsedSeconds,
                catchUpSpeed = PLAYER_LANDSCAPE_DETENT_CATCH_UP_SPEED
            ) - 0.1f,
            0.0001f
        )
    }

    @Test
    fun landscapeCatchUpOvershootsFingerByHalfTheMiniPlayerWidth() {
        val rawProgress = 0.5f
        val stageDistancePx = 600f
        val miniPlayerWidthPx = 72f
        val expandingTarget = landscapePlayerCatchUpTarget(
            rawProgress = rawProgress,
            direction = PlayerGestureDirection.EXPANDING,
            stageDistancePx = stageDistancePx,
            miniPlayerWidthPx = miniPlayerWidthPx
        )
        val collapsingTarget = landscapePlayerCatchUpTarget(
            rawProgress = rawProgress,
            direction = PlayerGestureDirection.COLLAPSING,
            stageDistancePx = stageDistancePx,
            miniPlayerWidthPx = miniPlayerWidthPx
        )
        var caughtUp = rawProgress - 0.04f
        repeat(2) {
            caughtUp = advancePlayerDetentCatchUp(
                progress = caughtUp,
                targetProgress = expandingTarget,
                elapsedSeconds = 1f / 60f
            )
        }

        assertEquals(
            rawProgress + 0.06f,
            expandingTarget,
            0.0001f
        )
        assertEquals(
            rawProgress - 0.06f,
            collapsingTarget,
            0.0001f
        )
        assertEquals(
            miniPlayerWidthPx / 2f,
            (expandingTarget - rawProgress) * stageDistancePx,
            0.0001f
        )
        assertTrue(caughtUp > rawProgress)
        assertEquals(
            1f,
            landscapePlayerCatchUpTarget(
                rawProgress = 0.98f,
                direction = PlayerGestureDirection.EXPANDING,
                stageDistancePx = stageDistancePx,
                miniPlayerWidthPx = miniPlayerWidthPx
            ),
            0.0001f
        )
        assertEquals(
            0f,
            landscapePlayerCatchUpTarget(
                rawProgress = 0.02f,
                direction = PlayerGestureDirection.COLLAPSING,
                stageDistancePx = stageDistancePx,
                miniPlayerWidthPx = miniPlayerWidthPx
            ),
            0.0001f
        )
        assertEquals(
            rawProgress,
            landscapePlayerCatchUpTarget(
                rawProgress = rawProgress,
                direction = PlayerGestureDirection.EXPANDING,
                stageDistancePx = 0f,
                miniPlayerWidthPx = miniPlayerWidthPx
            ),
            0.0001f
        )
        assertEquals(
            rawProgress,
            landscapePlayerCatchUpTarget(
                rawProgress = rawProgress,
                direction = PlayerGestureDirection.COLLAPSING,
                stageDistancePx = stageDistancePx,
                miniPlayerWidthPx = Float.NaN
            ),
            0.0001f
        )
    }

    @Test
    fun detentSensitivityIsNormalizedAtTheGestureStartingEdges() {
        assertEquals(
            1f,
            playerDetentGain(
                fingerProgress = 0f,
                direction = PlayerGestureDirection.EXPANDING,
                fingerDelta = 0.01f
            ),
            0.0001f
        )
        assertEquals(
            1f,
            playerDetentGain(
                fingerProgress = 1f,
                direction = PlayerGestureDirection.EXPANDING,
                fingerDelta = 0.01f
            ),
            0.0001f
        )
        assertEquals(
            1f,
            playerDetentGain(
                fingerProgress = 1f,
                direction = PlayerGestureDirection.COLLAPSING,
                fingerDelta = -0.01f
            ),
            0.0001f
        )
        assertEquals(
            1f,
            playerDetentGain(
                fingerProgress = 0f,
                direction = PlayerGestureDirection.COLLAPSING,
                fingerDelta = -0.01f
            ),
            0.0001f
        )
    }

    @Test
    fun detentAdvanceClampsAndHandlesNonFiniteInput() {
        val expanded = advancePlayerDetent(
            progress = 0.05f,
            rawDelta = 10f,
            direction = PlayerGestureDirection.EXPANDING,
            rawProgress = 0.05f
        )
        assertEquals(1f, expanded.rawProgress, 0.0001f)
        assertTrue(expanded.progress < PLAYER_CRITICAL_POINT_NEAR)
        assertTrue(expanded.consumedRawDelta > 0f)
        assertTrue(expanded.consumedRawDelta < 10f)

        val collapsed = advancePlayerDetent(
            progress = 0.95f,
            rawDelta = -10f,
            direction = PlayerGestureDirection.COLLAPSING,
            rawProgress = 0.95f
        )
        assertEquals(0f, collapsed.rawProgress, 0.0001f)
        assertTrue(collapsed.progress > PLAYER_CRITICAL_POINT_FAR)
        assertTrue(collapsed.consumedRawDelta < 0f)

        val unchanged = advancePlayerDetent(
            progress = 0.4f,
            rawDelta = Float.NaN,
            direction = PlayerGestureDirection.EXPANDING
        )
        assertEquals(0.4f, unchanged.progress, 0.0001f)
        assertEquals(0f, unchanged.consumedRawDelta, 0.0001f)

        val nonFiniteProgress = advancePlayerDetent(
            progress = Float.POSITIVE_INFINITY,
            rawDelta = 0.1f,
            direction = PlayerGestureDirection.EXPANDING
        )
        assertTrue(nonFiniteProgress.progress.isFinite())
    }

    @Test
    fun thresholdCrossingFiresOncePerSideAndDetectsBothDirections() {
        assertTrue(
            didCrossPlayerCriticalPoint(
                previousProgress = PLAYER_CRITICAL_POINT_NEAR - 0.01f,
                currentProgress = PLAYER_CRITICAL_POINT_NEAR,
                criticalPoint = PLAYER_CRITICAL_POINT_NEAR
            )
        )
        assertFalse(
            didCrossPlayerCriticalPoint(
                previousProgress = PLAYER_CRITICAL_POINT_NEAR,
                currentProgress = PLAYER_CRITICAL_POINT_NEAR + 0.01f,
                criticalPoint = PLAYER_CRITICAL_POINT_NEAR
            )
        )
        assertTrue(
            didCrossPlayerCriticalPoint(
                previousProgress = PLAYER_CRITICAL_POINT_FAR + 0.01f,
                currentProgress = PLAYER_CRITICAL_POINT_FAR,
                criticalPoint = PLAYER_CRITICAL_POINT_FAR
            )
        )
        assertFalse(
            didCrossPlayerCriticalPoint(
                previousProgress = PLAYER_CRITICAL_POINT_FAR,
                currentProgress = PLAYER_CRITICAL_POINT_FAR - 0.01f,
                criticalPoint = PLAYER_CRITICAL_POINT_FAR
            )
        )
        assertFalse(
            didCrossPlayerCriticalPoint(
                Float.NaN,
                PLAYER_CRITICAL_POINT_NEAR,
                PLAYER_CRITICAL_POINT_NEAR
            )
        )
    }

    @Test
    fun trackerDoesNotRepeatWhileStayingOnOneSide() {
        val tracker = PlayerCriticalPointHapticTracker()
        val session = tracker.begin(
            progress = 0.1f,
            criticalPoint = PLAYER_CRITICAL_POINT_NEAR
        )

        assertFalse(tracker.observe(PLAYER_CRITICAL_POINT_NEAR - 0.05f))
        assertFalse(tracker.observe(PLAYER_CRITICAL_POINT_NEAR - 0.01f))
        assertTrue(tracker.observe(PLAYER_CRITICAL_POINT_NEAR + 0.01f))
        assertFalse(tracker.observe(PLAYER_CRITICAL_POINT_NEAR + 0.05f))
        assertFalse(tracker.observe(PLAYER_CRITICAL_POINT_NEAR + 0.03f))

        tracker.end(session)
        assertFalse(tracker.observe(0.1f))
    }

    @Test
    fun trackerStaysSilentWhenStartingPastTheActiveThreshold() {
        val tracker = PlayerCriticalPointHapticTracker()
        val session = tracker.begin(
            progress = 0.9f,
            criticalPoint = PLAYER_CRITICAL_POINT_FAR
        )

        assertFalse(tracker.observe(0.95f))
        assertFalse(tracker.observe(0.85f))
        tracker.end(session)
    }

    @Test
    fun slowReleaseUsesDirectionalCriticalPoint() {
        assertFalse(
            resolvePlayerExpandedOnRelease(
                progress = PLAYER_CRITICAL_POINT_NEAR - 0.001f,
                velocityPxPerSecond = 0f,
                direction = PlayerGestureDirection.EXPANDING
            )
        )
        assertTrue(
            resolvePlayerExpandedOnRelease(
                progress = PLAYER_CRITICAL_POINT_NEAR,
                velocityPxPerSecond = 0f,
                direction = PlayerGestureDirection.EXPANDING
            )
        )
        assertTrue(
            resolvePlayerExpandedOnRelease(
                progress = PLAYER_CRITICAL_POINT_FAR + 0.001f,
                velocityPxPerSecond = 0f,
                direction = PlayerGestureDirection.COLLAPSING
            )
        )
        assertFalse(
            resolvePlayerExpandedOnRelease(
                progress = PLAYER_CRITICAL_POINT_FAR,
                velocityPxPerSecond = 0f,
                direction = PlayerGestureDirection.COLLAPSING
            )
        )
    }

    @Test
    fun landscapeReleaseUsesFortyPercentFromTheStartingEdge() {
        assertFalse(
            resolvePlayerExpandedOnRelease(
                progress = 0.399f,
                velocityPxPerSecond = 0f,
                direction = PlayerGestureDirection.EXPANDING,
                criticalEdgeFraction =
                    PLAYER_LANDSCAPE_CRITICAL_EDGE_FRACTION
            )
        )
        assertTrue(
            resolvePlayerExpandedOnRelease(
                progress = 0.40f,
                velocityPxPerSecond = 0f,
                direction = PlayerGestureDirection.EXPANDING,
                criticalEdgeFraction =
                    PLAYER_LANDSCAPE_CRITICAL_EDGE_FRACTION
            )
        )
        assertTrue(
            resolvePlayerExpandedOnRelease(
                progress = 0.601f,
                velocityPxPerSecond = 0f,
                direction = PlayerGestureDirection.COLLAPSING,
                criticalEdgeFraction =
                    PLAYER_LANDSCAPE_CRITICAL_EDGE_FRACTION
            )
        )
        assertFalse(
            resolvePlayerExpandedOnRelease(
                progress = 0.60f,
                velocityPxPerSecond = 0f,
                direction = PlayerGestureDirection.COLLAPSING,
                criticalEdgeFraction =
                    PLAYER_LANDSCAPE_CRITICAL_EDGE_FRACTION
            )
        )
    }

    @Test
    fun fastFlickOverridesSlowReleaseOnBothDirections() {
        assertTrue(
            resolvePlayerExpandedOnRelease(
                progress = 0f,
                velocityPxPerSecond = -1_001f,
                direction = PlayerGestureDirection.COLLAPSING
            )
        )
        assertFalse(
            resolvePlayerExpandedOnRelease(
                progress = 1f,
                velocityPxPerSecond = 1_001f,
                direction = PlayerGestureDirection.EXPANDING
            )
        )
        assertFalse(
            resolvePlayerExpandedOnRelease(
                progress = 0f,
                velocityPxPerSecond = -1_000f,
                direction = PlayerGestureDirection.EXPANDING
            )
        )
    }

    @Test
    fun progressAndOffsetConversionClampsInvalidSpans() {
        assertEquals(
            0f,
            playerProgressFromSheetOffset(120f, 120f),
            0.0001f
        )
        assertEquals(
            1f,
            playerProgressFromSheetOffset(0f, 120f),
            0.0001f
        )
        assertEquals(
            120f,
            playerSheetOffsetFromProgress(0f, 120f),
            0.0001f
        )
        assertEquals(
            0f,
            playerSheetOffsetFromProgress(1f, 120f),
            0.0001f
        )
        assertEquals(
            0f,
            playerProgressFromSheetOffset(10f, 0f),
            0.0001f
        )
        assertEquals(
            0f,
            playerSheetOffsetFromProgress(0.5f, Float.NaN),
            0.0001f
        )
    }
}
