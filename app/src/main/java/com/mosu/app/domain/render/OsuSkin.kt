package com.mosu.app.domain.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Decoded, immutable skin resources. [OsuSkinLoader] owns all asset I/O and is
 * called from a background dispatcher before the renderer's frame loop starts.
 */
data class OsuSkin(
    val name: String,
    val author: String,
    val version: String,
    val comboColours: List<Int>,
    val sliderTrackOverride: Int?,
    val sliderBorder: Int?,
    val hitCircleOverlap: Float,
    val hitCircleOverlayAboveNumber: Boolean,
    val hitCircle: Bitmap?,
    val hitCircleOverlay: Bitmap?,
    val approachCircle: Bitmap?,
    val sliderBall: Bitmap?,
    val sliderFollowCircle: Bitmap?,
    val sliderStartCircle: Bitmap?,
    val sliderStartOverlay: Bitmap?,
    val sliderEndCircle: Bitmap?,
    val sliderEndOverlay: Bitmap?,
    val reverseArrow: Bitmap?,
    val spinnerApproach: Bitmap?,
    val spinnerBackground: Bitmap?,
    val spinnerCircle: Bitmap?,
    val spinnerGlow: Bitmap?,
    val spinnerMiddle: Bitmap?,
    val digits: List<Bitmap?>,
    val digitWidthRatios: FloatArray
) {
    fun paletteColours(): List<Int> =
        if (comboColours.isNotEmpty()) comboColours else DEFAULT_COMBO_COLOURS

    fun digitAt(index: Int): Bitmap? = digits.getOrNull(index) ?: digits.lastOrNull()

    fun digitWidthRatio(index: Int): Float =
        digitWidthRatios.getOrNull(index)
            ?: digitWidthRatios.lastOrNull()
            ?: 1f

    companion object {
        val DEFAULT_COMBO_COLOURS = listOf(
            0xFFFF79A5.toInt(),
            0xFF7FE2FF.toInt(),
            0xFFF5D95B.toInt(),
            0xFF8CF07B.toInt(),
            0xFFB58CFF.toInt()
        )
    }
}

object OsuSkinLoader {
    private const val SKIN_ROOT = "skins/vv_idke_trail"
    private const val MIN_NON_BLANK_BYTES = 256

    @Volatile
    private var cached: OsuSkin? = null

    /** Must be invoked off the main thread. */
    @Synchronized
    fun load(context: Context): OsuSkin {
        cached?.let { return it }
        val assets = context.applicationContext.assets
        val iniBytes = assets.open("$SKIN_ROOT/Skin.ini").use { it.readBytes() }
        val ini = SkinIni.parse(iniBytes.toString(Charsets.UTF_8))
        val digits = (0..9).map { loadBitmap(assets, "default-$it.png") }
        val skin = OsuSkin(
            name = ini.value("General", "Name") ?: "vv idke trail",
            author = ini.value("General", "Author") ?: "[Garin] + Aristia + Various",
            version = ini.value("General", "Version") ?: "2.4",
            comboColours = ini.comboColours(),
            sliderTrackOverride = ini.colour("SliderTrackOverride"),
            sliderBorder = ini.colour("SliderBorder"),
            hitCircleOverlap = ini.value("Fonts", "HitCircleOverlap")
                ?.toFloatOrNull()
                ?.coerceAtLeast(0f)
                ?: 6f,
            hitCircleOverlayAboveNumber = ini.value("General", "HitCircleOverlayAboveNumer")
                ?.toIntOrNull()
                ?.let { it != 0 }
                ?: true,
            hitCircle = loadBitmap(assets, "hitcircle.png"),
            hitCircleOverlay = loadBitmap(assets, "hitcircleoverlay.png"),
            approachCircle = loadBitmap(assets, "approachcircle.png"),
            sliderBall = loadBitmap(assets, "sliderb0.png"),
            sliderFollowCircle = loadBitmap(assets, "sliderfollowcircle.png"),
            sliderStartCircle = loadBitmap(assets, "sliderstartcircle.png"),
            sliderStartOverlay = loadBitmap(assets, "sliderstartcircleoverlay.png"),
            sliderEndCircle = loadBitmap(assets, "sliderendcircle.png"),
            sliderEndOverlay = loadBitmap(assets, "sliderendcircleoverlay.png"),
            reverseArrow = loadBitmap(assets, "reversearrow.png"),
            spinnerApproach = loadBitmap(assets, "spinner-approachcircle.png"),
            spinnerBackground = loadBitmap(assets, "spinner-background.png"),
            spinnerCircle = loadBitmap(assets, "spinner-circle.png"),
            spinnerGlow = loadBitmap(assets, "spinner-glow.png"),
            spinnerMiddle = loadBitmap(assets, "spinner-middle.png"),
            digits = digits,
            digitWidthRatios = digits.map { bitmap ->
                if (bitmap == null || bitmap.height <= 0) 1f
                else bitmap.width.toFloat() / bitmap.height
            }.toFloatArray()
        )
        cached = skin
        return skin
    }

    private fun loadBitmap(
        assets: android.content.res.AssetManager,
        name: String
    ): Bitmap? = runCatching {
        assets.open("$SKIN_ROOT/$name").use { input ->
            val bytes = input.readBytes()
            // Transparent placeholder files occur in some community skins.
            if (bytes.size < MIN_NON_BLANK_BYTES) return@use null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@use null
            if (!hasVisiblePixels(bitmap)) {
                bitmap.recycle()
                return@use null
            }
            bitmap
        }
    }.getOrNull()

    /**
     * Community skins commonly include 1x1 or fully transparent files as
     * placeholders for components they do not supply. Decoding those as real
     * textures produces invisible or badly-sized pieces, so discard them while
     * the skin is loaded rather than handling the mistake on every frame.
     */
    private fun hasVisiblePixels(bitmap: Bitmap): Boolean {
        if (bitmap.width <= 1 || bitmap.height <= 1) return false
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (pixel in row) {
                if (pixel ushr 24 != 0) return true
            }
        }
        return false
    }
}

private class SkinIni private constructor(
    private val values: Map<String, Map<String, String>>
) {
    fun value(section: String, key: String): String? =
        values[section.lowercase()]?.get(key.lowercase())

    fun colour(key: String): Int? = value("Colours", key)?.let(::parseColour)

    fun comboColours(): List<Int> = values["colours"]
        ?.entries
        ?.filter { it.key.startsWith("combo") }
        ?.sortedBy { it.key.removePrefix("combo").toIntOrNull() ?: Int.MAX_VALUE }
        ?.mapNotNull { parseColour(it.value) }
        ?.distinct()
        .orEmpty()

    companion object {
        fun parse(text: String): SkinIni {
            val values = linkedMapOf<String, MutableMap<String, String>>()
            var section = "general"
            text.removePrefix("\uFEFF").lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("//") || line.startsWith(";")) {
                    return@forEach
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length - 1).trim().lowercase()
                    return@forEach
                }
                val separator = line.indexOf(':')
                if (separator <= 0) return@forEach
                val key = line.substring(0, separator).trim().lowercase()
                val value = line.substring(separator + 1).trim()
                values.getOrPut(section) { linkedMapOf() }[key] = value
            }
            return SkinIni(values)
        }

        fun parseColour(value: String): Int? {
            val components = value.split(',').mapNotNull { it.trim().toIntOrNull() }
            if (components.size < 3 || components.any { it !in 0..255 }) return null
            val alpha = components.getOrNull(3) ?: 255
            return (alpha shl 24) or
                (components[0] shl 16) or
                (components[1] shl 8) or
                components[2]
        }
    }
}
