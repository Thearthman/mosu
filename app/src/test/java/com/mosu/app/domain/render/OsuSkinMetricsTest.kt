package com.mosu.app.domain.render

import org.junit.Assert.assertEquals
import org.junit.Test

class OsuSkinMetricsTest {
    @Test
    fun legacyTexturesUseTheirIntrinsicSizeAtOneHundredPercentSkinScale() {
        val objectScale = circleScaleFromCircleSize(5f)

        assertEquals(
            64.02624f,
            128f * OsuLegacySkinMetrics.textureFitScale(
                128,
                128,
                OsuLegacySkinMetrics.HIT_CIRCLE_TEXTURE_MAX_SIZE
            ) * objectScale,
            0.0001f
        )
        assertEquals(
            85.03485f,
            170f * OsuLegacySkinMetrics.textureFitScale(
                170,
                170,
                OsuLegacySkinMetrics.SLIDER_TEXTURE_MAX_SIZE
            ) * objectScale,
            0.0001f
        )
        assertEquals(
            64.02624f,
            256f * OsuLegacySkinMetrics.textureFitScale(
                256,
                256,
                OsuLegacySkinMetrics.SLIDER_TEXTURE_MAX_SIZE
            ) * 0.5f * objectScale,
            0.0001f
        )
        assertEquals(
            64.02624f,
            128f * OsuLegacySkinMetrics.textureFitScale(
                128,
                128,
                OsuLegacySkinMetrics.HIT_CIRCLE_TEXTURE_MAX_SIZE
            ) * objectScale,
            0.0001f
        )
    }

    @Test
    fun sliderBodyUsesLegacyOuterAndInnerDiameters() {
        val objectScale = circleScaleFromCircleSize(5f)

        assertEquals(
            64.02624f,
            OsuLegacySkinMetrics.sliderBodyOuterWidth(objectScale),
            0.0001f
        )
        assertEquals(
            59.02419f,
            OsuLegacySkinMetrics.sliderBodyBorderWidth(objectScale),
            0.0001f
        )
        assertEquals(
            52.02132f,
            OsuLegacySkinMetrics.sliderBodyInnerWidth(objectScale),
            0.0001f
        )
    }

    @Test
    fun sliderCrossSectionSamplesLegacyShadowBorderAndAccent() {
        val trackColour = 0xFF1A74F2.toInt()
        val borderColour = 0xFF787878.toInt()

        assertEquals(
            0.078125f,
            OsuLegacySkinMetrics.SLIDER_SHADOW_PORTION,
            0.000001f
        )
        val outer = OsuLegacySkinMetrics.sliderCrossSectionColour(
            trackColour,
            borderColour,
            position = 0f
        )
        val shadowBoundary = OsuLegacySkinMetrics.sliderCrossSectionColour(
            trackColour,
            borderColour,
            position = OsuLegacySkinMetrics.SLIDER_SHADOW_PORTION
        )
        val border = OsuLegacySkinMetrics.sliderCrossSectionColour(
            trackColour,
            borderColour,
            position = OsuLegacySkinMetrics.SLIDER_SHADOW_PORTION + 0.01f
        )
        val centre = OsuLegacySkinMetrics.sliderCrossSectionColour(
            trackColour,
            borderColour,
            position = 1f
        )

        assertEquals(0, outer ushr 24)
        assertEquals(63, shadowBoundary ushr 24)
        assertEquals(borderColour, border)
        assertEquals(
            (255f * OsuLegacySkinMetrics.SLIDER_BODY_OPACITY).toInt(),
            centre ushr 24
        )
    }

    @Test
    fun comboTextPreservesGlyphAspectAndUsesLegacyOverlap() {
        val objectScale = circleScaleFromCircleSize(5f)
        val digitWidth = 41f * OsuLegacySkinMetrics.HIT_CIRCLE_TEXT_SCALE * objectScale
        val digitHeight = 60f * OsuLegacySkinMetrics.HIT_CIRCLE_TEXT_SCALE * objectScale

        assertEquals(16.40672f, digitWidth, 0.0001f)
        assertEquals(24.00984f, digitHeight, 0.0001f)
        assertEquals(
            2.400984f,
            OsuLegacySkinMetrics.comboOverlap(6f, objectScale),
            0.0001f
        )
    }

    @Test
    fun maximumTextureSizeOnlyShrinksAndPreservesAspectRatio() {
        assertEquals(1f, OsuLegacySkinMetrics.textureFitScale(128, 128, 256f), 0f)
        assertEquals(
            0.25f,
            OsuLegacySkinMetrics.textureFitScale(1024, 256, 256f),
            0.0001f
        )
        assertEquals(0f, OsuLegacySkinMetrics.textureFitScale(0, 128, 256f), 0f)
    }

    @Test
    fun playfieldFitsInsideSevenBySixCanvasWithObjectMarginAndEdgeInset() {
        val scale = osuPlayfieldScale(
            canvasWidthPx = 700f,
            canvasHeightPx = 600f,
            edgeInsetPx = 10f
        )

        assertEquals(680f / 640f, scale, 0.0001f)
        assertEquals(544f, OSU_PLAYFIELD_WIDTH * scale, 0.0001f)
        assertEquals(408f, OSU_PLAYFIELD_HEIGHT * scale, 0.0001f)

        val playfieldLeft = (700f - OSU_PLAYFIELD_WIDTH * scale) / 2f
        val playfieldTop = (600f - OSU_PLAYFIELD_HEIGHT * scale) / 2f
        assertEquals(10f, playfieldLeft - 64f * scale, 0.0001f)
        assertEquals(28f, playfieldTop - 64f * scale, 0.0001f)
    }
}
