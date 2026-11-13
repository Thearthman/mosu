package com.mosu.app.domain.render.mania

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

data class ManiaTimelineNote(
    val startTimeMs: Double,
    val endTimeMs: Double,
    val column: Int,
    val isHold: Boolean
) {
    val durationMs: Double get() = (endTimeMs - startTimeMs).coerceAtLeast(0.0)
}

data class ManiaTimelineTimingPoint(
    val timeMs: Double,
    val beatLengthMs: Double,
    val meter: Int,
    val uninherited: Boolean,
    val omitFirstBarLine: Boolean
)

data class ManiaScrollControlPoint(
    val timeMs: Double,
    val multiplier: Double,
    val cumulativePositionMs: Double
)

data class ManiaBarLine(
    val timeMs: Double,
    val major: Boolean
)

data class ManiaRenderTimeline(
    val beatmapSetId: Long?,
    val beatmapId: Long?,
    val difficultyName: String,
    val audioFilename: String,
    val keyCount: Int,
    val notes: List<ManiaTimelineNote>,
    val timingPoints: List<ManiaTimelineTimingPoint>,
    val scrollControlPoints: List<ManiaScrollControlPoint>,
    val mostCommonBeatLengthMs: Double,
    val barlines: List<ManiaBarLine>,
    val firstObjectStartMs: Double,
    val lastObjectEndMs: Double
) {
    val durationMs: Double get() = lastObjectEndMs

    /**
     * The preview uses twice lazer's default mania scroll speed and a 11485 ms
     * reference window at scroll speed 1.
     */
    fun timeRangeForHitPosition(hitPosition: Float): Double {
        val scrollLength = (MANIA_PLAYFIELD_HEIGHT - hitPosition).coerceAtLeast(1f)
        val scale = scrollLength / DEFAULT_SCROLL_LENGTH
        return max(1.0, BASE_TIME_RANGE_MS * scale)
    }

    /**
     * Position in screen-heights relative to the first control point. Keeping
     * the time range as a parameter makes this usable with custom skin hit
     * positions without recompiling the timeline.
     */
    fun relativePositionAt(timeMs: Double, timeRangeMs: Double): Double {
        if (scrollControlPoints.isEmpty()) return timeMs / timeRangeMs
        val point = controlPointAt(timeMs)
        return (
            point.cumulativePositionMs +
                (timeMs - point.timeMs) * point.multiplier
            ) / timeRangeMs
    }

    fun verticalPositionAt(
        timeMs: Double,
        currentTimeMs: Double,
        timeRangeMs: Double,
        hitPosition: Float
    ): Float {
        val scrollLength = MANIA_PLAYFIELD_HEIGHT - hitPosition
        val relative =
            relativePositionAt(timeMs, timeRangeMs) -
                relativePositionAt(currentTimeMs, timeRangeMs)
        return MANIA_PLAYFIELD_HEIGHT - hitPosition -
            (relative * scrollLength).toFloat()
    }

    private fun controlPointAt(timeMs: Double): ManiaScrollControlPoint {
        var low = 0
        var high = scrollControlPoints.lastIndex
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (scrollControlPoints[mid].timeMs <= timeMs) {
                low = mid
            } else {
                high = mid - 1
            }
        }
        return scrollControlPoints[low]
    }

    companion object {
        const val MANIA_PLAYFIELD_WIDTH = 1024f
        const val MANIA_PLAYFIELD_HEIGHT = 1024f
        const val DEFAULT_HIT_POSITION_FROM_BOTTOM = 110f
        const val LEGACY_DEFAULT_HIT_POSITION_FROM_BOTTOM = 124.8f
        const val DEFAULT_SCROLL_LENGTH =
            MANIA_PLAYFIELD_HEIGHT - LEGACY_DEFAULT_HIT_POSITION_FROM_BOTTOM
        const val PREVIEW_SCROLL_SPEED = 16.0
        const val BASE_TIME_RANGE_MS = 11485.0 / PREVIEW_SCROLL_SPEED
    }
}

object ManiaTimelineCompiler {
    private const val DEFAULT_BEAT_LENGTH_MS = 1000.0
    private const val MIN_MEANINGFUL_DURATION_MS = 0.000_001
    private const val MAX_BAR_LINES = 250_000

