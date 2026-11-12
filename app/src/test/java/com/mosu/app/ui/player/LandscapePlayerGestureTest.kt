package com.mosu.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LandscapePlayerGestureTest {
    @Test
    fun everyReleaseBandAtEveryStartStageAndDirection() {
        val speeds = listOf(
            999f,
            1_000f,
            1_999f,
            2_000f,
            3_999f,
            4_000f
        )

        speeds.forEach { speed ->
            assertRelease(
                startStage = LandscapePlayerStage.MINI,
                compactProgress = 0f,
                expansionProgress = 0f,
                velocity = -speed,
                expectedTarget = if (speed >= 4_000f) {
                    LandscapePlayerStage.FULL
                } else if (speed >= 1_000f) {
                    LandscapePlayerStage.COMPACT
                } else {
                    LandscapePlayerStage.MINI
                },
                expectedSpansBothStages = speed >= 4_000f
            )
            assertRelease(
                startStage = LandscapePlayerStage.MINI,
                compactProgress = 0f,
                expansionProgress = 0f,
                velocity = speed,
                expectedTarget = LandscapePlayerStage.MINI
            )
            assertRelease(
                startStage = LandscapePlayerStage.COMPACT,
                compactProgress = 1f,
                expansionProgress = 0f,
                velocity = -speed,
                expectedTarget = if (speed >= 4_000f) {
                    LandscapePlayerStage.FULL
                } else {
                    LandscapePlayerStage.COMPACT
                }
            )
            assertRelease(
                startStage = LandscapePlayerStage.COMPACT,
                compactProgress = 1f,
                expansionProgress = 0f,
                velocity = speed,
                expectedTarget = if (speed >= 1_000f) {
                    LandscapePlayerStage.MINI
                } else {
                    LandscapePlayerStage.COMPACT
                }
            )
            assertRelease(
                startStage = LandscapePlayerStage.FULL,
                compactProgress = 1f,
                expansionProgress = 1f,
                velocity = -speed,
                expectedTarget = LandscapePlayerStage.FULL
            )
            assertRelease(
                startStage = LandscapePlayerStage.FULL,
                compactProgress = 1f,
                expansionProgress = 1f,
                velocity = speed,
                expectedTarget = when {
                    speed >= 4_000f -> LandscapePlayerStage.MINI
                    speed >= 1_000f -> LandscapePlayerStage.COMPACT
                    else -> LandscapePlayerStage.FULL
                },
                expectedSpansBothStages = speed >= 4_000f
            )
        }
    }

    @Test
    fun miniReleaseUsesMediumFlickForCompactAndFastFlickForFull() {
        assertRelease(
            startStage = LandscapePlayerStage.MINI,
            compactProgress = 0.39f,
            expansionProgress = 0f,
            velocity = -999f,
            expectedTarget = LandscapePlayerStage.MINI
        )
        assertRelease(
            startStage = LandscapePlayerStage.MINI,
            compactProgress = 0f,
            expansionProgress = 0f,
            velocity = -1_000f,
            expectedTarget = LandscapePlayerStage.COMPACT
        )
        assertRelease(
            startStage = LandscapePlayerStage.MINI,
            compactProgress = 0f,
            expansionProgress = 0f,
            velocity = -1_999f,
            expectedTarget = LandscapePlayerStage.COMPACT
        )
        assertRelease(
            startStage = LandscapePlayerStage.MINI,
            compactProgress = 0f,
            expansionProgress = 0f,
            velocity = -2_000f,
            expectedTarget = LandscapePlayerStage.COMPACT
        )
        assertRelease(
            startStage = LandscapePlayerStage.MINI,
            compactProgress = 0f,
            expansionProgress = 0f,
            velocity = -3_999f,
            expectedTarget = LandscapePlayerStage.COMPACT
        )
        assertRelease(
            startStage = LandscapePlayerStage.MINI,
            compactProgress = 0f,
            expansionProgress = 0f,
            velocity = -4_000f,
            expectedTarget = LandscapePlayerStage.FULL,
            expectedSpansBothStages = true
        )
        assertRelease(
            startStage = LandscapePlayerStage.MINI,
            compactProgress = 0.39f,
            expansionProgress = 0f,
            velocity = 4_000f,
            expectedTarget = LandscapePlayerStage.MINI
        )
    }

    @Test
    fun compactReleaseUsesMediumFlickOnlyTowardMini() {
        assertRelease(
            startStage = LandscapePlayerStage.COMPACT,
            compactProgress = 1f,
            expansionProgress = 0.39f,
            velocity = -999f,
            expectedTarget = LandscapePlayerStage.COMPACT
        )
        assertRelease(
            startStage = LandscapePlayerStage.COMPACT,
            compactProgress = 1f,
            expansionProgress = 0.39f,
            velocity = -1_000f,
            expectedTarget = LandscapePlayerStage.COMPACT
        )
        assertRelease(
            startStage = LandscapePlayerStage.COMPACT,
            compactProgress = 1f,
            expansionProgress = 0.39f,
            velocity = -1_999f,
            expectedTarget = LandscapePlayerStage.COMPACT
        )
        assertRelease(
            startStage = LandscapePlayerStage.COMPACT,
            compactProgress = 1f,
            expansionProgress = 0f,
            velocity = -2_000f,
            expectedTarget = LandscapePlayerStage.COMPACT
        )
        assertRelease(
            startStage = LandscapePlayerStage.COMPACT,
            compactProgress = 1f,
            expansionProgress = 0f,
            velocity = -3_999f,
            expectedTarget = LandscapePlayerStage.COMPACT
        )
        assertRelease(
            startStage = LandscapePlayerStage.COMPACT,
            compactProgress = 1f,
            expansionProgress = 0f,
            velocity = -4_000f,
            expectedTarget = LandscapePlayerStage.FULL
        )
        assertRelease(
            startStage = LandscapePlayerStage.COMPACT,
            compactProgress = 0.61f,
            expansionProgress = 0f,
            velocity = 999f,
            expectedTarget = LandscapePlayerStage.COMPACT
        )
        assertRelease(
            startStage = LandscapePlayerStage.COMPACT,
            compactProgress = 1f,
            expansionProgress = 0f,
            velocity = 1_000f,
            expectedTarget = LandscapePlayerStage.MINI
        )
        assertRelease(
            startStage = LandscapePlayerStage.COMPACT,
            compactProgress = 1f,
            expansionProgress = 0f,
            velocity = 1_999f,
            expectedTarget = LandscapePlayerStage.MINI
        )
        assertRelease(
            startStage = LandscapePlayerStage.COMPACT,
            compactProgress = 1f,
            expansionProgress = 0f,
            velocity = 4_000f,
            expectedTarget = LandscapePlayerStage.MINI
        )
        assertRelease(
            startStage = LandscapePlayerStage.COMPACT,
            compactProgress = 0.59f,
            expansionProgress = 0f,
            velocity = 999f,
            expectedTarget = LandscapePlayerStage.MINI
        )
    }

    @Test
    fun fullReleaseUsesMediumFlickForCompactAndFastFlickForMini() {
        assertRelease(
            startStage = LandscapePlayerStage.FULL,
            compactProgress = 1f,
            expansionProgress = 0.61f,
            velocity = 999f,
            expectedTarget = LandscapePlayerStage.FULL
        )
        assertRelease(
            startStage = LandscapePlayerStage.FULL,
            compactProgress = 1f,
            expansionProgress = 1f,
            velocity = 1_000f,
            expectedTarget = LandscapePlayerStage.COMPACT
        )
        assertRelease(
            startStage = LandscapePlayerStage.FULL,
            compactProgress = 1f,
            expansionProgress = 1f,
            velocity = 1_999f,
            expectedTarget = LandscapePlayerStage.COMPACT
        )
        assertRelease(
            startStage = LandscapePlayerStage.FULL,
            compactProgress = 1f,
            expansionProgress = 1f,
            velocity = 2_000f,
            expectedTarget = LandscapePlayerStage.COMPACT
        )
        assertRelease(
            startStage = LandscapePlayerStage.FULL,
            compactProgress = 1f,
            expansionProgress = 1f,
            velocity = 3_999f,
            expectedTarget = LandscapePlayerStage.COMPACT
        )
        assertRelease(
            startStage = LandscapePlayerStage.FULL,
            compactProgress = 1f,
            expansionProgress = 1f,
            velocity = 4_000f,
            expectedTarget = LandscapePlayerStage.MINI,
            expectedSpansBothStages = true
        )
        assertRelease(
            startStage = LandscapePlayerStage.FULL,
            compactProgress = 1f,
            expansionProgress = 0.59f,
            velocity = 999f,
            expectedTarget = LandscapePlayerStage.COMPACT
        )
        assertRelease(
            startStage = LandscapePlayerStage.FULL,
            compactProgress = 0.59f,
            expansionProgress = 0f,
            velocity = 999f,
            expectedTarget = LandscapePlayerStage.MINI,
            expectedSpansBothStages = true
        )
        assertRelease(
            startStage = LandscapePlayerStage.FULL,
            compactProgress = 1f,
            expansionProgress = 1f,
            velocity = -4_000f,
            expectedTarget = LandscapePlayerStage.FULL
        )
    }

    @Test
    fun positionalFallbackKeepsDirectionalLandscapeThresholds() {
        assertRelease(
            startStage = LandscapePlayerStage.MINI,
            compactProgress = 0.399f,
            expansionProgress = 0f,
            velocity = 0f,
            expectedTarget = LandscapePlayerStage.MINI
        )
        assertRelease(
            startStage = LandscapePlayerStage.MINI,
            compactProgress = 0.40f,
            expansionProgress = 0f,
            velocity = 0f,
            expectedTarget = LandscapePlayerStage.COMPACT
        )
        assertRelease(
            startStage = LandscapePlayerStage.FULL,
            compactProgress = 1f,
            expansionProgress = 0.601f,
            velocity = 0f,
            expectedTarget = LandscapePlayerStage.FULL
        )
        assertRelease(
            startStage = LandscapePlayerStage.FULL,
            compactProgress = 1f,
            expansionProgress = 0.60f,
            velocity = 0f,
            expectedTarget = LandscapePlayerStage.COMPACT
        )
    }

    @Test
    fun dragVelocityUsesOnlyTheRollingHundredMillisecondWindow() {
        val tracker = LandscapeDragVelocityTracker()
        tracker.begin(timestampNanos = 0L)
        tracker.recordDelta(-20f, timestampNanos = 20_000_000L)
        tracker.recordDelta(-30f, timestampNanos = 60_000_000L)
        tracker.recordDelta(-50f, timestampNanos = 100_000_000L)

        assertEquals(
            -1_000f,
            tracker.velocityPxPerSecond(100_000_000L),
            0.001f
        )
        assertEquals(
            -2_000f,
            tracker.velocityPxPerSecond(150_000_000L),
            0.001f
        )
        assertEquals(
            0f,
            tracker.velocityPxPerSecond(201_000_000L),
            0.001f
        )
    }

    @Test
    fun dragVelocityResetsBetweenGesturesAndKeepsDirection() {
        val tracker = LandscapeDragVelocityTracker()
        tracker.begin(timestampNanos = 0L)
        tracker.recordDelta(100f, timestampNanos = 100_000_000L)
        assertEquals(
            1_000f,
            tracker.velocityPxPerSecond(100_000_000L),
            0.001f
        )

        tracker.end()
        assertEquals(0f, tracker.velocityPxPerSecond(100_000_000L), 0.001f)

        tracker.begin(timestampNanos = 1_000_000_000L)
        tracker.recordDelta(-25f, timestampNanos = 1_050_000_000L)
        assertEquals(
            -500f,
            tracker.velocityPxPerSecond(1_050_000_000L),
            0.001f
        )
    }

    @Test
    fun hapticsEmitForFirstAndSubFullFlickSpeedBoundaries() {
        val arbiter = LandscapePlayerGestureHapticArbiter()
        val session = arbiter.begin()

        assertTrue(
            arbiter.onLiveBoundaryCrossed(
                session,
                LandscapeDetentBoundary.COMPACT,
                velocityPxPerSecond = 500f
            )
        )
        assertTrue(
            arbiter.onLiveBoundaryCrossed(
                session,
                LandscapeDetentBoundary.EXPANSION,
                velocityPxPerSecond = 3_999f
            )
        )
    }

    @Test
    fun highSpeedSecondBoundarySuppressesTheRestOfTheGesture() {
        val arbiter = LandscapePlayerGestureHapticArbiter()
        val session = arbiter.begin()

        assertTrue(
            arbiter.onLiveBoundaryCrossed(
                session,
                LandscapeDetentBoundary.COMPACT,
                velocityPxPerSecond = 1_000f
            )
        )
        assertFalse(
            arbiter.onLiveBoundaryCrossed(
                session,
                LandscapeDetentBoundary.EXPANSION,
                velocityPxPerSecond = 4_000f
            )
        )
        assertFalse(
            arbiter.onLiveBoundaryCrossed(
                session,
                LandscapeDetentBoundary.COMPACT,
                velocityPxPerSecond = 500f
            )
        )
        arbiter.prepareForSettle(
            session,
            limitDirectTransitionToSingleTick = false
        )
        assertFalse(
            arbiter.onAnimationBoundaryCrossed(
                session,
                LandscapeDetentBoundary.EXPANSION
            )
        )
    }

    @Test
    fun directHighSpeedTransitionEmitsExactlyOneInEitherDirection() {
        val arbiter = LandscapePlayerGestureHapticArbiter()
        val expansionSession = arbiter.begin()
        arbiter.prepareForSettle(
            expansionSession,
            limitDirectTransitionToSingleTick = true
        )
        assertTrue(
            arbiter.onAnimationBoundaryCrossed(
                expansionSession,
                LandscapeDetentBoundary.COMPACT
            )
        )
        assertFalse(
            arbiter.onAnimationBoundaryCrossed(
                expansionSession,
                LandscapeDetentBoundary.EXPANSION
            )
        )

        arbiter.end(expansionSession)
        val collapseSession = arbiter.begin()
        arbiter.prepareForSettle(
            collapseSession,
            limitDirectTransitionToSingleTick = true
        )
        assertTrue(
            arbiter.onAnimationBoundaryCrossed(
                collapseSession,
                LandscapeDetentBoundary.EXPANSION
            )
        )
        assertFalse(
            arbiter.onAnimationBoundaryCrossed(
                collapseSession,
                LandscapeDetentBoundary.COMPACT
            )
        )
    }

    @Test
    fun suppressedBoundariesRemainHandledAndResetWithTheNextGesture() {
        val arbiter = LandscapePlayerGestureHapticArbiter()
        val firstSession = arbiter.begin()
        assertTrue(
            arbiter.onLiveBoundaryCrossed(
                firstSession,
                LandscapeDetentBoundary.COMPACT,
                velocityPxPerSecond = 500f
            )
        )
        assertFalse(
            arbiter.onLiveBoundaryCrossed(
                firstSession,
                LandscapeDetentBoundary.EXPANSION,
                velocityPxPerSecond = 4_000f
            )
        )
        arbiter.prepareForSettle(
            firstSession,
            limitDirectTransitionToSingleTick = false
        )
        assertFalse(
            arbiter.onAnimationBoundaryCrossed(
                firstSession,
                LandscapeDetentBoundary.COMPACT
            )
        )
        assertFalse(
            arbiter.onAnimationBoundaryCrossed(
                firstSession,
                LandscapeDetentBoundary.EXPANSION
            )
        )

        arbiter.end(firstSession)
        val secondSession = arbiter.begin()
        assertTrue(
            arbiter.onLiveBoundaryCrossed(
                secondSession,
                LandscapeDetentBoundary.COMPACT,
                velocityPxPerSecond = 500f
            )
        )
    }

    @Test
    fun staleGestureSessionCannotEndOrControlANewerSession() {
        val arbiter = LandscapePlayerGestureHapticArbiter()
        val staleSession = arbiter.begin()
        val activeSession = arbiter.begin()

        arbiter.end(staleSession)
        arbiter.prepareForSettle(
            staleSession,
            limitDirectTransitionToSingleTick = true
        )

        assertFalse(
            arbiter.onLiveBoundaryCrossed(
                staleSession,
                LandscapeDetentBoundary.COMPACT,
                velocityPxPerSecond = 500f
            )
        )
        assertFalse(
            arbiter.onAnimationBoundaryCrossed(
                staleSession,
                LandscapeDetentBoundary.EXPANSION
            )
        )
        assertTrue(
            arbiter.onLiveBoundaryCrossed(
                activeSession,
                LandscapeDetentBoundary.COMPACT,
                velocityPxPerSecond = 500f
            )
        )
    }

    @Test
    fun trackerCarriesItsAnimationRouteUntilANewerSessionReplacesIt() {
        val tracker = PlayerCriticalPointHapticTracker()
        val firstSession = tracker.begin(
            progress = 0.1f,
            criticalPoint = 0.4f,
            hapticArbiterSession = 7
        )
        val secondSession = tracker.begin(
            progress = 0.1f,
            criticalPoint = 0.4f,
            hapticArbiterSession = 8
        )

        assertFalse(tracker.observe(0.5f, expectedSession = firstSession))
        assertEquals(
            8,
            tracker.observeCrossing(
                0.5f,
                expectedSession = secondSession
            )?.hapticArbiterSession
        )

        tracker.end(firstSession)
        tracker.begin(
            progress = 0.1f,
            criticalPoint = 0.4f,
            hapticArbiterSession = 9
        )
        tracker.end(secondSession)
        assertEquals(
            9,
            tracker.observeCrossing(0.5f)?.hapticArbiterSession
        )
    }

    @Test
    fun trackerWithoutAnArbiterSessionKeepsDirectHaptics() {
        val tracker = PlayerCriticalPointHapticTracker()
        tracker.begin(progress = 0.1f, criticalPoint = 0.4f)

        val crossing = tracker.observeCrossing(0.5f)

        assertTrue(crossing != null)
        assertEquals(null, crossing?.hapticArbiterSession)
    }

    private fun assertRelease(
        startStage: LandscapePlayerStage,
        compactProgress: Float,
        expansionProgress: Float,
        velocity: Float,
        expectedTarget: LandscapePlayerStage,
        expectedSpansBothStages: Boolean = false
    ) {
        val release = resolveLandscapePlayerRelease(
            startStage = startStage,
            compactRawProgress = compactProgress,
            expansionRawProgress = expansionProgress,
            velocityPxPerSecond = velocity
        )
        assertEquals(expectedTarget, release.target)
        assertEquals(expectedSpansBothStages, release.spansBothStages)
    }
}
