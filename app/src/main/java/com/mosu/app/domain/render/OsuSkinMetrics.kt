package com.mosu.app.domain.render

import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * Legacy-skin sizing constants and pure calculations used by [OsuRenderer].
 *
 * These mirror ppy/osu's legacy osu!standard skin transformer at commit
 * 4e11ab19feb423c905cf98d9b8fa34700a8aeffa. Keeping the calculations here
 * makes the 100% skin scale testable without touching Android graphics.
 */
object OsuLegacySkinMetrics {
    const val OBJECT_DIMENSIONS = 128f
    const val LEGACY_CIRCLE_RADIUS = 59f
    const val HIT_CIRCLE_TEXTURE_MAX_SIZE = 256f
    const val SLIDER_TEXTURE_MAX_SIZE = 384f
    const val HIT_CIRCLE_TEXT_SCALE = 0.8f
    const val HIT_CIRCLE_TEXT_TEXTURE_MAX_SIZE =
        HIT_CIRCLE_TEXTURE_MAX_SIZE / HIT_CIRCLE_TEXT_SCALE
    const val SLIDER_SHADOW_PORTION =
        1f - LEGACY_CIRCLE_RADIUS / OsuRenderObject.OSU_OBJECT_RADIUS
    const val SLIDER_BORDER_PORTION = 0.1875f
    const val SLIDER_BODY_OPACITY = 0.7f
    const val SPINNER_SPRITE_SCALE = 0.625f

    /**
     * Legacy spinners are authored in stable's 640x480 window space. The
     * playfield occupies 512x384 of that space, so their native sprites must
     * additionally be multiplied by 0.8 when drawn in playfield coordinates.
     */
    const val STABLE_PLAYFIELD_SCALE = 0.8f

    /**
     * The factor applied by `Texture.WithMaximumSize()`: preserve the texture's
     * aspect ratio and shrink it only when its largest side exceeds the cap.
     */
    fun textureFitScale(width: Int, height: Int, maxSize: Float): Float {
        if (width <= 0 || height <= 0 || maxSize <= 0f) return 0f
        return min(1f, maxSize / max(width, height).toFloat())
    }

    /**
     * Slider paths are drawn as a stroke whose radius is
     * [OsuRenderObject.OSU_OBJECT_RADIUS], exactly as lazer's legacy slider body
     * sets `PathRadius = OBJECT_RADIUS * objectScale`.
     */
    fun sliderBodyOuterWidth(objectScale: Float): Float =
        OBJECT_DIMENSIONS * objectScale

    /**
     * Diameter at the shadow/border boundary. Stable's legacy 59px circle
     * radius is also where the outer shadow portion of the path ends.
     */
    fun sliderBodyBorderWidth(objectScale: Float): Float =
        LEGACY_CIRCLE_RADIUS * 2f * objectScale

    /**
     * Body accent starts at `SLIDER_BORDER_PORTION` of the path radius. The
     * returned width is the diameter of that inner circle.
     */
    fun sliderBodyInnerWidth(objectScale: Float): Float =
        OBJECT_DIMENSIONS * (1f - SLIDER_BORDER_PORTION) * objectScale

    /**
     * Samples lazer's `LegacySliderBody.ColourAt()` at [position], where 0 is
     * the outer edge of the slider body and 1 is the centreline.
     */
    fun sliderCrossSectionColour(
        trackColour: Int,
        borderColour: Int,
        position: Float,
        opacity: Float = 1f
    ): Int {
        val clampedPosition = position.coerceIn(0f, 1f)
        val colour = when {
            clampedPosition <= SLIDER_SHADOW_PORTION -> {
                interpolateArgb(
                    start = Color.TRANSPARENT,
                    end = withOpacity(Color.BLACK, 0.25f),
                    amount = clampedPosition / SLIDER_SHADOW_PORTION
                )
            }
            clampedPosition <= SLIDER_BORDER_PORTION -> borderColour
            else -> sliderBodyAccentColour(
                trackColour = trackColour,
                position = (clampedPosition - SLIDER_BORDER_PORTION) /
                    (1f - SLIDER_BORDER_PORTION),
                opacity = 1f
            )
        }
        return withAlphaMultiplied(colour, opacity)
    }

    /**
     * Samples only the body accent, where [position] is 0 at the
     * border/accent boundary and 1 on the centreline.
     */
    fun sliderBodyAccentColour(
        trackColour: Int,
        position: Float,
        opacity: Float = 1f
    ): Int {
        val colour = interpolateArgb(
            start = withOpacity(
                darkenArgb(trackColour, 0.1f),
                SLIDER_BODY_OPACITY
            ),
            end = withOpacity(
                sliderBodyLightenArgb(trackColour, 0.5f),
                SLIDER_BODY_OPACITY
            ),
            amount = position.coerceIn(0f, 1f)
        )
        return withAlphaMultiplied(colour, opacity)
    }

    /** Matches osu.Framework's Color4.Darken(). */
    fun darkenArgb(colour: Int, amount: Float): Int {
        val factor = 1f / (1f + amount.coerceAtLeast(0f))
        return scaleArgb(colour, factor)
    }

    /**
     * Matches the legacy slider body's local `lighten()` helper.
     */
    fun sliderBodyLightenArgb(colour: Int, amount: Float): Int {
        val lightenAmount = amount.coerceAtLeast(0f) * 0.5f
        val factor = 1f + 0.5f * lightenAmount
        val offset = 1f * lightenAmount
        val alpha = colour ushr 24 and 0xFF
        val red = ((colour ushr 16 and 0xFF) / 255f * factor + offset)
            .coerceIn(0f, 1f)
        val green = ((colour ushr 8 and 0xFF) / 255f * factor + offset)
            .coerceIn(0f, 1f)
        val blue = ((colour and 0xFF) / 255f * factor + offset)
            .coerceIn(0f, 1f)
        return (alpha shl 24) or
            ((red * 255f).toInt() shl 16) or
            ((green * 255f).toInt() shl 8) or
            (blue * 255f).toInt()
    }

    fun interpolateArgb(start: Int, end: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val from = start ushr shift and 0xFF
            val to = end ushr shift and 0xFF
            return (from + (to - from) * t).toInt().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or
            (channel(16) shl 16) or
            (channel(8) shl 8) or
            channel(0)
    }

    fun comboOverlap(
        configuredOverlap: Float,
        objectScale: Float
    ): Float = configuredOverlap * HIT_CIRCLE_TEXT_SCALE * objectScale

    fun spinnerPlayfieldScale(): Float =
        SPINNER_SPRITE_SCALE * STABLE_PLAYFIELD_SCALE

    fun withOpacity(colour: Int, opacity: Float): Int {
        val alpha = (255f * opacity.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        return (colour and 0x00FFFFFF) or (alpha shl 24)
    }

    fun withAlphaMultiplied(colour: Int, opacity: Float): Int {
        val existingAlpha = colour ushr 24 and 0xFF
        val alpha = (existingAlpha * opacity.coerceIn(0f, 1f))
            .toInt()
            .coerceIn(0, 255)
        return (colour and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun scaleArgb(colour: Int, factor: Float): Int {
        val alpha = colour ushr 24 and 0xFF
        val red = ((colour ushr 16 and 0xFF) * factor).toInt().coerceIn(0, 255)
        val green = ((colour ushr 8 and 0xFF) * factor).toInt().coerceIn(0, 255)
        val blue = ((colour and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