    fun compile(parsed: ParsedManiaMap): ManiaRenderTimeline {
        require(parsed.hitObjects.isNotEmpty()) { "Map has no hit objects" }
        val notes = parsed.hitObjects.map { obj ->
            ManiaTimelineNote(
                startTimeMs = obj.startTimeMs,
                endTimeMs = obj.endTimeMs,
                column = obj.column,
                isHold = obj.isHold
            )
        }
        val firstObjectStart = notes.minOf { it.startTimeMs }
        val lastObjectEnd = notes.maxOf { it.endTimeMs }
        val timingPoints = parsed.timingPoints.map(::toTimelineTimingPoint)
        val uninherited = timingPoints.filter { it.uninherited }
        val mostCommonBeatLength = mostCommonBeatLength(uninherited, lastObjectEnd)
        val scrollControlPoints = buildScrollControlPoints(
            timingPoints = timingPoints,
            mostCommonBeatLengthMs = mostCommonBeatLength,
            lastObjectEndMs = lastObjectEnd
        )
        val barlines = generateBarLines(
            timingPoints = uninherited,
            firstObjectStartMs = firstObjectStart,
            lastObjectEndMs = lastObjectEnd
        )
        return ManiaRenderTimeline(
            beatmapSetId = parsed.beatmapSetId,
            beatmapId = parsed.beatmapId,
            difficultyName = parsed.difficultyName,
            audioFilename = parsed.audioFilename,
            keyCount = parsed.keyCount,
            notes = notes,
            timingPoints = timingPoints,
            scrollControlPoints = scrollControlPoints,
            mostCommonBeatLengthMs = mostCommonBeatLength,
            barlines = barlines,
            firstObjectStartMs = firstObjectStart,
            lastObjectEndMs = lastObjectEnd
        )
    }

    fun mostCommonBeatLength(
        timingPoints: List<ManiaTimelineTimingPoint>,
        lastObjectEndMs: Double
    ): Double {
        val uninherited = timingPoints
            .asSequence()
            .filter { it.uninherited && it.beatLengthMs > 0.0 }
            .sortedBy { it.timeMs }
            .toList()
        if (uninherited.isEmpty()) return DEFAULT_BEAT_LENGTH_MS

        val durations = linkedMapOf<Double, Double>()
        uninherited.forEachIndexed { index, point ->
            if (point.timeMs > lastObjectEndMs) return@forEachIndexed
            val currentTime = if (index == 0) 0.0 else point.timeMs
            val nextTime = uninherited.getOrNull(index + 1)?.timeMs ?: lastObjectEndMs
            val duration = (nextTime - currentTime).coerceAtLeast(0.0)
            val roundedBeatLength = round(point.beatLengthMs * 1000.0) / 1000.0
            durations[roundedBeatLength] = (durations[roundedBeatLength] ?: 0.0) + duration
        }
        val mostCommon = durations.maxByOrNull { it.value }?.key ?: DEFAULT_BEAT_LENGTH_MS
        val minBeatLength = uninherited.minOf { it.beatLengthMs }
        val maxBeatLength = uninherited.maxOf { it.beatLengthMs }
        return mostCommon.coerceIn(minBeatLength, maxBeatLength)
    }

