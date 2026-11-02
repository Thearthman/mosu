package com.mosu.app.domain.render

import kotlin.math.max
import kotlin.math.min

/** One or more samples attached to a hit object at a specific map time. */
data class OsuHitsoundCue(
    val timeMs: Double,
    val samples: List<RenderSample>
)

/** A renderer-owned looping sample attached to a slider or spinner object. */
data class OsuHitsoundLoop(
    val key: String,
    val startTimeMs: Double,
    val endTimeMs: Double,
    val sample: RenderSample
)

/**
 * Pure cue generation for osu!standard. All timing here is map time; latency
 * is applied only by the prepared renderer's audio scheduler.
 */
object OsuHitsoundCompiler {
    private const val HIT_NORMAL = 1
    private const val HIT_WHISTLE = 2
    private const val HIT_FINISH = 4
    private const val HIT_CLAP = 8
    private const val SAMPLE_CONTROL_POINT_LENIENCY_MS = 5.0
    private const val DIFFICULTY_DURATION_ERROR = 0.0001
    private const val SPINNER_BONUS_GAP = 2

    fun circle(
        raw: OsuRawCircle,
        timingPoints: List<OsuTimingPoint>
    ): Pair<List<OsuHitsoundCue>, List<OsuHitsoundLoop>> {
        val samples = resolve(
            hitSample = raw.hitSample,
            timingPoints = timingPoints,
            timeMs = raw.timeMs,
            leniencyMs = SAMPLE_CONTROL_POINT_LENIENCY_MS
        )
        return listOf(OsuHitsoundCue(raw.timeMs, samples)) to emptyList()
    }

    fun slider(
        raw: OsuRawSlider,
        objectIndex: Int,
        timingPoints: List<OsuTimingPoint>,
        tickDistance: Double,
        pathLength: Float,
        spanDurationMs: Double
    ): Pair<List<OsuHitsoundCue>, List<OsuHitsoundLoop>> {
        val nodes = raw.nodeSamples.takeIf { it.size == raw.spanCount + 1 }
            ?: List(raw.spanCount + 1) { OsuRawHitSample(sample = raw.nodeSamples.firstOrNull()?.sample ?: OsuRawSample()) }
        val objectEndTimeMs = raw.timeMs + spanDurationMs * raw.spanCount
        val cues = mutableListOf<OsuHitsoundCue>()

        val headSamples = resolve(
            hitSample = nodes[0],
            timingPoints = timingPoints,
            timeMs = raw.timeMs,
            leniencyMs = SAMPLE_CONTROL_POINT_LENIENCY_MS
        )
        val bodySamples = resolve(
            hitSample = raw.baseHitSample,
            timingPoints = timingPoints,
            timeMs = raw.timeMs,
            leniencyMs = SAMPLE_CONTROL_POINT_LENIENCY_MS + 1.0
        )
        if (headSamples.isNotEmpty()) cues += OsuHitsoundCue(raw.timeMs, headSamples)

        for (repeatIndex in 1 until raw.spanCount) {
            val timeMs = raw.timeMs + repeatIndex * spanDurationMs
            val samples = resolveNodeSamples(nodes[repeatIndex], timingPoints, timeMs)
            if (samples.isNotEmpty()) cues += OsuHitsoundCue(timeMs, samples)
        }

        val tailSamples = resolveNodeSamples(
            nodes[raw.spanCount],
            timingPoints,
            objectEndTimeMs
        )
        if (tailSamples.isNotEmpty()) cues += OsuHitsoundCue(objectEndTimeMs, tailSamples)

        val tickSample = bodySamples.firstOrNull { it.sampleName == "hitnormal" }
            ?: bodySamples.firstOrNull()
        if (
            tickSample != null &&
            spanDurationMs > 0.0 &&
            pathLength > 0f &&
            tickDistance > 0.0 &&
            tickDistance.isFinite()
        ) {
            val velocity = pathLength / spanDurationMs
            addSliderTicks(
                cues = cues,
                startTimeMs = raw.timeMs,
                spanDurationMs = spanDurationMs,
                spanCount = raw.spanCount,
                pathLength = pathLength.toDouble(),
                tickDistance = tickDistance,
                minimumDistanceFromEnd = velocity * 10.0,
                sample = tickSample.copy(sampleName = "slidertick")
            )
        }

        val loops = buildList {
            val normalSample = bodySamples.firstOrNull { it.sampleName == "hitnormal" }
            val whistleSample = bodySamples.firstOrNull { it.sampleName == "hitwhistle" }
            if (normalSample != null && objectEndTimeMs > raw.timeMs) {
                add(
                    OsuHitsoundLoop(
                        key = "slider:$objectIndex:sliderslide",
                        startTimeMs = raw.timeMs,
                        endTimeMs = objectEndTimeMs,
                        sample = normalSample.copy(sampleName = "sliderslide")
                    )
                )
            }
            if (whistleSample != null && objectEndTimeMs > raw.timeMs) {
                add(
                    OsuHitsoundLoop(
                        key = "slider:$objectIndex:sliderwhistle",
                        startTimeMs = raw.timeMs,
                        endTimeMs = objectEndTimeMs,
                        sample = whistleSample.copy(sampleName = "sliderwhistle")
                    )
                )
            }
        }
        return cues.sortedBy { it.timeMs } to loops
    }

