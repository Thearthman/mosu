package com.mosu.app.domain.render.mania

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManiaGeometryTest {
    @Test
    fun centresDefaultFourKeyPlayfield() {
        val geometry = maniaPlayfieldGeometry(
            keyCount = 4,
            columnWidths = emptyList(),
            columnSpacings = emptyList(),
            hitPosition = 110f,
            barlineHeight = 1.2f
        )

        assertEquals(323f, geometry.width, 0.0001f)
        assertEquals(350.5f, geometry.left, 0.0001f)
        assertEquals(673.5f, geometry.right, 0.0001f)
        assertEquals(
            listOf(350.5f, 431.5f, 512.5f, 593.5f),
            geometry.columns.map { it.left }
        )
        assertEquals(List(4) { 80f }, geometry.columns.map { it.width })
    }

    @Test
    fun givesOddKeyCountsANarrowerCentreColumn() {
        val geometry = maniaPlayfieldGeometry(
            keyCount = 5,
            columnWidths = emptyList(),
            columnSpacings = emptyList(),
            hitPosition = 110f,
            barlineHeight = 1.2f
        )

        assertEquals(listOf(80f, 80f, 70f, 80f, 80f), geometry.columns.map { it.width })
        assertEquals(315f, geometry.left, 0.0001f)
        assertEquals(477f, geometry.columns[2].left, 0.0001f)
    }

    @Test
    fun usesExplicitColumnWidthsAndSpacings() {
        val geometry = maniaPlayfieldGeometry(
            keyCount = 3,
            columnWidths = listOf(100f, 60f, 80f),
            columnSpacings = listOf(4f, 6f),
            hitPosition = 100f,
            barlineHeight = 2f
        )

        assertEquals(250f, geometry.width, 0.0001f)
        assertEquals(387f, geometry.left, 0.0001f)
        assertEquals(listOf(387f, 491f, 557f), geometry.columns.map { it.left })
        assertEquals(listOf(100f, 60f, 80f), geometry.columns.map { it.width })
        assertEquals(100f, geometry.hitPosition, 0.0001f)
        assertEquals(2f, geometry.barlineHeight, 0.0001f)
    }

    @Test
    fun derivesStableManiaFallbackSuffixes() {
        assertEquals(
            listOf("1", "2", "2", "1"),
            (0 until 4).map { maniaColumnFallbackSuffix(4, it) }
        )
        assertEquals(
            listOf("1", "2", "S", "2", "1"),
            (0 until 5).map { maniaColumnFallbackSuffix(5, it) }
        )
        assertEquals(
            listOf("1", "2", "1", "S", "1", "2", "1"),
            (0 until 7).map { maniaColumnFallbackSuffix(7, it) }
        )
    }

    @Test
    fun playfieldIsASquareThatPreservesTheExistingWidth() {
        assertEquals(1024f, ManiaRenderTimeline.MANIA_PLAYFIELD_WIDTH, 0f)
        assertEquals(
            ManiaRenderTimeline.MANIA_PLAYFIELD_WIDTH,
            ManiaRenderTimeline.MANIA_PLAYFIELD_HEIGHT,
            0f
        )
        assertEquals(1f, ManiaBeatmapRenderer().preferredAspectRatio, 0f)
    }

    @Test
    fun centresSquareViewportWithinAWideSurface() {
        val transform = maniaViewportTransform(
            surfaceWidthPx = 800f,
            surfaceHeightPx = 600f
        )

        assertEquals(600f / 1024f, transform.scale, 0.0001f)
        assertEquals(100f, transform.offsetX, 0.0001f)
        assertEquals(0f, transform.offsetY, 0.0001f)
        assertEquals(700f, transform.mapX(1024f), 0.0001f)
        assertEquals(600f, transform.mapY(1024f), 0.0001f)
    }

    @Test
    fun appliesEdgeInsetBeforeAspectFitting() {
        val transform = maniaViewportTransform(
            surfaceWidthPx = 800f,
            surfaceHeightPx = 600f,
            edgeInsetPx = 10f
        )

        assertEquals(580f / 1024f, transform.scale, 0.0001f)
        assertEquals(110f, transform.offsetX, 0.0001f)
        assertEquals(10f, transform.offsetY, 0.0001f)
    }

    @Test
    fun invalidSurfacesReturnAnEmptyTransform() {
        val transform = maniaViewportTransform(
            surfaceWidthPx = 0f,
            surfaceHeightPx = 600f
        )

        assertEquals(0f, transform.scale, 0f)
        assertEquals(0f, transform.offsetX, 0f)
        assertEquals(0f, transform.offsetY, 0f)
    }

    @Test
    fun holdBodyEndpointsAlignWithNoteCentres() {
        val body = maniaHoldBodyBounds(
            headY = 200f,
            tailY = 100f,
            headHeight = 12f,
            tailHeight = 12f
        )

        assertEquals(94f, body.top, 0.0001f)
        assertEquals(194f, body.bottom, 0.0001f)
    }

    @Test
    fun holdBodyUsesEachPieceHeightForItsAnchorCentres() {
        val body = maniaHoldBodyBounds(
            headY = 200f,
            tailY = 100f,
            headHeight = 20f,
            tailHeight = 8f
        )

        assertEquals(96f, body.top, 0.0001f)
        assertEquals(190f, body.bottom, 0.0001f)
    }

    @Test
    fun nonSquareTexturesRetainTheirAspectRatio() {
        assertEquals(
            6f,
            maniaNotePieceHeight(
                referenceHeight = 12f,
                textureWidth = 80,
                textureHeight = 40
            ),
            0.0001f
        )
        assertEquals(
            12f,
            maniaNotePieceHeight(
                referenceHeight = 12f,
                textureWidth = 80,
                textureHeight = 80
            ),
            0.0001f
        )
    }

    @Test
    fun tailPiecesUseTheSameBottomAnchorAndMirroredTexture() {
        val head = maniaNotePieceGeometry(
            y = 200f,
            pieceHeight = 12f,
            isTail = false
        )
        val tail = maniaNotePieceGeometry(
            y = 100f,
            pieceHeight = 6f,
            isTail = true
        )

        assertEquals(188f, head.top, 0.0001f)
        assertEquals(200f, head.bottom, 0.0001f)
        assertEquals(false, head.verticallyMirrored)
        assertEquals(94f, tail.top, 0.0001f)
        assertEquals(100f, tail.bottom, 0.0001f)
        assertEquals(true, tail.verticallyMirrored)
    }

    @Test
    fun shortHoldKeepsTheTailAboveTheHeadBottom() {
        val head = maniaNotePieceGeometry(
            y = 200f,
            pieceHeight = 12f,
            isTail = false
        )
        val tail = maniaNotePieceGeometry(
            y = 199f,
            pieceHeight = 12f,
            isTail = true
        )
        val body = maniaHoldBodyBounds(
            headY = 200f,
            tailY = 199f,
            headHeight = 12f,
            tailHeight = 12f
        )

        assertTrue(tail.bottom <= head.bottom)
        assertEquals(193f, body.top, 0.0001f)
        assertEquals(194f, body.bottom, 0.0001f)
    }
}
