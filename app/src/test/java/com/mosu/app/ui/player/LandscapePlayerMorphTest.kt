package com.mosu.app.ui.player

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LandscapePlayerMorphTest {
    private val landscapeCriticalPointNear = playerGestureCriticalPoint(
        PlayerGestureDirection.EXPANDING,
        PLAYER_LANDSCAPE_CRITICAL_EDGE_FRACTION
    )

    @Test
    fun leftDragContinuesFromCompactIntoFullscreenUsingPhysicalProgress() {
        val state = advanceLandscapePlayerDragState(
            state = LandscapePlayerDragState.at(
                position = LandscapePlayerDragPosition(
                    sheetOffsetPx = 120f,
                    expansionProgress = 0f
                ),
                collapsedOffsetPx = 120f
            ),
            deltaPx = -220f,
            collapsedOffsetPx = 120f,
            expansionDistancePx = 1_000f,
            expansionGain = 1.2f
        )

        assertEquals(1f, state.compactRawProgress, 0.0001f)
        assertEquals(0.12f, state.expansionRawProgress, 0.0001f)
        assertTrue(state.position.sheetOffsetPx > 0f)
        assertTrue(state.position.expansionProgress < 0.12f)

        val caughtUp = state.withCaughtUpVisuals(
            collapsedOffsetPx = 120f,
            compactDirection = PlayerGestureDirection.EXPANDING,
            expansionDirection = PlayerGestureDirection.EXPANDING
        )
        assertEquals(0f, caughtUp.position.sheetOffsetPx, 0.001f)
        assertEquals(
            0.12f + (72f * 0.5f / 1_000f),
            caughtUp.position.expansionProgress,
            0.001f
        )
    }

    @Test
    fun rightDragReversesFullscreenBeforeReturningToMiniPlayer() {
        var state = LandscapePlayerDragState.at(
            position = LandscapePlayerDragPosition(
                sheetOffsetPx = 0f,
                expansionProgress = 1f
            ),
            collapsedOffsetPx = 120f
        )
        state = advanceLandscapePlayerDragState(
            state = state,
            deltaPx = 300f,
            collapsedOffsetPx = 120f,
            expansionDistancePx = 1_000f,
            expansionGain = 1.2f,
            compactDirection = PlayerGestureDirection.COLLAPSING,
            expansionDirection = PlayerGestureDirection.COLLAPSING
        )
        state = advanceLandscapePlayerDragState(
            state = state,
            deltaPx = 100f,
            collapsedOffsetPx = 120f,
            expansionDistancePx = 1_000f,
            expansionGain = 1.2f,
            compactDirection = PlayerGestureDirection.COLLAPSING,
            expansionDirection = PlayerGestureDirection.COLLAPSING,
            expansionCatchUpActive = true
        )
        assertEquals(1f, state.compactRawProgress, 0.0001f)
        assertEquals(0.52f, state.expansionRawProgress, 0.0001f)

        state = advanceLandscapePlayerDragState(
            state = state,
            deltaPx = 1_000f,
            collapsedOffsetPx = 120f,
            expansionDistancePx = 1_000f,
            expansionGain = 1.2f,
            compactDirection = PlayerGestureDirection.COLLAPSING,
            expansionDirection = PlayerGestureDirection.COLLAPSING,
            expansionCatchUpActive = true
        )
        assertEquals(0f, state.expansionRawProgress, 0.0001f)
        assertEquals(0f, state.compactRawProgress, 0.0001f)

        val caughtUp = state.withCaughtUpVisuals(
            collapsedOffsetPx = 120f,
            compactDirection = PlayerGestureDirection.COLLAPSING,
            expansionDirection = PlayerGestureDirection.COLLAPSING
        )
        assertEquals(120f, caughtUp.position.sheetOffsetPx, 0.001f)
        assertEquals(0f, caughtUp.position.expansionProgress, 0.0001f)
    }

    @Test
    fun continuousLeftDragPassesBothCriticalPoints() {
        var state = advanceLandscapePlayerDragState(
            state = LandscapePlayerDragState.at(
                position = LandscapePlayerDragPosition(120f, 0f),
                collapsedOffsetPx = 120f
            ),
            deltaPx = -150f,
            collapsedOffsetPx = 120f,
            expansionDistancePx = 1_000f,
            expansionGain = 1.2f,
            compactDirection = PlayerGestureDirection.EXPANDING,
            expansionDirection = PlayerGestureDirection.EXPANDING
        )
        assertEquals(1f, state.compactRawProgress, 0.0001f)
        assertTrue(
            state.expansionRawProgress in 0f..landscapeCriticalPointNear
        )

        state = advanceLandscapePlayerDragState(
            state = state,
            deltaPx = -500f,
            collapsedOffsetPx = 120f,
            expansionDistancePx = 1_000f,
            expansionGain = 1.2f,
            compactDirection = PlayerGestureDirection.EXPANDING,
            expansionDirection = PlayerGestureDirection.EXPANDING
        )
        assertEquals(1f, state.compactRawProgress, 0.0001f)
        assertTrue(
            state.expansionRawProgress > landscapeCriticalPointNear
        )
        assertTrue(state.position.expansionProgress < state.expansionRawProgress)
    }

    @Test
    fun compactDetentResistsMovementNearItsCriticalPoint() {
        val detented = advanceLandscapePlayerDrag(
            position = LandscapePlayerDragPosition(120f, 0f),
            deltaPx = -30f,
            collapsedOffsetPx = 120f,
            expansionDistancePx = 1_000f,
            expansionGain = 1.2f,
            compactDirection = PlayerGestureDirection.EXPANDING,
            expansionDirection = PlayerGestureDirection.EXPANDING
        )

        assertTrue(detented.sheetOffsetPx > 0f)
    }

    @Test
    fun statefulCompactDragCrossesPhysicalCriticalPointThenCatchesUp() {
        var state = LandscapePlayerDragState.at(
            position = LandscapePlayerDragPosition(120f, 0f),
            collapsedOffsetPx = 120f
        )
        var previousRawProgress = 0f
        var crossed = false
        var visualAtCrossing = 1f
        var gapAtCrossing = 0f

        repeat(15) {
            state = advanceLandscapePlayerDragState(
                state = state,
                deltaPx = -5f,
                collapsedOffsetPx = 120f,
                expansionDistancePx = 1_000f,
                expansionGain = 1.2f,
                compactDirection = PlayerGestureDirection.EXPANDING,
                expansionDirection = PlayerGestureDirection.EXPANDING,
                compactCatchUpActive = crossed
            )
            val progress = 1f - state.position.sheetOffsetPx / 120f
            val rawProgress = state.compactRawProgress
            if (
                previousRawProgress < landscapeCriticalPointNear &&
                rawProgress >= landscapeCriticalPointNear
            ) {
                crossed = true
                visualAtCrossing = progress
                gapAtCrossing = rawProgress - progress
                assertTrue(progress < landscapeCriticalPointNear)
                assertEquals(
                    landscapeCriticalPointNear,
                    rawProgress,
                    0.05f
                )
            }
            if (crossed) {
                assertEquals(
                    visualAtCrossing,
                    progress,
                    0.0001f
                )
            }
            assertTrue(progress <= rawProgress + 0.0001f)
            previousRawProgress = rawProgress
        }

        assertTrue(crossed)
        assertTrue(visualAtCrossing < landscapeCriticalPointNear)
        assertTrue(gapAtCrossing > 0.03f)
        var caughtUpProgress = visualAtCrossing
        repeat(40) {
            caughtUpProgress = advancePlayerDetentCatchUp(
                progress = caughtUpProgress,
                targetProgress = landscapePlayerCatchUpTarget(
                    rawProgress = state.compactRawProgress,
                    direction = PlayerGestureDirection.EXPANDING,
                    stageDistancePx = 120f,
                    miniPlayerWidthPx = 72f
                ),
                elapsedSeconds = 1f / 60f,
                catchUpSpeed = PLAYER_LANDSCAPE_DETENT_CATCH_UP_SPEED
            )
        }
        assertTrue(caughtUpProgress > state.compactRawProgress)
    }

    @Test
    fun bothDragDirectionsClampToTheirEndpoints() {
        val expanded = advanceLandscapePlayerDragState(
            state = LandscapePlayerDragState.at(
                position = LandscapePlayerDragPosition(120f, 0f),
                collapsedOffsetPx = 120f
            ),
            deltaPx = -10_000f,
            collapsedOffsetPx = 120f,
            expansionDistancePx = 1_000f,
            expansionGain = 1.2f
        )
        assertEquals(1f, expanded.compactRawProgress, 0.0001f)
        assertEquals(1f, expanded.expansionRawProgress, 0.0001f)
        val caughtUpExpanded = expanded.withCaughtUpVisuals(
            collapsedOffsetPx = 120f,
            compactDirection = PlayerGestureDirection.EXPANDING,
            expansionDirection = PlayerGestureDirection.EXPANDING
        )
        assertEquals(0f, caughtUpExpanded.position.sheetOffsetPx, 0.001f)
        assertEquals(
            1f,
            caughtUpExpanded.position.expansionProgress,
            0.0001f
        )

        val collapsed = advanceLandscapePlayerDragState(
            state = LandscapePlayerDragState.at(
                position = LandscapePlayerDragPosition(0f, 1f),
                collapsedOffsetPx = 120f
            ),
            deltaPx = 10_000f,
            collapsedOffsetPx = 120f,
            expansionDistancePx = 1_000f,
            expansionGain = 1.2f
        )
        assertEquals(0f, collapsed.compactRawProgress, 0.0001f)
        assertEquals(0f, collapsed.expansionRawProgress, 0.0001f)
        val caughtUpCollapsed = collapsed.withCaughtUpVisuals(
            collapsedOffsetPx = 120f,
            compactDirection = PlayerGestureDirection.COLLAPSING,
            expansionDirection = PlayerGestureDirection.COLLAPSING
        )
        assertEquals(
            120f,
            caughtUpCollapsed.position.sheetOffsetPx,
            0.001f
        )
        assertEquals(
            0f,
            caughtUpCollapsed.position.expansionProgress,
            0.0001f
        )
    }

    @Test
    fun nonFiniteOrZeroExpansionDistanceIsHandledWithoutBreakingProgress() {
        val zeroDistance = advanceLandscapePlayerDragState(
            state = LandscapePlayerDragState.at(
                position = LandscapePlayerDragPosition(0f, 0f),
                collapsedOffsetPx = 120f
            ),
            deltaPx = -100f,
            collapsedOffsetPx = 120f,
            expansionDistancePx = 0f,
            expansionGain = 1.2f
        )
        assertEquals(1f, zeroDistance.expansionRawProgress, 0.0001f)
        assertEquals(
            1f,
            zeroDistance.withCaughtUpVisuals(
                collapsedOffsetPx = 120f,
                compactDirection = PlayerGestureDirection.EXPANDING,
                expansionDirection = PlayerGestureDirection.EXPANDING
            )
                .position.expansionProgress,
            0.0001f
        )

        val nonFiniteDelta = advanceLandscapePlayerDrag(
            position = LandscapePlayerDragPosition(80f, 0.4f),
            deltaPx = Float.NaN,
            collapsedOffsetPx = 120f,
            expansionDistancePx = 1_000f,
            expansionGain = 1.2f
        )
        assertEquals(80f, nonFiniteDelta.sheetOffsetPx, 0.001f)
        assertEquals(0.4f, nonFiniteDelta.expansionProgress, 0.0001f)
    }

    @Test
    fun sharedProgressKeepsHorizontalAndVerticalExpansionSynchronized() {
        val compact = DpSize(180.dp, 270.dp)
        val fullscreen = DpSize(800.dp, 360.dp)
        val halfway = landscapePlayerMorphBounds(
            compactSize = compact,
            fullscreenSize = fullscreen,
            progress = 0.5f
        )

        val horizontalFraction =
            (fullscreen.width - halfway.width) / (fullscreen.width - compact.width)
        val verticalTarget = (fullscreen.height - compact.height) / 2f
        val verticalFraction = halfway.topInset / verticalTarget

        assertEquals(0.5f, horizontalFraction, 0.0001f)
        assertEquals(0.5f, verticalFraction, 0.0001f)
        assertEquals(490.dp, halfway.width)
        assertEquals(315.dp, halfway.height)
    }

    @Test
    fun morphBoundsMatchBothEndpointsAndClampOutOfRangeProgress() {
        val compact = DpSize(180.dp, 270.dp)
        val fullscreen = DpSize(800.dp, 360.dp)

        val start = landscapePlayerMorphBounds(compact, fullscreen, -1f)
        assertEquals(compact.width, start.width)
        assertEquals(compact.height, start.height)
        assertEquals(620.dp, start.leftInset)
        assertEquals(45.dp, start.topInset)

        val end = landscapePlayerMorphBounds(compact, fullscreen, 2f)
        assertEquals(fullscreen.width, end.width)
        assertEquals(fullscreen.height, end.height)
        assertEquals(0.dp, end.leftInset)
        assertEquals(0.dp, end.topInset)
    }

    @Test
    fun morphFilterIsDisabledAtBothEndpointsAndPeaksAtMidpoint() {
        assertEquals(0f, landscapeMorphFilterIntensity(0f), 0.0001f)
        assertEquals(0.5f, landscapeMorphFilterIntensity(0.25f), 0.0001f)
        assertEquals(1f, landscapeMorphFilterIntensity(0.5f), 0.0001f)
        assertEquals(0.5f, landscapeMorphFilterIntensity(0.75f), 0.0001f)
        assertEquals(0f, landscapeMorphFilterIntensity(1f), 0.0001f)
    }

    @Test
    fun compactLayoutFollowsVerticalOrderAndKeepsArtworkUsable() {
        val rects = compactLandscapeMorphRects(
            compactSize = DpSize(180.dp, 270.dp),
            showDifficultySelector = true
        )

        assertTrue(rects.artwork.height >= 56.dp)
        assertTrue(rects.artwork.width > rects.artwork.height)
        assertEquals(44f, rects.artwork.top.value, 0.001f)
        assertEquals(
            rects.artwork.top - (rects.collapse.top + rects.collapse.height),
            rects.title.top - (rects.artwork.top + rects.artwork.height)
        )
        assertTrue(rects.artwork.top + rects.artwork.height <= rects.title.top)
        assertTrue(rects.title.top + rects.title.height <= rects.progress.top)
        assertTrue(rects.progress.top + rects.progress.height <= rects.controls.top)
        assertEquals(240f, (rects.controls.top + rects.controls.height).value, 0.001f)
        assertEquals(16.2f, rects.title.left.value, 0.001f)

        val metadataRight = rects.title.left + rects.title.width
        assertTrue(metadataRight <= rects.difficulty.left)
        assertTrue(
            rects.difficulty.left + rects.difficulty.width <=
                rects.rendererToggle.left
        )
    }

    private fun LandscapePlayerDragState.withCaughtUpVisuals(
        collapsedOffsetPx: Float,
        compactDirection: PlayerGestureDirection,
        expansionDirection: PlayerGestureDirection,
        expansionDistancePx: Float = 1_000f,
        miniPlayerWidthPx: Float = 72f
    ): LandscapePlayerDragState {
        var compactProgress = playerProgressFromSheetOffset(
            position.sheetOffsetPx,
            collapsedOffsetPx
        )
        var expansionProgress = position.expansionProgress
        repeat(40) {
            compactProgress = advancePlayerDetentCatchUp(
                progress = compactProgress,
                targetProgress = landscapePlayerCatchUpTarget(
                    rawProgress = compactRawProgress,
                    direction = compactDirection,
                    stageDistancePx = collapsedOffsetPx,
                    miniPlayerWidthPx = miniPlayerWidthPx
                ),
                elapsedSeconds = 1f / 60f,
                catchUpSpeed = PLAYER_LANDSCAPE_DETENT_CATCH_UP_SPEED
            )
            expansionProgress = advancePlayerDetentCatchUp(
                progress = expansionProgress,
                targetProgress = landscapePlayerCatchUpTarget(
                    rawProgress = expansionRawProgress,
                    direction = expansionDirection,
                    stageDistancePx = expansionDistancePx,
                    miniPlayerWidthPx = miniPlayerWidthPx
                ),
                elapsedSeconds = 1f / 60f,
                catchUpSpeed = PLAYER_LANDSCAPE_DETENT_CATCH_UP_SPEED
            )
        }
        return copy(
            position = LandscapePlayerDragPosition(
                sheetOffsetPx = playerSheetOffsetFromProgress(
                    compactProgress,
                    collapsedOffsetPx
                ),
                expansionProgress = expansionProgress
            )
        )
    }
}
