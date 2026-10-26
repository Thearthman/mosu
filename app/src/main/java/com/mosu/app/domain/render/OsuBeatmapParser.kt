package com.mosu.app.domain.render

import java.io.File

data class OsuMapHeader(
    val beatmapSetId: Long?,
    val mode: Int,
    val audioFilename: String,
    val difficultyName: String,
    val approachRate: Float,
    val overallDifficulty: Float,
    val circleSize: Float,
    val sliderMultiplier: Float,
    val stackLeniency: Float
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
    override val comboOffset: Int
) : OsuRawHitObject()

data class OsuRawSlider(
    override val timeMs: Double,
    override val position: RenderPoint,
    override val newCombo: Boolean,
    override val comboOffset: Int,
    val pathControlPoints: List<OsuPathControlPoint>,
    val spanCount: Int,
    val pathLength: Float
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
        pathLength: Float
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
        pathLength = pathLength
    )
}

data class OsuRawSpinner(
    override val timeMs: Double,
    val endTimeMs: Double,
    override val newCombo: Boolean,
    override val comboOffset: Int
) : OsuRawHitObject() {
    override val position: RenderPoint
        get() = RenderPoint(OSU_PLAYFIELD_WIDTH / 2f, OSU_PLAYFIELD_HEIGHT / 2f)
}

data class OsuRawTimingPoint(
    val timeMs: Double,
    val beatLengthMs: Double,
    val meter: Int,
    val uninherited: Boolean
)

data class ParsedOsuMap(
    val header: OsuMapHeader,
    val timingPoints: List<OsuRawTimingPoint>,
    val hitObjects: List<OsuRawHitObject>,
    val comboColours: List<Int>,
    val sliderTrackOverride: Int?,
    val sliderBorder: Int?
)

class OsuMapParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

object OsuBeatmapParser {
    private const val FIRST_LAZER_FORMAT_VERSION = 128
    private val formatVersionRegex = Regex("""osu file format v(\d+)""", RegexOption.IGNORE_CASE)

    /** Lightweight scan that stops before the hit-object payload. */
    fun parseHeader(file: File): OsuMapHeader {
        val text = try {
            file.bufferedReader(Charsets.UTF_8).use { reader ->
                val builder = StringBuilder()
                var section = ""
                while (true) {
                    val rawLine = reader.readLine() ?: break
                    val line = rawLine.trim()
                    if (line.startsWith("[") && line.endsWith("]")) {
                        section = line.substring(1, line.length - 1)
                        if (section == "HitObjects") break
                    }
                    if (section != "HitObjects") {
                        builder.append(line).append('\n')
                    }
                }
                builder.toString()
            }
        } catch (e: Exception) {
            throw OsuMapParseException("Unable to read ${file.name}", e)
        }
        return parseHeaderText(text)
    }

