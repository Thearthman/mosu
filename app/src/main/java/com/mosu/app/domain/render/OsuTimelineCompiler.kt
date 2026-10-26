package com.mosu.app.domain.render

import kotlin.math.abs

object OsuTimelineCompiler {
    private const val DEFAULT_BEAT_LENGTH_MS = 500.0
    private const val MAX_STACK_DISTANCE = 3f

    fun compile(parsed: ParsedOsuMap, expectedBeatmapSetId: Long): OsuRenderTimeline {
        val header = parsed.header
        require(header.mode == 0) { "Only osu!standard maps can be rendered" }
        require(header.beatmapSetId == expectedBeatmapSetId) {
            "BeatmapSetID ${header.beatmapSetId} does not match $expectedBeatmapSetId"
        }

        val preempt = approachRateToPreemptMs(header.approachRate)
        val fadeIn = preemptToFadeInMs(preempt)
        val objectScale = circleScaleFromCircleSize(header.circleSize)
        val timing = parsed.timingPoints.map {
            OsuTimingPoint(
                timeMs = it.timeMs,
                beatLengthMs = it.beatLengthMs,
                meter = it.meter,
                uninherited = it.uninherited,
                bpmMultiplier = inheritedBpmMultiplier(it)
            )
        }
        val sliderPaths = HashMap<Int, OsuSliderPath>()
        parsed.hitObjects.forEachIndexed { index, raw ->
            if (raw is OsuRawSlider) {
                sliderPaths[index] = OsuSliderGeometry.build(
                    pathControlPoints = raw.pathControlPoints,
                    declaredLength = raw.pathLength
                )
            }
        }
        val stackHeights = calculateStackHeights(
            parsed.hitObjects,
            sliderPaths,
            preempt,
            header.stackLeniency
        )

        var comboNumber = 1
        var comboColourIndex = 0
        val objects = parsed.hitObjects.mapIndexed { index, raw ->
            val comboOffset = raw.comboOffset.coerceIn(0, 7)
            if (index > 0) {
                val previous = parsed.hitObjects[index - 1]
                // Spinners never start a combo, but the first object after one does.
                val startsCombo = raw !is OsuRawSpinner && (raw.newCombo || previous is OsuRawSpinner)
                if (startsCombo) {
                    comboNumber = 1
                    comboColourIndex += comboOffset + 1
                } else {
                    comboNumber++
                }
            }
            val stackHeight = stackHeights[index]

            when (raw) {
                is OsuRawCircle -> OsuCircleObject(
                    startTimeMs = raw.timeMs,
                    position = raw.position,
                    stackHeight = stackHeight,
                    comboNumber = comboNumber,
                    comboColourIndex = comboColourIndex,
                    objectScale = objectScale,
                    preemptMs = preempt,
                    fadeInMs = fadeIn
                )
                is OsuRawSlider -> {
                    val path = sliderPaths.getValue(index)
                    val timingState = timingStateAt(timing, raw.timeMs)
                    val spanDuration = sliderDurationMs(
                        sliderMultiplier = header.sliderMultiplier,
                        beatLengthMs = timingState.first,
                        bpmMultiplier = timingState.second,
                        pathLength = path.length,
                        spanCount = 1
                    )
                    OsuSliderObject(
                        startTimeMs = raw.timeMs,
                        endTimeMs = raw.timeMs + spanDuration * raw.spanCount,
                        position = raw.position,
                        stackHeight = stackHeight,
                        comboNumber = comboNumber,
                        comboColourIndex = comboColourIndex,
                        objectScale = objectScale,
                        preemptMs = preempt,
                        fadeInMs = fadeIn,
                        path = path,
                        spanCount = raw.spanCount,
                        spanDurationMs = spanDuration
                    )
                }
                is OsuRawSpinner -> OsuSpinnerObject(
                    startTimeMs = raw.timeMs,
                    endTimeMs = raw.endTimeMs,
                    stackHeight = 0,
                    comboNumber = comboNumber,
                    comboColourIndex = comboColourIndex,
                    objectScale = objectScale,
                    preemptMs = preempt,
                    fadeInMs = fadeIn
                )
            }
        }

        return OsuRenderTimeline(
            beatmapSetId = expectedBeatmapSetId,
            difficultyName = header.difficultyName,
            audioFilename = header.audioFilename,
            approachRate = header.approachRate,
            overallDifficulty = header.overallDifficulty,
            circleSize = header.circleSize,
            preemptMs = preempt,
            fadeInMs = fadeIn,
            sliderMultiplier = header.sliderMultiplier,
            objects = objects,
            timingPoints = timing,
            comboColours = parsed.comboColours,
            sliderTrackOverride = parsed.sliderTrackOverride,
            sliderBorder = parsed.sliderBorder
        )
    }

    /**
     * The .osu inherited beat length is -100 / speedMultiplier. v1.2's velocity
     * formula multiplies the base beat length by this reciprocal multiplier.
     */
    fun inheritedBpmMultiplier(point: OsuRawTimingPoint): Double {
        if (point.uninherited) return 1.0
        val beatLength = point.beatLengthMs
        if (beatLength >= 0.0) return 1.0
        return abs(beatLength) / 100.0
    }

    internal fun timingStateAt(
        timingPoints: List<OsuTimingPoint>,
        timeMs: Double
    ): Pair<Double, Double> {
        var baseBeatLength = DEFAULT_BEAT_LENGTH_MS
        var bpmMultiplier = 1.0
        var foundBase = false
        var foundInherited = false
        for (point in timingPoints) {
            if (point.timeMs > timeMs) break
            if (point.uninherited) {
                if (point.beatLengthMs > 0.0 && point.beatLengthMs.isFinite()) {
                    baseBeatLength = point.beatLengthMs
                    foundBase = true
                }
                // A new uninherited section resets any inherited SV multiplier.
                // Inherited points only affect the section in which they occur.
                bpmMultiplier = 1.0
                foundInherited = false
            } else if (!point.uninherited) {
                bpmMultiplier = point.bpmMultiplier.takeIf { it > 0.0 && it.isFinite() } ?: 1.0
                foundInherited = true
            }
        }
        if (!foundBase) {
            timingPoints.firstOrNull { it.uninherited && it.beatLengthMs > 0.0 }?.let {
                baseBeatLength = it.beatLengthMs
            }
        }
        if (!foundInherited) bpmMultiplier = 1.0
        return baseBeatLength to bpmMultiplier
    }

    private fun calculateStackHeights(
        objects: List<OsuRawHitObject>,
        sliderPaths: Map<Int, OsuSliderPath>,
        preemptMs: Double,
        stackLeniency: Float
    ): IntArray {
        val heights = IntArray(objects.size)
        val threshold = preemptMs * stackLeniency
        for (index in objects.indices) {
            if (objects[index] is OsuRawSpinner) continue
            var previous = index - 1
            while (previous >= 0) {
                val previousObject = objects[previous]
                if (objects[index].timeMs - previousObject.timeMs > threshold) break
                if (previousObject is OsuRawSpinner) {
                    previous--
                    continue
                }
                val previousEnd = when (previousObject) {
                    is OsuRawSlider -> {
                        val path = sliderPaths.getValue(previous)
                        val finalFraction = if (previousObject.spanCount % 2 == 0) 0f else 1f
                        previousObject.position + (
                            path.positionAtFraction(finalFraction) - previousObject.position
                            )
                    }
                    else -> previousObject.position
                }
                if (objects[index].position.distanceTo(previousEnd) <= MAX_STACK_DISTANCE) {
                    heights[index] = heights[previous] + 1
                    break
                }
                previous--
            }
        }
        return heights
    }
}
