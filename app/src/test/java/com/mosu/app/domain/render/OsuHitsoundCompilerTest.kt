package com.mosu.app.domain.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OsuHitsoundCompilerTest {
    @Test
    fun circleInheritsTimingPointBankIndexAndVolumeAndIncludesEveryHitType() {
        val timingPoints = listOf(
            timingPoint(
                timeMs = 0.0,
                bank = RenderSampleBank.SOFT,
                customSampleIndex = 3,
                volume = 42
            )
        )
        val raw = OsuRawCircle(
            timeMs = 1_000.0,
            position = RenderPoint(256f, 192f),
            newCombo = false,
            comboOffset = 0,
            hitSample = OsuRawHitSample(hitSound = 1 or 2 or 4 or 8)
        )

        val (cues, loops) = OsuHitsoundCompiler.circle(raw, timingPoints)

        assertTrue(loops.isEmpty())
        assertEquals(1, cues.size)
        assertEquals(1_000.0, cues.single().timeMs, 0.0)
        assertEquals(
            listOf("hitnormal", "hitfinish", "hitwhistle", "hitclap"),
            cues.single().samples.map { it.sampleName }
        )
        cues.single().samples.forEach { sample ->
            assertEquals(RenderSampleBank.SOFT, sample.bank)
            assertEquals(3, sample.customSampleIndex)
            assertEquals(42, sample.volume)
        }
    }

    @Test
    fun circleUsesNormalDefaultsWhenTheMapHasNoTimingPoint() {
        val raw = OsuRawCircle(
            timeMs = 1_000.0,
            position = RenderPoint(256f, 192f),
            newCombo = false,
            comboOffset = 0
        )

        val (cues, _) = OsuHitsoundCompiler.circle(raw, emptyList())

        val normal = cues.single().samples.single()
        assertEquals("hitnormal", normal.sampleName)
        assertEquals(RenderSampleBank.NORMAL, normal.bank)
        assertEquals(0, normal.customSampleIndex)
        assertEquals(100, normal.volume)
    }

    @Test
    fun customFilenameReplacesMissingCustomBankAndAdditions() {
        val timingPoints = listOf(
            timingPoint(
                timeMs = 0.0,
                bank = RenderSampleBank.SOFT,
                customSampleIndex = 3,
                volume = 42
            )
        )
        val raw = OsuRawCircle(
            timeMs = 1_000.0,
            position = RenderPoint(256f, 192f),
            newCombo = false,
            comboOffset = 0,
            hitSample = OsuRawHitSample(
                hitSound = 4 or 8,
                sample = OsuRawSample(
                    normalBank = RenderSampleBank.DRUM,
                    additionBank = RenderSampleBank.SOFT,
                    customSampleIndex = 5,
                    volume = 72,
                    filename = "Custom/Kick.WAV"
                )
            )
        )

        val (cues, _) = OsuHitsoundCompiler.circle(raw, timingPoints)

        val sample = cues.single().samples.single()
        assertEquals("hitnormal", sample.sampleName)
        assertEquals(RenderSampleBank.NORMAL, sample.bank)
        assertEquals(1, sample.customSampleIndex)
        assertEquals("Custom/Kick.WAV", sample.customFilename)
        assertEquals(72, sample.volume)
    }

    @Test
    fun sliderEmitsHeadRepeatsTailTicksAndBothBodyLoops() {
        val raw = OsuRawSlider(
            timeMs = 1_000.0,
            position = RenderPoint(100f, 100f),
            newCombo = false,
            comboOffset = 0,
            pathControlPoints = linearPath(),
            spanCount = 2,
            pathLength = 200f,
            nodeSamples = listOf(
                sliderNode(hitSound = 4),
                sliderNode(hitSound = 2),
                sliderNode(hitSound = 8)
            ),
            baseHitSample = OsuRawHitSample(
                hitSound = 2,
                sample = sliderSample()
            )
        )

        val (cues, loops) = OsuHitsoundCompiler.slider(
            raw = raw,
            objectIndex = 7,
            timingPoints = emptyList(),
            tickDistance = 100.0,
            pathLength = 200f,
            spanDurationMs = 500.0
        )

        assertEquals(
            listOf(1_000.0, 1_250.0, 1_500.0, 1_750.0, 2_000.0),
            cues.map { it.timeMs }
        )
        assertEquals(
            listOf(
                listOf("hitnormal", "hitfinish"),
                listOf("slidertick"),
                listOf("hitnormal", "hitwhistle"),
                listOf("slidertick"),
                listOf("hitnormal", "hitclap")
            ),
            cues.map { cue -> cue.samples.map { it.sampleName } }
        )
        cues.filter { cue -> cue.samples.singleOrNull()?.sampleName == "slidertick" }
            .forEach { cue ->
                val tick = cue.samples.single()
                assertEquals(RenderSampleBank.SOFT, tick.bank)
                assertEquals(2, tick.customSampleIndex)
                assertEquals(70, tick.volume)
            }

        assertEquals(
            listOf("slider:7:sliderslide", "slider:7:sliderwhistle"),
            loops.map { it.key }
        )
        loops.forEach { loop ->
            assertEquals(1_000.0, loop.startTimeMs, 0.0)
            assertEquals(2_000.0, loop.endTimeMs, 0.0)
        }
        assertEquals("sliderslide", loops[0].sample.sampleName)
        assertEquals(RenderSampleBank.SOFT, loops[0].sample.bank)
        assertEquals("sliderwhistle", loops[1].sample.sampleName)
        assertEquals(RenderSampleBank.DRUM, loops[1].sample.bank)
    }

    @Test
    fun sliderTickRateControlsTickSpacing() {
        val raw = OsuRawSlider(
            timeMs = 1_000.0,
            position = RenderPoint(100f, 100f),
            newCombo = false,
            comboOffset = 0,
            pathControlPoints = linearPath(),
            spanCount = 2,
            pathLength = 200f
        )

        val (cues, _) = OsuHitsoundCompiler.slider(
            raw = raw,
            objectIndex = 0,
            timingPoints = emptyList(),
            tickDistance = 50.0,
            pathLength = 200f,
            spanDurationMs = 500.0
        )

        val tickTimes = cues
            .filter { cue -> cue.samples.singleOrNull()?.sampleName == "slidertick" }
            .map { it.timeMs }
        assertEquals(
            listOf(1_125.0, 1_250.0, 1_375.0, 1_625.0, 1_750.0, 1_875.0),
            tickTimes
        )
    }

    @Test
    fun spinnerEmitsSpinLoopAndBonusCues() {
        val raw = OsuRawSpinner(
            timeMs = 1_000.0,
            endTimeMs = 3_000.0,
            newCombo = false,
            comboOffset = 0,
            hitSample = OsuRawHitSample(
                hitSound = 4,
                sample = OsuRawSample(
                    normalBank = RenderSampleBank.SOFT,
                    additionBank = RenderSampleBank.DRUM,
                    customSampleIndex = 2,
                    volume = 65
                )
            )
        )

        val (cues, loops) = OsuHitsoundCompiler.spinner(
            raw = raw,
            objectIndex = 5,
            timingPoints = emptyList(),
            overallDifficulty = 5f
        )

        val bonusCues = cues.filter { cue ->
            cue.samples.singleOrNull()?.sampleName == "spinnerbonus"
        }
        assertEquals(5, bonusCues.size)
        bonusCues.forEachIndexed { index, cue ->
            assertEquals(1_000.0 + (index + 8) / 12.0 * 2_000.0, cue.timeMs, 0.0001)
            val bonus = cue.samples.single()
            assertEquals("spinnerbonus", bonus.sampleName)
            assertEquals(RenderSampleBank.DRUM, bonus.bank)
            assertEquals(2, bonus.customSampleIndex)
            assertEquals(65, bonus.volume)
        }
        val endCue = cues.single { cue ->
            cue.samples.map { it.sampleName } == listOf("hitnormal", "hitfinish")
        }
        assertEquals(3_000.0, endCue.timeMs, 0.0)
        assertEquals(RenderSampleBank.SOFT, endCue.samples.first().bank)
        assertEquals(65, endCue.samples.first().volume)

        val loop = loops.single()
        assertEquals("spinner:5:spinnerspin", loop.key)
        assertEquals(1_000.0, loop.startTimeMs, 0.0)
        assertEquals(3_000.0, loop.endTimeMs, 0.0)
        assertEquals("spinnerspin", loop.sample.sampleName)
        assertEquals(RenderSampleBank.SOFT, loop.sample.bank)
        assertEquals(2, loop.sample.customSampleIndex)
        assertEquals(65, loop.sample.volume)
    }

    private fun timingPoint(
        timeMs: Double,
        bank: RenderSampleBank,
        customSampleIndex: Int,
        volume: Int
    ) = OsuTimingPoint(
        timeMs = timeMs,
        beatLengthMs = 500.0,
        meter = 4,
        uninherited = true,
        bpmMultiplier = 1.0,
        sampleBank = bank,
        customSampleIndex = customSampleIndex,
        sampleVolume = volume
    )

    private fun sliderNode(hitSound: Int) = OsuRawHitSample(
        hitSound = hitSound,
        sample = sliderSample()
    )

    private fun sliderSample() = OsuRawSample(
        normalBank = RenderSampleBank.SOFT,
        additionBank = RenderSampleBank.DRUM,
        customSampleIndex = 2,
        volume = 70
    )

    private fun linearPath() = listOf(
        OsuPathControlPoint(RenderPoint(100f, 100f), OsuCurveType.LINEAR),
        OsuPathControlPoint(RenderPoint(300f, 100f))
    )
}