    fun spinner(
        raw: OsuRawSpinner,
        objectIndex: Int,
        timingPoints: List<OsuTimingPoint>,
        overallDifficulty: Float
    ): Pair<List<OsuHitsoundCue>, List<OsuHitsoundLoop>> {
        val samples = resolve(
            hitSample = raw.hitSample,
            timingPoints = timingPoints,
            timeMs = raw.endTimeMs,
            leniencyMs = SAMPLE_CONTROL_POINT_LENIENCY_MS
        )
        val referenceSample = samples.firstOrNull { it.sampleName == "hitnormal" }
            ?: samples.firstOrNull()
        val bonusSample = derivedAdditionSample(samples, "spinnerbonus")
        val loop = if (referenceSample != null && raw.endTimeMs > raw.timeMs) {
            listOf(
                OsuHitsoundLoop(
                    key = "spinner:$objectIndex:spinnerspin",
                    startTimeMs = raw.timeMs,
                    endTimeMs = raw.endTimeMs,
                    sample = referenceSample.copy(sampleName = "spinnerspin")
                )
            )
        } else {
            emptyList()
        }

        val durationMs = (raw.endTimeMs - raw.timeMs).coerceAtLeast(0.0)
        val seconds = durationMs / 1000.0
        val clearRpm = difficultyRange(
            overallDifficulty,
            DifficultyRange(clear = 90.0, average = 150.0, complete = 225.0)
        )
        val completeRpm = difficultyRange(
            overallDifficulty,
            DifficultyRange(clear = 250.0, average = 380.0, complete = 430.0)
        )
        val spinsRequired = (
            clearRpm / 60.0 * seconds + DIFFICULTY_DURATION_ERROR
            ).toInt()
        val maximumBonusSpins = max(
            0,
            (
                completeRpm / 60.0 * seconds + DIFFICULTY_DURATION_ERROR
                ).toInt() - spinsRequired - SPINNER_BONUS_GAP
        )
        val totalSpins = maximumBonusSpins + spinsRequired + SPINNER_BONUS_GAP
        val bonusStartIndex = spinsRequired + SPINNER_BONUS_GAP
        val bonusCues = if (bonusSample != null && totalSpins > 0) {
            (bonusStartIndex until totalSpins).map { index ->
                OsuHitsoundCue(
                    timeMs = raw.timeMs + (index + 1).toFloat() / totalSpins * durationMs,
                    samples = listOf(bonusSample)
                )
            }
        } else {
            emptyList()
        }
        // DrawableSpinner plays its base samples when the spinner is judged at
        // its end time, in addition to the loop and bonus samples.
        val endCue = samples.takeIf { it.isNotEmpty() }?.let {
            OsuHitsoundCue(raw.endTimeMs, it)
        }
        return (bonusCues + listOfNotNull(endCue)).sortedBy { it.timeMs } to loop
    }

    private fun resolveNodeSamples(
        hitSample: OsuRawHitSample,
        timingPoints: List<OsuTimingPoint>,
        timeMs: Double
    ): List<RenderSample> = resolve(
        hitSample = hitSample,
        timingPoints = timingPoints,
        timeMs = timeMs,
        leniencyMs = SAMPLE_CONTROL_POINT_LENIENCY_MS
    )

    /**
     * Mirrors `HitObject.CreateHitSampleInfo()`: non-normal additions inherit
     * the first addition sample, otherwise the normal sample.
     */
    private fun derivedAdditionSample(
        samples: List<RenderSample>,
        sampleName: String
    ): RenderSample? {
        val addition = samples.firstOrNull { it.sampleName != "hitnormal" }
        val normal = samples.firstOrNull { it.sampleName == "hitnormal" }
        return (addition ?: normal)?.copy(sampleName = sampleName)
    }

