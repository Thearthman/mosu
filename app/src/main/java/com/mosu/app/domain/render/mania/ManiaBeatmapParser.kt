package com.mosu.app.domain.render.mania

import com.mosu.app.domain.render.OsuFileHeaderReader
import java.io.File
import kotlin.math.floor
import kotlin.math.max

data class ManiaRawHitObject(
    val startTimeMs: Double,
    val endTimeMs: Double,
    val column: Int,
    val isHold: Boolean
)

data class ManiaRawTimingPoint(
    val timeMs: Double,
    val beatLengthMs: Double,
    val meter: Int,
    val uninherited: Boolean,
    val omitFirstBarLine: Boolean
)

data class ParsedManiaMap(
    val beatmapSetId: Long?,
    val beatmapId: Long?,
    val difficultyName: String,
    val audioFilename: String,
    val keyCount: Int,
    val formatVersion: Int,
    val timingPoints: List<ManiaRawTimingPoint>,
    val hitObjects: List<ManiaRawHitObject>
)

class ManiaMapParseException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Parser for the subset of legacy `.osu` data needed by the mania preview.
 *
 * It intentionally does not share osu!standard's object parser: mania holds
 * use type 128, while standard interprets that value as a malformed object.
 * The parser is Android-free so its validation and timing semantics can be
 * exercised by plain JVM tests.
 */
object ManiaBeatmapParser {
    private const val MANIA_MODE = 3
    private const val EARLY_VERSION_TIMING_OFFSET_MS = 24.0
    private const val PLAYFIELD_WIDTH = 512f
    private const val MAX_KEYS = 10
    private const val OBJECT_CATEGORY_MASK = 1 or 2 or 8 or 128
    private const val OMIT_FIRST_BAR_LINE_FLAG = 8
    private val formatVersionRegex = Regex("""osu file format v(\d+)""", RegexOption.IGNORE_CASE)

    private data class ParsedHitObject(
        val x: Float,
        val startTimeMs: Double,
        val endTimeMs: Double,
        val isHold: Boolean
    )

