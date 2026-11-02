package com.mosu.app.domain.render

import java.io.File

/**
 * Per-object legacy sample metadata. Null banks mean "inherit the timing
 * point"; a zero custom index and volume mean the same.
 */
data class OsuRawSample(
    val normalBank: RenderSampleBank? = null,
    val additionBank: RenderSampleBank? = null,
    val customSampleIndex: Int = 0,
    val volume: Int = 0,
    val filename: String? = null,
    val normalBankSpecified: Boolean = normalBank != null,
    val additionBankSpecified: Boolean = additionBank != null
) {
    fun overlay(other: OsuRawSample): OsuRawSample = OsuRawSample(
        normalBank = if (other.normalBankSpecified) other.normalBank else normalBank,
        additionBank = if (other.additionBankSpecified) other.additionBank else additionBank,
        customSampleIndex = other.customSampleIndex.takeIf { it > 0 } ?: customSampleIndex,
        volume = other.volume.takeIf { it > 0 } ?: volume,
        filename = other.filename ?: filename,
        normalBankSpecified = normalBankSpecified || other.normalBankSpecified,
        additionBankSpecified = additionBankSpecified || other.additionBankSpecified
    )
}

data class OsuRawHitSample(
    val hitSound: Int = 0,
    val sample: OsuRawSample = OsuRawSample()
)

sealed class OsuRawHitObject {
    abstract val timeMs: Double
    abstract val position: RenderPoint
    abstract val newCombo: Boolean
    abstract val comboOffset: Int
}

data class OsuRawCircle(
    override val timeMs: Double,
    override val position: RenderPoint,
    override val newCombo: Boolean,
    override val comboOffset: Int,
    val hitSample: OsuRawHitSample = OsuRawHitSample()
) : OsuRawHitObject()

data class OsuRawSlider(
    override val timeMs: Double,
    override val position: RenderPoint,
    override val newCombo: Boolean,
    override val comboOffset: Int,
    val pathControlPoints: List<OsuPathControlPoint>,
    val spanCount: Int,
    val pathLength: Float,
    /** Head, repeat and tail nodes in that order. */
    val nodeSamples: List<OsuRawHitSample> = emptyList(),
    /** Object-wide sample settings used by ticks and the sliding body loop. */
    val baseHitSample: OsuRawHitSample = OsuRawHitSample()
) : OsuRawHitObject() {
    /** Compatibility view of the first explicit segment's type. */
    val curveType: OsuCurveType
        get() = pathControlPoints.firstOrNull()?.curveType ?: OsuCurveType.LINEAR

    /** Compatibility view used by callers that only need raw coordinates. */
    val controlPoints: List<RenderPoint>
        get() = pathControlPoints.drop(1).map { it.position }

    constructor(
        timeMs: Double,
        position: RenderPoint,
        newCombo: Boolean,
        comboOffset: Int,
        curveType: OsuCurveType,
        controlPoints: List<RenderPoint>,
        spanCount: Int,
        pathLength: Float,
        nodeSamples: List<OsuRawHitSample> = emptyList(),
        baseHitSample: OsuRawHitSample = OsuRawHitSample()
    ) : this(
        timeMs = timeMs,
        position = position,
        newCombo = newCombo,
        comboOffset = comboOffset,
        pathControlPoints = buildList {
            add(OsuPathControlPoint(position, curveType))
            controlPoints.forEach { add(OsuPathControlPoint(it)) }
        },
        spanCount = spanCount,
        pathLength = pathLength,
        nodeSamples = nodeSamples,
        baseHitSample = baseHitSample
    )
}

data class OsuRawSpinner(
    override val timeMs: Double,
    val endTimeMs: Double,
    override val newCombo: Boolean,
    override val comboOffset: Int,
    val hitSample: OsuRawHitSample = OsuRawHitSample()
) : OsuRawHitObject() {
    override val position: RenderPoint
        get() = RenderPoint(OSU_PLAYFIELD_WIDTH / 2f, OSU_PLAYFIELD_HEIGHT / 2f)
}

data class OsuRawTimingPoint(
    val timeMs: Double,
    val beatLengthMs: Double,
    val meter: Int,
    val uninherited: Boolean,
    val sampleBank: RenderSampleBank = RenderSampleBank.NORMAL,
    val customSampleIndex: Int = 0,
    val sampleVolume: Int = 100
)

