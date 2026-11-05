package com.mosu.app.domain.render

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Decoded skin resources. [OsuSkinLoader] owns all asset I/O and is called from
 * a background dispatcher before the renderer's frame loop starts.
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
    val digitWidthRatios: FloatArray,
    val layeredHitSounds: Boolean,
    private val ownedBitmaps: List<Bitmap> = emptyList()
) {
    fun paletteColours(): List<Int> =
        if (comboColours.isNotEmpty()) comboColours else DEFAULT_COMBO_COLOURS

    fun digitAt(index: Int): Bitmap? = digits.getOrNull(index) ?: digits.lastOrNull()

    fun digitWidthRatio(index: Int): Float =
        digitWidthRatios.getOrNull(index)
            ?: digitWidthRatios.lastOrNull()
            ?: 1f

    /**
     * Decoded imported-skin bitmaps belong to this instance. Bundled bitmaps
     * are intentionally absent from [ownedBitmaps] because they may be reused
     * by a future bundled-skin cache.
     */
    fun close() {
        ownedBitmaps.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

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

    /** Must be invoked off the main thread. */
    fun load(
        context: Context,
        selection: RenderSkinSelection = RenderSkinSelection.Bundled
    ): OsuSkin {
        val assets = context.applicationContext.assets
        val bundledIni = SkinIni.parse(
            assets.open("$SKIN_ROOT/Skin.ini").use { it.readBytes() }
                .toString(Charsets.UTF_8)
        )
        val customRoot = selection.directory?.takeIf(File::isDirectory)
        val customFiles = customRoot?.let(SkinFileIndex::build)
        val customIni = customFiles?.find("skin.ini")?.let { file ->
            runCatching {
                SkinIni.parse(file.readText(Charsets.UTF_8))
            }.getOrNull()
        }
        val imported = customRoot != null
        val ownedBitmaps = mutableListOf<Bitmap>()

        fun setting(section: String, key: String): String? =
            customIni?.value(section, key)?.takeIf { it.isNotBlank() }
                ?: bundledIni.value(section, key)?.takeIf { it.isNotBlank() }

        fun bitmap(name: String): Bitmap? {
            customFiles?.find(name)?.let { file ->
                runCatching { file.readBytes() }.getOrNull()
                    ?.let { bytes ->
                        decodeBitmap(bytes, owned = true)?.let { bitmap ->
                            ownedBitmaps += bitmap
                            return bitmap
                        }
                    }
            }
            return loadBundledBitmap(assets, name)
        }

        val digits = (0..9).map { bitmap("default-$it.png") }
        val skin = OsuSkin(
            name = if (imported) {
                customIni?.value("General", "Name")?.takeIf { it.isNotBlank() }
                    ?: selection.name?.takeIf { it.isNotBlank() }
                    ?: "Imported skin"
            } else {
                bundledIni.value("General", "Name") ?: "vv idke trail"
            },
            author = if (imported) {
                customIni?.value("General", "Author").orEmpty()
            } else {
                bundledIni.value("General", "Author") ?: "[Garin] + Aristia + Various"
            },
            version = if (imported) {
                customIni?.value("General", "Version").orEmpty()
            } else {
                bundledIni.value("General", "Version") ?: "2.4"
            },
            comboColours = customIni?.comboColours()?.takeIf { it.isNotEmpty() }
                ?: bundledIni.comboColours(),
            sliderTrackOverride = if (imported) {
                customIni?.colour("SliderTrackOverride")
            } else {
                bundledIni.colour("SliderTrackOverride")
            },
            sliderBorder = if (imported) {
                customIni?.colour("SliderBorder")
            } else {
                bundledIni.colour("SliderBorder")
            },
            hitCircleOverlap = setting("Fonts", "HitCircleOverlap")
                ?.toFloatOrNull()
                ?.coerceAtLeast(0f)
                ?: 6f,
            hitCircleOverlayAboveNumber = (
                customIni?.value("General", "HitCircleOverlayAboveNumer")
                    ?: customIni?.value("General", "HitCircleOverlayAboveNumber")
                    ?: bundledIni.value("General", "HitCircleOverlayAboveNumer")
                )?.toIntOrNull()?.let { it != 0 } ?: true,
            hitCircle = bitmap("hitcircle.png"),
            hitCircleOverlay = bitmap("hitcircleoverlay.png"),
            approachCircle = bitmap("approachcircle.png"),
            sliderBall = bitmap("sliderb0.png"),
            sliderFollowCircle = bitmap("sliderfollowcircle.png"),
            sliderStartCircle = bitmap("sliderstartcircle.png"),
            sliderStartOverlay = bitmap("sliderstartcircleoverlay.png"),
            sliderEndCircle = bitmap("sliderendcircle.png"),
            sliderEndOverlay = bitmap("sliderendcircleoverlay.png"),
            reverseArrow = bitmap("reversearrow.png"),
            spinnerApproach = bitmap("spinner-approachcircle.png"),
            spinnerBackground = bitmap("spinner-background.png"),
            spinnerCircle = bitmap("spinner-circle.png"),
            spinnerGlow = bitmap("spinner-glow.png"),
            spinnerMiddle = bitmap("spinner-middle.png"),
            digits = digits,
            digitWidthRatios = digits.map { bitmap ->
                if (bitmap == null || bitmap.height <= 0) 1f
                else bitmap.width.toFloat() / bitmap.height
            }.toFloatArray(),
            layeredHitSounds = customIni?.boolean(
                section = "General",
                key = "LayeredHitSounds",
                default = bundledIni.boolean("General", "LayeredHitSounds", true)
            ) ?: bundledIni.boolean("General", "LayeredHitSounds", true),
            ownedBitmaps = ownedBitmaps
        )
        return skin
    }

    private fun loadBundledBitmap(
        assets: AssetManager,
        name: String
    ): Bitmap? = runCatching {
        assets.open("$SKIN_ROOT/$name").use { input ->
            decodeBitmap(input.readBytes(), owned = false)
        }
    }.getOrNull()

    private fun decodeBitmap(bytes: ByteArray, owned: Boolean): Bitmap? {
        // Transparent placeholder files occur in some community skins.
        if (bytes.size < MIN_NON_BLANK_BYTES) return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        if (!hasVisiblePixels(bitmap)) {
            if (owned) bitmap.recycle()
            return null
        }
        return bitmap
    }

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

internal class SkinFileIndex private constructor(
    private val files: Map<String, File>
) {
    val size: Int get() = files.size

    fun find(name: String): File? {
        val normalized = name.replace('\\', '/').trimStart('/').lowercase()
        return files[normalized]
            ?: files.values.firstOrNull { file ->
                file.name.lowercase() == normalized.substringAfterLast('/')
            }
    }

    companion object {
        fun build(root: File): SkinFileIndex {
            val files = linkedMapOf<String, File>()
            root.walkTopDown()
                .filter(File::isFile)
                .forEach { file ->
                    val relative = file.relativeTo(root)
                        .invariantSeparatorsPath
                        .lowercase()
                    files.putIfAbsent(relative, file)
                }
            return SkinFileIndex(files)
        }
    }
}