    fun parse(file: File): ParsedManiaMap {
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            throw ManiaMapParseException("Unable to read ${file.name}", e)
        }
        return parse(text)
    }

    fun parse(text: String): ParsedManiaMap {
        val cleanText = text.removePrefix("\uFEFF")
        val formatVersion = parseFormatVersion(cleanText)
        val timingOffsetMs = if (formatVersion < 5) EARLY_VERSION_TIMING_OFFSET_MS else 0.0
        var section = ""
        val general = mutableMapOf<String, String>()
        val metadata = mutableMapOf<String, String>()
        val difficulty = mutableMapOf<String, String>()
        val timingPoints = mutableListOf<ManiaRawTimingPoint>()
        val parsedHitObjects = mutableListOf<ParsedHitObject>()
        var rawCircleSize: Float? = null

        cleanText.lineSequence().forEachIndexed { lineIndex, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("//")) return@forEachIndexed
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length - 1)
                return@forEachIndexed
            }

            when (section) {
                "General" -> parseKeyValue(line)?.let { (key, value) ->
                    general[key.lowercase()] = value
                }
                "Metadata" -> parseKeyValue(line)?.let { (key, value) ->
                    metadata[key.lowercase()] = value
                }
                "Difficulty" -> parseKeyValue(line)?.let { (key, value) ->
                    difficulty[key.lowercase()] = value
                    if (key.equals("CircleSize", ignoreCase = true)) {
                        rawCircleSize = value.toFloatOrNull()
                            ?.takeIf { it.isFinite() }
                            ?: throw ManiaMapParseException(
                                "Invalid CircleSize on line ${lineIndex + 1}"
                            )
                    }
                }
                "TimingPoints" -> timingPoints += parseTimingPoint(
                    line = line,
                    lineNumber = lineIndex + 1,
                    timeOffsetMs = timingOffsetMs
                )
                "HitObjects" -> parsedHitObjects += parseHitObject(
                    line = line,
                    lineNumber = lineIndex + 1,
                    timeOffsetMs = timingOffsetMs
                )
            }
        }

        val mode = general["mode"]?.trim()?.toIntOrNull()
            ?: throw ManiaMapParseException("Mode is missing")
        if (mode != MANIA_MODE) {
            throw ManiaMapParseException("Mode $mode is not osu!mania")
        }

        val audioFilename = general["audiofilename"]?.trim().orEmpty()
        if (audioFilename.isBlank()) {
            throw ManiaMapParseException("AudioFilename is missing")
        }
        val difficultyName = metadata["version"]?.trim().orEmpty()
        if (difficultyName.isBlank()) {
            throw ManiaMapParseException("Version is missing")
        }
        val keys = keyCount(rawCircleSize ?: 4f)
        if (parsedHitObjects.isEmpty()) {
            throw ManiaMapParseException("Map has no hit objects")
        }
        val hitObjects = parsedHitObjects.map { parsed ->
            ManiaRawHitObject(
                startTimeMs = parsed.startTimeMs,
                endTimeMs = parsed.endTimeMs,
                column = columnForX(parsed.x, keys),
                isHold = parsed.isHold
            )
        }

        return ParsedManiaMap(
            beatmapSetId = metadata["beatmapsetid"]?.trim()?.toLongOrNull()?.takeIf { it > 0L },
            beatmapId = metadata["beatmapid"]?.trim()?.toLongOrNull()?.takeIf { it > 0L },
            difficultyName = difficultyName,
            audioFilename = OsuFileHeaderReader.normalizeAudioFilename(audioFilename),
            keyCount = keys,
            formatVersion = formatVersion,
            timingPoints = timingPoints.sortedBy { it.timeMs },
            hitObjects = hitObjects.sortedBy { it.startTimeMs }
        )
    }

    /**
     * Parses and applies the identity checks required before a map can be
     * associated with a downloaded difficulty. Legacy maps without an embedded
     * set id are accepted.
     */
    fun parseAndValidate(
        file: File,
        expectedBeatmapSetId: Long,
        expectedDifficultyName: String,
        expectedAudioFilename: String
    ): ParsedManiaMap? = runCatching {
        parse(file).takeIf { parsed ->
            belongsToBeatmapSet(parsed.beatmapSetId, expectedBeatmapSetId) &&
                parsed.difficultyName == expectedDifficultyName &&
                parsed.audioFilename ==
                OsuFileHeaderReader.normalizeAudioFilename(expectedAudioFilename)
        }
    }.getOrNull()

    fun belongsToBeatmapSet(embeddedBeatmapSetId: Long?, expectedBeatmapSetId: Long): Boolean =
        embeddedBeatmapSetId == null || embeddedBeatmapSetId == expectedBeatmapSetId

    private fun parseFormatVersion(text: String): Int {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        return formatVersionRegex.find(firstLine)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: 14
    }

    private fun keyCount(rawCircleSize: Float): Int {
        if (!rawCircleSize.isFinite()) {
            throw ManiaMapParseException("Invalid CircleSize")
        }
        val keys = Math.rint(rawCircleSize.toDouble()).toInt()
        if (keys !in 1..MAX_KEYS) {
            throw ManiaMapParseException("Unsupported mania key count $keys")
        }
        return keys
    }

    private fun columnForX(x: Float, keyCount: Int): Int {
        val columnWidth = PLAYFIELD_WIDTH / keyCount
        return floor(x / columnWidth).toInt().coerceIn(0, keyCount - 1)
    }

    private fun parseTimingPoint(
        line: String,
        lineNumber: Int,
        timeOffsetMs: Double
    ): ManiaRawTimingPoint {
        val parts = line.split(',')
        if (parts.size < 2) {
            throw ManiaMapParseException("Malformed timing point on line $lineNumber")
        }
        val time = parts[0].trim().toDoubleOrNull()
            ?.takeIf { it.isFinite() }
            ?.plus(timeOffsetMs)
            ?: throw ManiaMapParseException("Invalid timing point time on line $lineNumber")
        val beatLength = parts[1].trim().toDoubleOrNull()
            ?.takeIf { it.isFinite() && it != 0.0 }
            ?: throw ManiaMapParseException("Invalid beat length on line $lineNumber")
        val meter = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 4
        val uninherited = parts.getOrNull(6)?.trim()?.let { it == "1" } ?: true
        if (uninherited && (beatLength < 0.0 || meter <= 0)) {
            throw ManiaMapParseException("Invalid uninherited timing point on line $lineNumber")
        }
        val effectFlags = parts.getOrNull(7)?.trim()?.toIntOrNull() ?: 0
        return ManiaRawTimingPoint(
            timeMs = time,
            beatLengthMs = beatLength,
            meter = if (meter > 0) meter else 4,
            uninherited = uninherited,
            omitFirstBarLine = effectFlags and OMIT_FIRST_BAR_LINE_FLAG != 0
        )
    }

    private fun parseHitObject(
        line: String,
        lineNumber: Int,
        timeOffsetMs: Double
    ): ParsedHitObject {
        val parts = line.split(',')
        if (parts.size < 5) {
            throw ManiaMapParseException("Malformed hit object on line $lineNumber")
        }
        val x = parts[0].trim().toFloatOrNull()
            ?.takeIf { it.isFinite() }
            ?: throw ManiaMapParseException("Invalid hit object x on line $lineNumber")
        parts[1].trim().toFloatOrNull()
            ?.takeIf { it.isFinite() }
            ?: throw ManiaMapParseException("Invalid hit object y on line $lineNumber")
        val startTime = parts[2].trim().toDoubleOrNull()
            ?.takeIf { it.isFinite() }
            ?.plus(timeOffsetMs)
            ?: throw ManiaMapParseException("Invalid hit object time on line $lineNumber")
        val type = parts[3].trim().toIntOrNull()
            ?: throw ManiaMapParseException("Invalid hit object type on line $lineNumber")
        val category = type and OBJECT_CATEGORY_MASK
        if (category != 1 && category != 128) {
            throw ManiaMapParseException("Unsupported hit object type $type on line $lineNumber")
        }

        val isHold = category == 128
        val rawEndTime = if (isHold) {
            val endField = parts.getOrNull(5)?.substringBefore(':')?.trim()
            endField?.toDoubleOrNull()
                ?.takeIf { it.isFinite() }
                ?.plus(timeOffsetMs)
                ?: throw ManiaMapParseException(
                    "Invalid hold end time on line $lineNumber"
                )
        } else {
            startTime
        }

        return ParsedHitObject(
            x = x,
            startTimeMs = startTime,
            endTimeMs = max(startTime, rawEndTime),
            isHold = isHold
        )
    }

    private fun parseKeyValue(line: String): Pair<String, String>? {
        val separator = line.indexOf(':')
        if (separator <= 0) return null
        return line.substring(0, separator).trim() to line.substring(separator + 1).trim()
    }
}
