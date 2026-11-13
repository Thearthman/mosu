package com.mosu.app.domain.render.mania

import com.mosu.app.domain.render.SkinIni
import kotlin.math.min

enum class ManiaNoteBodyStyle(val legacyValue: Int) {
    Stretch(0),
    RepeatTop(2),
    RepeatBottom(3),
    RepeatTopAndBottom(4);

    companion object {
        fun fromLegacyValue(value: Int): ManiaNoteBodyStyle? =
            entries.firstOrNull { it.legacyValue == value }

        fun fromLegacyName(value: String): ManiaNoteBodyStyle? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

/**
 * The part of a legacy mania skin that affects the beatmap preview.
 *
 * Pixel dimensions are already converted with Stable's legacy scale factor.
 * [hitPositionFromBottom] is a bottom-relative offset in the renderer's
 * coordinate space. A null value means the skin did not explicitly configure
 * a hit position.
 */
data class LegacyManiaSkinConfiguration(
    val keyCount: Int,
    val columnWidths: List<Float>,
    val columnSpacings: List<Float>,
    val columnLineWidths: List<Float>,
    val hitPositionFromBottom: Float?,
    val barlineHeight: Float,
    val showJudgementLine: Boolean,
    val keysUnderNotes: Boolean,
    val noteBodyStyle: ManiaNoteBodyStyle,
    val widthForNoteHeightScale: Float?,
    val columnColours: List<Int>,
    val columnLineColour: Int?,
    val judgementLineColour: Int?,
    val barlineColour: Int?,
    val lightColours: List<Int?>,
    val imageLookups: Map<String, String>
) {
    fun resolvedNoteHeight(columnWidth: Float): Float =
        widthForNoteHeightScale
            ?.takeIf { it > 0f }
            ?: columnWidth.coerceAtLeast(1f)

    fun imageNameFor(
        column: Int,
        imageKind: ManiaSkinImageKind
    ): String? {
        val key = when (imageKind) {
            ManiaSkinImageKind.NOTE,
            ManiaSkinImageKind.HOLD_HEAD,
            ManiaSkinImageKind.HOLD_TAIL,
            ManiaSkinImageKind.HOLD_BODY ->
                "NoteImage$column${imageKind.explicitSuffix}"
            ManiaSkinImageKind.KEY_UP,
            ManiaSkinImageKind.KEY_DOWN ->
                "KeyImage$column${imageKind.explicitSuffix}"
            ManiaSkinImageKind.STAGE_LEFT,
            ManiaSkinImageKind.STAGE_RIGHT,
            ManiaSkinImageKind.STAGE_BOTTOM,
            ManiaSkinImageKind.STAGE_HINT,
            ManiaSkinImageKind.STAGE_LIGHT ->
                imageKind.explicitSuffix
        }
        return imageLookup(key)
    }

    private fun imageLookup(key: String): String? = imageLookups[key.lowercase()]
}

enum class ManiaSkinImageKind(
    val explicitSuffix: String,
    val fallbackPrefix: String
) {
    NOTE("", "mania-note"),
    HOLD_HEAD("H", "mania-note"),
    HOLD_TAIL("T", "mania-note"),
    HOLD_BODY("L", "mania-note"),
    KEY_UP("", "mania-key"),
    KEY_DOWN("D", "mania-key"),
    STAGE_LEFT("StageLeft", "mania-stage-left"),
    STAGE_RIGHT("StageRight", "mania-stage-right"),
    STAGE_BOTTOM("StageBottom", "mania-stage-bottom"),
    STAGE_HINT("StageHint", "mania-stage-hint"),
    STAGE_LIGHT("StageLight", "mania-stage-light")
}

data class LegacyManiaSkinImageRequest(
    val explicitName: String?,
    val fallbackName: String
)

object LegacyManiaSkinParser {
    const val STABLE_MAGIC_SCALE_FACTOR = 1.6f
    const val DEFAULT_COLUMN_WIDTH = 30f * STABLE_MAGIC_SCALE_FACTOR
    const val DEFAULT_HIT_POSITION_FROM_BOTTOM =
        (480f - 402f) * STABLE_MAGIC_SCALE_FACTOR
    const val DEFAULT_COLUMN_LINE_WIDTH = 2f
    const val DEFAULT_BARLINE_HEIGHT = 1.2f

    fun parse(text: String, keyCount: Int): LegacyManiaSkinConfiguration? {
        if (keyCount !in 1..10) return null
        val legacyIni = runCatching { SkinIni.parse(text) }.getOrNull()
        val version = legacyIni?.value("General", "Version")?.toDoubleOrNull()
        val defaultBodyStyle = if (version != null && version < 2.5) {
            ManiaNoteBodyStyle.Stretch
        } else {
            ManiaNoteBodyStyle.RepeatBottom
        }
        return parseBlocks(text)
            .asSequence()
            .mapNotNull { block -> parseBlock(block, keyCount, defaultBodyStyle) }
            .firstOrNull { it.keyCount == keyCount }
    }

    fun imageRequest(
        config: LegacyManiaSkinConfiguration?,
        keyCount: Int,
        column: Int,
        kind: ManiaSkinImageKind
    ): LegacyManiaSkinImageRequest {
        val explicit = config?.imageNameFor(column, kind)
        val fallback = when (kind) {
            ManiaSkinImageKind.NOTE,
            ManiaSkinImageKind.HOLD_HEAD,
            ManiaSkinImageKind.HOLD_TAIL,
            ManiaSkinImageKind.HOLD_BODY -> {
                "${kind.fallbackPrefix}${maniaColumnFallbackSuffix(keyCount, column)}" +
                    kind.explicitSuffix
            }
            ManiaSkinImageKind.KEY_UP,
            ManiaSkinImageKind.KEY_DOWN -> {
                "${kind.fallbackPrefix}${maniaColumnFallbackSuffix(keyCount, column)}" +
                    kind.explicitSuffix
            }
            ManiaSkinImageKind.STAGE_LEFT,
            ManiaSkinImageKind.STAGE_RIGHT,
            ManiaSkinImageKind.STAGE_BOTTOM,
            ManiaSkinImageKind.STAGE_HINT,
            ManiaSkinImageKind.STAGE_LIGHT -> kind.fallbackPrefix
        }
        return LegacyManiaSkinImageRequest(explicitName = explicit, fallbackName = fallback)
    }

    private fun parseBlocks(text: String): List<List<String>> {
        val blocks = mutableListOf<List<String>>()
        var current: MutableList<String>? = null
        text.removePrefix("\uFEFF").lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("[") && line.endsWith("]")) {
                current?.let { blocks += it }
                current = if (line.substring(1, line.length - 1).trim()
                        .equals("Mania", ignoreCase = true)
                ) {
                    mutableListOf()
                } else {
                    null
                }
                return@forEach
            }
            if (line.isEmpty() || line.startsWith("//") || line.startsWith(";")) {
                return@forEach
            }
            current?.add(line)
        }
        current?.let { blocks += it }
        return blocks
    }