data class ParsedOsuMap(
    val header: OsuMapHeader,
    val timingPoints: List<OsuRawTimingPoint>,
    val hitObjects: List<OsuRawHitObject>,
    val comboColours: List<Int>,
    val sliderTrackOverride: Int?,
    val sliderBorder: Int?,
    val formatVersion: Int = 14
)

object OsuBeatmapParser {
    private const val EARLY_VERSION_TIMING_OFFSET_MS = 24.0
    private const val FIRST_LAZER_FORMAT_VERSION = 128
    private const val ZERO_LENGTH_SLIDER_EPSILON = 0.001f
    private val formatVersionRegex = Regex("""osu file format v(\d+)""", RegexOption.IGNORE_CASE)

    /** Lightweight scan that stops before the hit-object payload. */
    fun parseHeader(file: File): OsuMapHeader = OsuFileHeaderReader.parse(file)

    fun parseHeader(text: String): OsuMapHeader = OsuFileHeaderReader.parse(text)

    /**
     * Checks whether an `.osu` file contains at least one structurally valid hit
     * object, regardless of ruleset. This deliberately does not build render
     * objects: osu!mania hold notes are valid stored maps but are unsupported by
     * the osu!standard renderer.
     */
    fun hasHitObjects(file: File): Boolean {
        try {
            file.bufferedReader(Charsets.UTF_8).use { reader ->
                var inHitObjects = false
                while (true) {
                    val line = reader.readLine()?.trim() ?: break
                    if (line.isEmpty() || line.startsWith("//")) continue
                    if (line.startsWith("[") && line.endsWith("]")) {
                        inHitObjects = line.substring(1, line.length - 1) == "HitObjects"
                        continue
                    }
                    if (inHitObjects && looksLikeHitObject(line)) return true
                }
            }
        } catch (e: Exception) {
            throw OsuMapParseException("Unable to read ${file.name}", e)
        }
        return false
    }