    fun parseHeader(text: String): OsuMapHeader = parseHeaderText(text.removePrefix("\uFEFF"))

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
        var section = ""
        val general = mutableMapOf<String, String>()
        val metadata = mutableMapOf<String, String>()
        val difficulty = mutableMapOf<String, String>()
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
                "General" -> parseKeyValue(line)?.let { (key, value) -> general[key] = value }
                "Metadata" -> parseKeyValue(line)?.let { (key, value) -> metadata[key] = value }
                "Difficulty" -> parseKeyValue(line)?.let { (key, value) -> difficulty[key] = value }
                "Events" -> Unit
                "TimingPoints" -> {
                    parseTimingPoint(line, lineNumber + 1)?.let(timingPoints::add)
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
                    hitObjects += parseHitObject(line, lineNumber + 1, formatVersion)
                }
            }
        }

        val header = buildHeader(general, metadata, difficulty)

        return ParsedOsuMap(
            header = header,
            timingPoints = timingPoints.sortedBy { it.timeMs },
            hitObjects = hitObjects.sortedBy { it.timeMs },
            comboColours = comboColours,
            sliderTrackOverride = sliderTrackOverride,
            sliderBorder = sliderBorder
        )
    }

    /**
     * Validates the fields that make a map safe to associate with a downloaded
     * standard beatmap. Unsupported rulesets and mismatched set IDs are rejected.
     */
    fun parseAndValidate(file: File, expectedBeatmapSetId: Long): ParsedOsuMap? {
        return try {
            val parsed = parse(file)
            parsed.takeIf {
                it.header.mode == 0 && it.header.beatmapSetId == expectedBeatmapSetId
            }
        } catch (_: Exception) {
            null
        }
    }

    fun normalizeAudioFilename(value: String): String =
        value.trim()
            .replace('\\', '/')
            .trimStart('/')
            .lowercase()

    private fun parseHeaderText(text: String): OsuMapHeader {
        var section = ""
        val general = mutableMapOf<String, String>()
        val metadata = mutableMapOf<String, String>()
        val difficulty = mutableMapOf<String, String>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("//")) return@forEach
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length - 1)
                return@forEach
            }
            when (section) {
                "General" -> parseKeyValue(line)?.let { (key, value) -> general[key] = value }
                "Metadata" -> parseKeyValue(line)?.let { (key, value) -> metadata[key] = value }
                "Difficulty" -> parseKeyValue(line)?.let { (key, value) -> difficulty[key] = value }
            }
        }
        return buildHeader(general, metadata, difficulty)
    }

    private fun buildHeader(
        general: Map<String, String>,
        metadata: Map<String, String>,
        difficulty: Map<String, String>
    ): OsuMapHeader {
        val audioFilename = general["AudioFilename"]?.trim().orEmpty()
        val difficultyName = metadata["Version"]?.trim().orEmpty()
        if (audioFilename.isBlank()) throw OsuMapParseException("AudioFilename is missing")
        if (difficultyName.isBlank()) throw OsuMapParseException("Version is missing")

        val mode = general["Mode"]?.trim()?.toIntOrNull() ?: 0
        val overallDifficulty = difficulty.floatValue("OverallDifficulty") ?: 5f
        val approachRate = difficulty.floatValue("ApproachRate") ?: overallDifficulty
        return OsuMapHeader(
            beatmapSetId = metadata["BeatmapSetID"]?.trim()?.toLongOrNull(),
            mode = mode,
            audioFilename = normalizeAudioFilename(audioFilename),
            difficultyName = difficultyName,
            approachRate = approachRate.coerceIn(0f, 10f),
            overallDifficulty = overallDifficulty.coerceIn(0f, 10f),
            circleSize = (difficulty.floatValue("CircleSize") ?: 4f).coerceIn(0f, 10f),
            sliderMultiplier = (difficulty.floatValue("SliderMultiplier") ?: 1.4f)
                .takeIf { it > 0f } ?: 1.4f,
            stackLeniency = (difficulty.floatValue("StackLeniency") ?: 0.7f).coerceIn(0f, 1f)
        )
    }

    private fun parseKeyValue(line: String): Pair<String, String>? {
        val separator = line.indexOf(':')
        if (separator <= 0) return null
        return line.substring(0, separator).trim() to line.substring(separator + 1).trim()
    }

    private fun Map<String, String>.floatValue(key: String): Float? =
        this[key]?.trim()?.toFloatOrNull()

    private fun parseTimingPoint(line: String, lineNumber: Int): OsuRawTimingPoint? {
        val parts = line.split(',')
        if (parts.size < 2) return null
        val time = parts[0].trim().toDoubleOrNull()
            ?: throw OsuMapParseException("Invalid timing point time on line $lineNumber")
        val beatLength = parts[1].trim().toDoubleOrNull()
            ?: throw OsuMapParseException("Invalid timing point beat length on line $lineNumber")
        if (beatLength == 0.0 || !beatLength.isFinite()) return null
        val meter = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 4
        val uninherited = parts.getOrNull(6)?.trim()?.let { it != "0" } ?: true
        return OsuRawTimingPoint(time, beatLength, meter, uninherited)
    }

    private fun parseHitObject(
        line: String,
        lineNumber: Int,
        formatVersion: Int
    ): OsuRawHitObject {
        val parts = line.split(',')
        if (parts.size < 5) throw OsuMapParseException("Malformed hit object on line $lineNumber")
        val x = parts[0].trim().toFloatOrNull()
            ?: throw OsuMapParseException("Invalid hit object x on line $lineNumber")
        val y = parts[1].trim().toFloatOrNull()
            ?: throw OsuMapParseException("Invalid hit object y on line $lineNumber")
        val time = parts[2].trim().toDoubleOrNull()
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

        return when (baseType) {
            1 -> OsuRawCircle(time, position, newCombo, comboOffset)
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
                OsuRawSlider(
                    timeMs = time,
                    position = position,
                    newCombo = newCombo,
                    comboOffset = comboOffset,
                    pathControlPoints = pathControlPoints,
                    spanCount = spans,
                    pathLength = length
                )
            }
            else -> {
                if (parts.size < 6) {
                    throw OsuMapParseException("Malformed spinner on line $lineNumber")
                }
                val endTime = parts[5].trim().toDoubleOrNull()
                    ?: throw OsuMapParseException("Invalid spinner end time on line $lineNumber")
                if (endTime < time) throw OsuMapParseException("Spinner ends before it starts")
                OsuRawSpinner(time, endTime, newCombo, comboOffset)
            }
        }
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