    private fun parseBlock(
        lines: List<String>,
        requestedKeyCount: Int,
        defaultBodyStyle: ManiaNoteBodyStyle
    ): LegacyManiaSkinConfiguration? {
        val pairs = lines.mapNotNull(::parseKeyValue)
        val keys = pairs.firstOrNull { it.first.equals("Keys", ignoreCase = true) }
            ?.second
            ?.toIntOrNull()
            ?: return null
        if (keys != requestedKeyCount) {
            return LegacyManiaSkinConfiguration(
                keyCount = keys,
                columnWidths = emptyList(),
                columnSpacings = emptyList(),
                columnLineWidths = emptyList(),
                hitPositionFromBottom = DEFAULT_HIT_POSITION_FROM_BOTTOM,
                barlineHeight = DEFAULT_BARLINE_HEIGHT,
                showJudgementLine = true,
                keysUnderNotes = false,
                noteBodyStyle = defaultBodyStyle,
                widthForNoteHeightScale = null,
                columnColours = emptyList(),
                columnLineColour = null,
                judgementLineColour = null,
                barlineColour = null,
                lightColours = emptyList(),
                imageLookups = emptyMap()
            )
        }

        val columnWidths = MutableList(keys) { DEFAULT_COLUMN_WIDTH }
        val columnSpacings = MutableList((keys - 1).coerceAtLeast(0)) { 0f }
        val columnLineWidths = MutableList(keys + 1) { DEFAULT_COLUMN_LINE_WIDTH }
        val columnColours = MutableList<Int?>(keys) { null }
        val lightColours = MutableList<Int?>(keys) { null }
        val imageLookups = linkedMapOf<String, String>()
        var hitPositionFromBottom: Float? = DEFAULT_HIT_POSITION_FROM_BOTTOM
        var barlineHeight = DEFAULT_BARLINE_HEIGHT
        var showJudgementLine = true
        var keysUnderNotes = false
        var noteBodyStyle = defaultBodyStyle
        var widthForNoteHeightScale: Float? = null
        var columnLineColour: Int? = null
        var judgementLineColour: Int? = null
        var barlineColour: Int? = null

        pairs.forEach { (rawKey, rawValue) ->
            val key = rawKey.trim()
            val value = rawValue.trim()
            when {
                key.equals("Keys", ignoreCase = true) -> Unit
                key.equals("ColumnWidth", ignoreCase = true) -> {
                    parseScaledArray(value, columnWidths)
                }
                key.equals("ColumnSpacing", ignoreCase = true) -> {
                    parseScaledArray(value, columnSpacings)
                }
                key.equals("ColumnLineWidth", ignoreCase = true) -> {
                    parseArray(value, columnLineWidths, scale = false)
                }
                key.equals("BarlineHeight", ignoreCase = true) -> {
                    value.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }
                        ?.let { barlineHeight = it }
                }
                key.equals("HitPosition", ignoreCase = true) -> {
                    value.toFloatOrNull()?.takeIf { it.isFinite() }?.let { hitPosition ->
                        hitPositionFromBottom =
                            (480f - hitPosition.coerceIn(240f, 480f)) *
                                STABLE_MAGIC_SCALE_FACTOR
                    }
                }
                key.equals("JudgementLine", ignoreCase = true) -> {
                    showJudgementLine = value == "1"
                }
                key.equals("KeysUnderNotes", ignoreCase = true) -> {
                    keysUnderNotes = value == "1"
                }
                key.equals("NoteBodyStyle", ignoreCase = true) -> {
                    val style = value.toIntOrNull()
                        ?.let(ManiaNoteBodyStyle::fromLegacyValue)
                        ?: ManiaNoteBodyStyle.fromLegacyName(value)
                    if (style != null) noteBodyStyle = style
                }
                key.equals("WidthForNoteHeightScale", ignoreCase = true) -> {
                    value.toFloatOrNull()?.takeIf { it.isFinite() }?.let {
                        widthForNoteHeightScale = it * STABLE_MAGIC_SCALE_FACTOR
                    }
                }
                key.equals("ColourColumnLine", ignoreCase = true) -> {
                    columnLineColour = SkinIni.parseColour(value)
                }
                key.equals("ColourJudgementLine", ignoreCase = true) -> {
                    judgementLineColour = SkinIni.parseColour(value)
                }
                key.equals("ColourBarline", ignoreCase = true) -> {
                    barlineColour = SkinIni.parseColour(value)
                }
                key.startsWith("ColourLight", ignoreCase = true) -> {
                    val index = key.substringAfter("ColourLight", "")
                        .toIntOrNull()
                        ?.minus(1)
                        ?: -1
                    if (index in lightColours.indices) {
                        lightColours[index] = SkinIni.parseColour(value)
                    }
                }
                key.startsWith("Colour", ignoreCase = true) -> {
                    val index = key.substringAfter("Colour", "")
                        .toIntOrNull()
                        ?.minus(1)
                        ?: -1
                    if (index in columnColours.indices) {
                        columnColours[index] = SkinIni.parseColour(value)
                    }
                }
                key.startsWith("NoteImage", ignoreCase = true) ||
                    key.startsWith("KeyImage", ignoreCase = true) ||
                    key.startsWith("Stage", ignoreCase = true) -> {
                    imageLookups[key.lowercase()] = value
                }
            }
        }