    fun parse(file: File): ParsedOsuMap {
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            throw OsuMapParseException("Unable to read ${file.name}", e)
        }
        return parse(text)
    }

    fun parse(text: String): ParsedOsuMap {
        val formatVersion = parseFormatVersion(text)
        val timingOffsetMs = if (formatVersion < 5) EARLY_VERSION_TIMING_OFFSET_MS else 0.0
        val header = OsuFileHeaderReader.parse(text)
        var section = ""
        val timingPoints = mutableListOf<OsuRawTimingPoint>()
        val hitObjects = mutableListOf<OsuRawHitObject>()
        val comboColours = mutableListOf<Int>()
        var sliderTrackOverride: Int? = null
        var sliderBorder: Int? = null

        text.removePrefix("\uFEFF").lineSequence().forEachIndexed { lineNumber, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("//")) return@forEachIndexed
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length - 1)
                return@forEachIndexed
            }

            when (section) {
                "Events" -> Unit
                "TimingPoints" -> {
                    parseTimingPoint(
                        line = line,
                        lineNumber = lineNumber + 1,
                        defaultSampleBank = header.defaultSampleBank,
                        defaultSampleVolume = header.defaultSampleVolume,
                        timeOffsetMs = timingOffsetMs
                    )?.let(timingPoints::add)
                }
                "Colours" -> {
                    val pair = parseKeyValue(line) ?: return@forEachIndexed
                    when {
                        pair.first.startsWith("Combo", ignoreCase = true) -> {
                            parseColour(pair.second, lineNumber + 1)?.let(comboColours::add)
                        }
                        pair.first.equals("SliderTrackOverride", ignoreCase = true) -> {
                            sliderTrackOverride = parseColour(pair.second, lineNumber + 1)
                        }
                        pair.first.equals("SliderBorder", ignoreCase = true) -> {
                            sliderBorder = parseColour(pair.second, lineNumber + 1)
                        }
                    }
                }
                "HitObjects" -> {
                    hitObjects += parseHitObject(
                        line = line,
                        lineNumber = lineNumber + 1,
                        formatVersion = formatVersion,
                        timeOffsetMs = timingOffsetMs
                    )
                }
            }
        }

        return ParsedOsuMap(
            header = header,
            timingPoints = timingPoints.sortedBy { it.timeMs },
            hitObjects = hitObjects.sortedBy { it.timeMs },
            comboColours = comboColours,
            sliderTrackOverride = sliderTrackOverride,
            sliderBorder = sliderBorder,
            formatVersion = formatVersion
        )
    }

    /**
     * Validates the fields that make a map safe to associate with a downloaded
     * standard beatmap. Unsupported rulesets and explicitly mismatched set IDs
     * are rejected; legacy maps without an embedded set ID are accepted.
     */
    fun parseAndValidate(file: File, expectedBeatmapSetId: Long): ParsedOsuMap? {
        return try {
            val parsed = parse(file)
            parsed.takeIf {
                it.header.mode == 0 &&
                    belongsToBeatmapSet(it.header.beatmapSetId, expectedBeatmapSetId)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Old `.osu` formats predate BeatmapSetID and therefore omit it. The
     * containing `beatmaps/{setId}` directory supplies that identity, while an
     * explicitly different embedded id remains invalid.
     */
    fun belongsToBeatmapSet(
        embeddedBeatmapSetId: Long?,
        expectedBeatmapSetId: Long
    ): Boolean = OsuFileHeaderReader.belongsToBeatmapSet(
        embeddedBeatmapSetId,
        expectedBeatmapSetId
    )

    fun normalizeAudioFilename(value: String): String =
        OsuFileHeaderReader.normalizeAudioFilename(value)

    private fun parseKeyValue(line: String): Pair<String, String>? {
        val separator = line.indexOf(':')
        if (separator <= 0) return null
        return line.substring(0, separator).trim() to line.substring(separator + 1).trim()
    }

    private fun looksLikeHitObject(line: String): Boolean {
        val parts = line.split(',')
        if (parts.size < 5) return false
        return parts[0].trim().toFloatOrNull() != null &&
            parts[1].trim().toFloatOrNull() != null &&
            parts[2].trim().toDoubleOrNull() != null &&
            parts[3].trim().toIntOrNull() != null
    }

    private fun parseTimingPoint(
        line: String,
        lineNumber: Int,
        defaultSampleBank: RenderSampleBank,
        defaultSampleVolume: Int,
        timeOffsetMs: Double
    ): OsuRawTimingPoint? {
        val parts = line.split(',')
        if (parts.size < 2) return null
        val time = parts[0].trim().toDoubleOrNull()?.plus(timeOffsetMs)
            ?: throw OsuMapParseException("Invalid timing point time on line $lineNumber")
        val beatLength = parts[1].trim().toDoubleOrNull()
            ?: throw OsuMapParseException("Invalid timing point beat length on line $lineNumber")
        if (beatLength == 0.0 || beatLength.isInfinite()) return null
        val meter = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 4
        val uninherited = parts.getOrNull(6)?.trim()?.let { it.startsWith("1") } ?: true
        if (uninherited && beatLength.isNaN()) {
            throw OsuMapParseException(
                "Beat length cannot be NaN in a timing control point on line $lineNumber"
            )
        }
        val sampleBank = if (parts.size >= 4) {
            legacySampleBank(parts[3].trim().toIntOrNull()) ?: RenderSampleBank.NORMAL
        } else {
            defaultSampleBank
        }
        val customSampleIndex = parts.getOrNull(4)?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val sampleVolume = (parts.getOrNull(5)?.trim()?.toIntOrNull() ?: defaultSampleVolume)
            .coerceIn(0, 100)
        return OsuRawTimingPoint(
            timeMs = time,
            beatLengthMs = beatLength,
            meter = meter,
            uninherited = uninherited,
            sampleBank = sampleBank,
            customSampleIndex = customSampleIndex,
            sampleVolume = sampleVolume
        )
    }

    private fun parseHitObject(
        line: String,
        lineNumber: Int,
        formatVersion: Int,
        timeOffsetMs: Double
    ): OsuRawHitObject {
        val parts = line.split(',')
        if (parts.size < 5) throw OsuMapParseException("Malformed hit object on line $lineNumber")
        val x = parts[0].trim().toFloatOrNull()
            ?: throw OsuMapParseException("Invalid hit object x on line $lineNumber")
        val y = parts[1].trim().toFloatOrNull()
            ?: throw OsuMapParseException("Invalid hit object y on line $lineNumber")
        val time = parts[2].trim().toDoubleOrNull()?.plus(timeOffsetMs)
            ?: throw OsuMapParseException("Invalid hit object time on line $lineNumber")
        val type = parts[3].trim().toIntOrNull()
            ?: throw OsuMapParseException("Invalid hit object type on line $lineNumber")
        // Combo/colour flags live above the low nibble. Hold notes (128) are
        // an osu!mania object and must not be interpreted as spinners.
        val baseType = type and (1 or 2 or 8)
        if (baseType != 1 && baseType != 2 && baseType != 8) {
            throw OsuMapParseException("Unsupported hit object type $type on line $lineNumber")
        }
        val newCombo = type and 4 != 0
        val comboOffset = (type shr 4) and 7
        val position = RenderPoint(x, y)
        val hitSound = parts[4].trim().toIntOrNull() ?: 0

        return when (baseType) {
            1 -> OsuRawCircle(
                timeMs = time,
                position = position,
                newCombo = newCombo,
                comboOffset = comboOffset,
                hitSample = OsuRawHitSample(
                    hitSound = hitSound,
                    sample = parseSample(parts.getOrNull(5))
                )
            )
            2 -> {
                if (parts.size < 8) {
                    throw OsuMapParseException("Malformed slider on line $lineNumber")
                }
                val pathControlPoints = parseSliderPath(
                    pointString = parts[5].trim(),
                    start = position,
                    lineNumber = lineNumber,
                    formatVersion = formatVersion
                )
                val spans = parts[6].trim().toIntOrNull()
                    ?: throw OsuMapParseException("Invalid slider repeat count on line $lineNumber")
                if (spans <= 0) throw OsuMapParseException("Slider spans must be positive")
                val length = parts[7].trim().toFloatOrNull()
                    ?: throw OsuMapParseException("Invalid slider length on line $lineNumber")
                if (length < 0f || !length.isFinite()) {
                    throw OsuMapParseException("Invalid slider length on line $lineNumber")
                }
                val nodeCount = spans + 1
                val commonSample = parseSample(parts.getOrNull(10), banksOnly = true)
                val edgeSounds = parseIntegerList(parts.getOrNull(8))
                val edgeSets = parseStringList(parts.getOrNull(9))
                val nodeSamples = List(nodeCount) { nodeIndex ->
                    OsuRawHitSample(
                        hitSound = edgeSounds.getOrNull(nodeIndex) ?: hitSound,
                        sample = commonSample.overlay(
                            parseSample(edgeSets.getOrNull(nodeIndex))
                        )
                    )
                }
                val effectiveSpanCount = if (
                    isZeroLengthSlider(pathControlPoints, length)
                ) {
                    1
                } else {
                    spans
                }
                val effectiveNodeSamples = if (effectiveSpanCount == 1) {
                    listOf(nodeSamples.first(), nodeSamples.last())
                } else {
                    nodeSamples
                }
                OsuRawSlider(
                    timeMs = time,
                    position = position,
                    newCombo = newCombo,
                    comboOffset = comboOffset,
                    pathControlPoints = pathControlPoints,
                    spanCount = effectiveSpanCount,
                    pathLength = length,
                    nodeSamples = effectiveNodeSamples,
                    baseHitSample = OsuRawHitSample(
                        hitSound = hitSound,
                        sample = commonSample
                    )
                )
            }
            else -> {
                if (parts.size < 6) {
                    throw OsuMapParseException("Malformed spinner on line $lineNumber")
                }
                val endTime = parts[5].trim().toDoubleOrNull()?.plus(timeOffsetMs)
                    ?: throw OsuMapParseException("Invalid spinner end time on line $lineNumber")
                if (endTime < time) throw OsuMapParseException("Spinner ends before it starts")
                OsuRawSpinner(
                    timeMs = time,
                    endTimeMs = endTime,
                    newCombo = newCombo,
                    comboOffset = comboOffset,
                    hitSample = OsuRawHitSample(
                        hitSound = hitSound,
                        sample = parseSample(parts.getOrNull(6))
                    )
                )
            }
        }
    }

    /**
     * lazer collapses sliders whose resolved path distance is zero to one span
     * and keeps only the head and original tail samples.
     */
    private fun isZeroLengthSlider(
        pathControlPoints: List<OsuPathControlPoint>,
        declaredLength: Float
    ): Boolean {
        if (pathControlPoints.isEmpty()) return true
        val firstPosition = pathControlPoints.first().position
        if (pathControlPoints.all { it.position == firstPosition }) return true
        if (declaredLength != 0f) return false
        return OsuSliderGeometry.build(pathControlPoints, declaredLength).length <=
            ZERO_LENGTH_SLIDER_EPSILON
    }

    /**
     * Parses `normalSet:additionSet:customIndex:volume:filename`. Bank zero
     * explicitly means "none", while an invalid enum value becomes Normal,
     * matching `ConvertHitObjectParser.readCustomSampleBanks()`.
     */
    private fun parseSample(value: String?, banksOnly: Boolean = false): OsuRawSample {
        if (value.isNullOrBlank()) return OsuRawSample()
        val parts = value.split(':', limit = 5)
        val normalBank = parseObjectSampleBank(parts.getOrNull(0))
        val additionBank = parseObjectSampleBank(parts.getOrNull(1))
        if (banksOnly) {
            return OsuRawSample(
                normalBank = normalBank.first,
                additionBank = additionBank.first,
                normalBankSpecified = normalBank.second,
                additionBankSpecified = additionBank.second
            )
        }
        return OsuRawSample(
            normalBank = normalBank.first,
            additionBank = additionBank.first,
            customSampleIndex = parts.getOrNull(2)?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            volume = parts.getOrNull(3)?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            filename = parts.getOrNull(4)?.trim()?.takeIf { it.isNotEmpty() },
            normalBankSpecified = normalBank.second,
            additionBankSpecified = additionBank.second
        )
    }

    private fun parseObjectSampleBank(value: String?): Pair<RenderSampleBank?, Boolean> {
        if (value == null) return null to false
        return when (value.trim().toIntOrNull()) {
            0 -> null to true
            1 -> RenderSampleBank.NORMAL to true
            2 -> RenderSampleBank.SOFT to true
            3 -> RenderSampleBank.DRUM to true
            else -> RenderSampleBank.NORMAL to true
        }
    }

    private fun legacySampleBank(value: Int?): RenderSampleBank? = when (value) {
        1 -> RenderSampleBank.NORMAL
        2 -> RenderSampleBank.SOFT
        3 -> RenderSampleBank.DRUM
        else -> null
    }

    private fun parseIntegerList(value: String?): List<Int> {
        if (value.isNullOrEmpty()) return emptyList()
        return value.split('|').map { it.trim().toIntOrNull() ?: 0 }
    }

    private fun parseStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return value.split('|')
    }

    /**
     * Ports lazer's `convertPathString()` / `convertPoints()` pipeline.
     *
     * Every token starting with a letter begins a new typed segment. The first
     * segment is prepended with the slider head. Adjacent duplicate controls
     * also create implicit legacy segment boundaries, which is important for
     * sliders that use several curve types in one path.
     */
    private fun parseSliderPath(
        pointString: String,
        start: RenderPoint,
        lineNumber: Int,
        formatVersion: Int
    ): List<OsuPathControlPoint> {
        val tokens = pointString.split('|')
        if (tokens.isEmpty() || tokens.any { it.trim().isEmpty() }) {
            throw OsuMapParseException("Malformed slider path on line $lineNumber")
        }

        val points = mutableListOf<RenderPoint>()
        val segments = mutableListOf<Pair<OsuCurveType, Int>>()
        tokens.forEach { token ->
            val value = token.trim()
            if (value.first().isLetter()) {
                val type = when (value.uppercase()) {
                    "L" -> OsuCurveType.LINEAR
                    "B" -> OsuCurveType.BEZIER
                    "P" -> OsuCurveType.PERFECT_CIRCLE
                    "C" -> OsuCurveType.CATMULL
                    else -> throw OsuMapParseException(
                        "Unsupported slider curve '$value' on line $lineNumber"
                    )
                }
                segments += type to points.size
                // lazer prepends a zero offset point to the first segment. This
                // app keeps absolute playfield coordinates, so that point is
                // the slider head.
                if (points.isEmpty()) points += start
            } else {
                if (segments.isEmpty()) {
                    throw OsuMapParseException("Slider path has no curve type on line $lineNumber")
                }
                val coordinates = value.split(':')
                if (coordinates.size < 2) {
                    throw OsuMapParseException("Malformed slider control point on line $lineNumber")
                }
                points += RenderPoint(
                    coordinates[0].trim().toFloatOrNull()
                        ?: throw OsuMapParseException("Invalid slider x on line $lineNumber"),
                    coordinates[1].trim().toFloatOrNull()
                        ?: throw OsuMapParseException("Invalid slider y on line $lineNumber")
                )
            }
        }

        if (segments.isEmpty()) {
            throw OsuMapParseException("Slider path has no curve type on line $lineNumber")
        }

        val result = mutableListOf<OsuPathControlPoint>()
        for (index in segments.indices) {
            val (type, startIndex) = segments[index]
            val endIndex = segments.getOrNull(index + 1)?.second ?: points.size
            if (startIndex >= endIndex) {
                throw OsuMapParseException("Empty slider segment on line $lineNumber")
            }
            val endPoint = points.getOrNull(endIndex)
            result += convertPoints(
                type = type,
                points = points.subList(startIndex, endIndex),
                endPoint = endPoint,
                legacy = formatVersion < FIRST_LAZER_FORMAT_VERSION
            )
        }
        return result
    }

    private fun convertPoints(
        type: OsuCurveType,
        points: List<RenderPoint>,
        endPoint: RenderPoint?,
        legacy: Boolean
    ): List<OsuPathControlPoint> {
        if (points.isEmpty()) return emptyList()

        var resolvedType = type
        if (resolvedType == OsuCurveType.PERFECT_CIRCLE) {
            val pointCount = points.size + if (endPoint != null) 1 else 0
            if (legacy) {
                if (pointCount != 3) {
                    resolvedType = OsuCurveType.BEZIER
                } else {
                    val arcEnd = endPoint ?: points[2]
                    if (isLinear(points[0], points[1], arcEnd)) {
                        // osu!stable special-cased collinear perfect curves.
                        resolvedType = OsuCurveType.LINEAR
                    }
                }
            } else if (pointCount > 3) {
                resolvedType = OsuCurveType.BEZIER
            }
        }

        val vertices = points.map { OsuPathControlPoint(it) }.toMutableList()
        vertices[0] = vertices[0].copy(curveType = resolvedType)
        val result = mutableListOf<OsuPathControlPoint>()
        var startIndex = 0
        var endIndex = 0
        while (++endIndex < vertices.size) {
            if (vertices[endIndex].position != vertices[endIndex - 1].position) continue

            // Legacy Catmull sliders cannot represent multiple explicit
            // segments, so adjacent Catmull duplicates are kept as one.
            if (resolvedType == OsuCurveType.CATMULL && legacy && endIndex > 1) continue

            // A duplicate final point cannot start another segment.
            if (endIndex == vertices.lastIndex) continue

            vertices[endIndex - 1] = vertices[endIndex - 1].copy(curveType = resolvedType)
            result += vertices.subList(startIndex, endIndex)
            // The duplicate itself is implicit in the next segment.
            startIndex = endIndex + 1
        }
        if (startIndex < endIndex) result += vertices.subList(startIndex, endIndex)
        return result
    }

    private fun isLinear(a: RenderPoint, b: RenderPoint, c: RenderPoint): Boolean {
        val cross = (b.y - a.y) * (c.x - a.x) - (b.x - a.x) * (c.y - a.y)
        return kotlin.math.abs(cross) <= 0.001f
    }

    private fun parseFormatVersion(text: String): Int {
        val firstLine = text.removePrefix("\uFEFF").lineSequence().firstOrNull().orEmpty()
        return formatVersionRegex.find(firstLine)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 14
    }

    private fun parseColour(value: String, lineNumber: Int): Int? {
        val components = value.trim().split(':').flatMap { it.split(',') }
            .mapNotNull { it.trim().toIntOrNull() }
        if (components.size < 3) return null
        if (components.any { it !in 0..255 }) {
            throw OsuMapParseException("Invalid colour on line $lineNumber")
        }
        return (0xff shl 24) or
            (components[0] shl 16) or
            (components[1] shl 8) or
            components[2]
    }
}