    fun relativePositionAt(
        controlPoints: List<ManiaScrollControlPoint>,
        timeMs: Double,
        timeRangeMs: Double
    ): Double {
        if (controlPoints.isEmpty()) return timeMs / timeRangeMs
        var low = 0
        var high = controlPoints.lastIndex
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (controlPoints[mid].timeMs <= timeMs) {
                low = mid
            } else {
                high = mid - 1
            }
        }
        val point = controlPoints[low]
        return (
            point.cumulativePositionMs +
                (timeMs - point.timeMs) * point.multiplier
            ) / timeRangeMs
    }

    private fun toTimelineTimingPoint(point: ManiaRawTimingPoint): ManiaTimelineTimingPoint =
        ManiaTimelineTimingPoint(
            timeMs = point.timeMs,
            beatLengthMs = point.beatLengthMs,
            meter = point.meter,
            uninherited = point.uninherited,
            omitFirstBarLine = point.omitFirstBarLine
        )

    /**
     * Builds the merged timing/effect control points used by the sequential
     * scroll algorithm. In legacy maps an uninherited timing point also emits
     * an effect point with the default scroll speed of one, so it resets any
     * inherited scroll speed that was active before it.
     */
    private fun buildScrollControlPoints(
        timingPoints: List<ManiaTimelineTimingPoint>,
        mostCommonBeatLengthMs: Double,
        lastObjectEndMs: Double
    ): List<ManiaScrollControlPoint> {
        val visiblePoints = timingPoints
            .filter { it.timeMs <= lastObjectEndMs }
            .distinctByLast { it.timeMs }
        if (visiblePoints.isEmpty()) {
            return listOf(ManiaScrollControlPoint(0.0, 1.0, 0.0))
        }

        var timingBeatLength = mostCommonBeatLengthMs
        var inheritedMultiplier = 1.0
        val result = mutableListOf<ManiaScrollControlPoint>()
        var cumulativePosition = 0.0

        visiblePoints.forEachIndexed { index, point ->
            if (point.uninherited) {
                timingBeatLength = point.beatLengthMs
                inheritedMultiplier = 1.0
            } else {
                inheritedMultiplier = if (point.beatLengthMs < 0.0) {
                    100.0 / -point.beatLengthMs
                } else {
                    1.0
                }
            }
            val multiplier =
                mostCommonBeatLengthMs / timingBeatLength * inheritedMultiplier
            if (index > 0) {
                val previous = visiblePoints[index - 1]
                cumulativePosition +=
                    (point.timeMs - previous.timeMs) * result.last().multiplier
            }
            result += ManiaScrollControlPoint(
                timeMs = point.timeMs,
                multiplier = multiplier,
                cumulativePositionMs = cumulativePosition
            )
        }
        return result
    }

    private fun generateBarLines(
        timingPoints: List<ManiaTimelineTimingPoint>,
        firstObjectStartMs: Double,
        lastObjectEndMs: Double
    ): List<ManiaBarLine> {
        val uninherited = timingPoints
            .filter { it.uninherited }
            .distinctByLast { it.timeMs }
        if (uninherited.isEmpty()) return emptyList()

        val generationStartTime = min(0.0, firstObjectStartMs)
        val lastHitTime = lastObjectEndMs + 1.0
        val result = mutableListOf<ManiaBarLine>()

        uninherited.forEachIndexed { index, point ->
            if (point.beatLengthMs <= 0.0 || point.meter <= 0) return@forEachIndexed
            val meter = point.meter
            val barLength = point.beatLengthMs * meter
            if (!barLength.isFinite() || barLength <= 0.0) return@forEachIndexed

            val endTime = uninherited.getOrNull(index + 1)?.timeMs
                ?: lastHitTime + barLength
            val startTime = if (point.timeMs > generationStartTime) {
                point.timeMs
            } else {
                val barCount = ceil(
                    (generationStartTime - point.timeMs) / barLength
                ).toLong()
                point.timeMs + barCount * barLength
            } + if (point.omitFirstBarLine) barLength else 0.0

            var currentBeat = 0
            var barTime = startTime
            var generated = 0
            while (
                barTime < endTime - MIN_MEANINGFUL_DURATION_MS &&
                generated < MAX_BAR_LINES
            ) {
                val roundedTime = roundAlmostIntegral(barTime)
                if (roundedTime != barTime) barTime = roundedTime
                result += ManiaBarLine(
                    timeMs = barTime,
                    major = currentBeat % meter == 0
                )
                currentBeat++
                generated++
                barTime += barLength
            }
        }
        return result.sortedBy { it.timeMs }
    }

    private fun roundAlmostIntegral(value: Double): Double {
        val rounded = floor(abs(value) + 0.5).withSign(value)
        return if (abs(value - rounded) <= 0.001) rounded else value
    }

    private fun Double.withSign(value: Double): Double =
        if (value < 0.0) -this else this

    private fun <T> List<T>.distinctByLast(key: (T) -> Double): List<T> {
        val lastByKey = linkedMapOf<Double, T>()
        forEach { value -> lastByKey[key(value)] = value }
        return lastByKey.entries.sortedBy { it.key }.map { it.value }
    }
}