        return LegacyManiaSkinConfiguration(
            keyCount = keys,
            columnWidths = columnWidths,
            columnSpacings = columnSpacings,
            columnLineWidths = columnLineWidths,
            hitPositionFromBottom = hitPositionFromBottom,
            barlineHeight = barlineHeight,
            showJudgementLine = showJudgementLine,
            keysUnderNotes = keysUnderNotes,
            noteBodyStyle = noteBodyStyle,
            widthForNoteHeightScale = widthForNoteHeightScale,
            columnColours = columnColours.map { it ?: DEFAULT_ACCENT_COLOUR },
            columnLineColour = columnLineColour,
            judgementLineColour = judgementLineColour,
            barlineColour = barlineColour,
            lightColours = lightColours,
            imageLookups = imageLookups
        )
    }

    private fun parseScaledArray(value: String, output: MutableList<Float>) {
        parseArray(value, output, scale = true)
    }

    private fun parseArray(
        value: String,
        output: MutableList<Float>,
        scale: Boolean
    ) {
        value.split(',').forEachIndexed { index, raw ->
            if (index >= output.size) return@forEachIndexed
            val parsed = raw.trim().toFloatOrNull()
                ?.takeIf { it.isFinite() }
                ?: 0f
            output[index] = parsed * if (scale) STABLE_MAGIC_SCALE_FACTOR else 1f
        }
    }

    private fun parseKeyValue(line: String): Pair<String, String>? {
        val colon = line.indexOf(':')
        val equals = line.indexOf('=')
        val separator = when {
            colon > 0 && equals > 0 -> min(colon, equals)
            colon > 0 -> colon
            equals > 0 -> equals
            else -> -1
        }
        if (separator <= 0) return null
        return line.substring(0, separator).trim() to
            line.substring(separator + 1).trim()
    }

    const val DEFAULT_ACCENT_COLOUR: Int = 0xFFFFFFFF.toInt()
}
