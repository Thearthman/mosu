package com.mosu.app.ui.player

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LandscapePlayerMorphTest {
    @Test
    fun leftDragContinuesFromCompactIntoFullscreenUsingGain() {
        val result = advanceLandscapePlayerDrag(
            position = LandscapePlayerDragPosition(
                sheetOffsetPx = 120f,
                expansionProgress = 0f
            ),
            deltaPx = -220f,
            collapsedOffsetPx = 120f,
            expansionDistancePx = 1_000f,
            expansionGain = 1.2f
        )

        assertEquals(0f, result.sheetOffsetPx, 0.001f)
        assertEquals(0.12f, result.expansionProgress, 0.0001f)
    }

    @Test
    fun rightDragReversesFullscreenBeforeReturningToMiniPlayer() {
        val backToCompact = advanceLandscapePlayerDrag(
            position = LandscapePlayerDragPosition(
                sheetOffsetPx = 0f,
                expansionProgress = 0.2f
            ),
            deltaPx = 100f,
            collapsedOffsetPx = 120f,
            expansionDistancePx = 1_000f,
            expansionGain = 1.2f
        )
        assertEquals(0f, backToCompact.sheetOffsetPx, 0.001f)
        assertEquals(0.08f, backToCompact.expansionProgress, 0.0001f)

        val continueToMini = advanceLandscapePlayerDrag(
            position = backToCompact,
            deltaPx = 100f,
            collapsedOffsetPx = 120f,
            expansionDistancePx = 1_000f,
            expansionGain = 1.2f
        )
        assertEquals(33.3333f, continueToMini.sheetOffsetPx, 0.001f)
        assertEquals(0f, continueToMini.expansionProgress, 0.0001f)
    }

    @Test
    fun bothDragDirectionsClampToTheirEndpoints() {
        val expanded = advanceLandscapePlayerDrag(
            position = LandscapePlayerDragPosition(120f, 0f),
            deltaPx = -10_000f,
            collapsedOffsetPx = 120f,
            expansionDistancePx = 1_000f,
            expansionGain = 1.2f
        )
        assertEquals(0f, expanded.sheetOffsetPx, 0.001f)
        assertEquals(1f, expanded.expansionProgress, 0.0001f)

        val collapsed = advanceLandscapePlayerDrag(
            position = LandscapePlayerDragPosition(0f, 1f),
            deltaPx = 10_000f,
            collapsedOffsetPx = 120f,
            expansionDistancePx = 1_000f,
            expansionGain = 1.2f
        )
        assertEquals(120f, collapsed.sheetOffsetPx, 0.001f)
        assertEquals(0f, collapsed.expansionProgress, 0.0001f)
    }

    @Test
    fun nonFiniteOrZeroExpansionDistanceIsHandledWithoutBreakingProgress() {
        val zeroDistance = advanceLandscapePlayerDrag(
            position = LandscapePlayerDragPosition(0f, 0f),
            deltaPx = -100f,
            collapsedOffsetPx = 120f,
            expansionDistancePx = 0f,
            expansionGain = 1.2f
        )
        assertEquals(1f, zeroDistance.expansionProgress, 0.0001f)

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
}