    private fun resolve(
        hitSample: OsuRawHitSample,
        timingPoints: List<OsuTimingPoint>,
        timeMs: Double,
        leniencyMs: Double
    ): List<RenderSample> {
        val point = timingPoints.lastOrNull {
            it.timeMs <= timeMs + leniencyMs
        } ?: timingPoints.firstOrNull()
        val fallbackBank = point?.sampleBank ?: RenderSampleBank.NORMAL
        val normalBank = hitSample.sample.normalBank ?: fallbackBank
        val additionBank = hitSample.sample.additionBank
            ?: hitSample.sample.normalBank
            ?: fallbackBank
        val customSampleIndex = hitSample.sample.customSampleIndex
            .takeIf { it > 0 }
            ?: point?.customSampleIndex
            ?: 0
        val volume = hitSample.sample.volume
            .takeIf { it > 0 }
            ?: point?.sampleVolume
            ?: 100
        val filename = hitSample.sample.filename?.takeIf { it.isNotBlank() }
        if (filename != null) {
            // A file hit sample replaces the normal/addition composition. This
            // mirrors lazer's FileHitSampleInfo, whose lookup falls back to the
            // normal bank only after the explicit file cannot be resolved.
            return listOf(
                RenderSample(
                    sampleName = "hitnormal",
                    bank = RenderSampleBank.NORMAL,
                    customSampleIndex = 1,
                    customFilename = filename,
                    volume = volume.coerceIn(0, 100)
                )
            )
        }
        val samples = mutableListOf(
            RenderSample(
                sampleName = "hitnormal",
                bank = normalBank,
                customSampleIndex = customSampleIndex,
                volume = volume.coerceIn(0, 100)
            )
        )
        if (hitSample.hitSound and HIT_FINISH != 0) {
            samples += RenderSample(
                sampleName = "hitfinish",
                bank = additionBank,
                customSampleIndex = customSampleIndex,
                volume = volume.coerceIn(0, 100)
            )
        }
        if (hitSample.hitSound and HIT_WHISTLE != 0) {
            samples += RenderSample(
                sampleName = "hitwhistle",
                bank = additionBank,
                customSampleIndex = customSampleIndex,
                volume = volume.coerceIn(0, 100)
            )
        }
        if (hitSample.hitSound and HIT_CLAP != 0) {
            samples += RenderSample(
                sampleName = "hitclap",
                bank = additionBank,
                customSampleIndex = customSampleIndex,
                volume = volume.coerceIn(0, 100)
            )
        }
        return samples
    }

    private fun addSliderTicks(
        cues: MutableList<OsuHitsoundCue>,
        startTimeMs: Double,
        spanDurationMs: Double,
        spanCount: Int,
        pathLength: Double,
        tickDistance: Double,
        minimumDistanceFromEnd: Double,
        sample: RenderSample
    ) {
        val length = min(MAX_TICK_PATH_LENGTH, pathLength)
        val spacing = tickDistance.coerceIn(0.0, length)
        if (length <= 0.0 || spacing <= 0.0) return
        for (span in 0 until spanCount) {
            val spanStartTimeMs = startTimeMs + span * spanDurationMs
            val reversed = span % 2 == 1
            var distance = spacing
            while (distance <= length) {
                if (distance >= length - minimumDistanceFromEnd) break
                val pathProgress = distance / length
                val timeProgress = if (reversed) 1.0 - pathProgress else pathProgress
                cues += OsuHitsoundCue(
                    timeMs = spanStartTimeMs + timeProgress * spanDurationMs,
                    samples = listOf(sample)
                )
                distance += spacing
            }
        }
    }

    private fun difficultyRange(
        value: Float,
        range: DifficultyRange
    ): Double {
        val od = value.coerceIn(0f, 10f).toDouble()
        return when {
            od < 5.0 -> range.clear + (range.average - range.clear) * od / 5.0
            od > 5.0 -> range.average + (range.complete - range.average) * (od - 5.0) / 5.0
            else -> range.average
        }
    }

    private data class DifficultyRange(
        val clear: Double,
        val average: Double,
        val complete: Double
    )

    private const val MAX_TICK_PATH_LENGTH = 100_000.0
}
