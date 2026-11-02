package com.mosu.app.domain.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OsuHitsoundTimelineTest {
    @Test
    fun preV5MapsApplyEarlyTimingOffsetToTimingPointsAndHitObjects() {
        val parsed = OsuBeatmapParser.parse(
            mapText(
                formatVersion = 4,
                timingPoints = "1000,500,4,2,0,100,1,0",
                hitObjects = """
                    256,192,2000,1,0
                    256,192,3000,8,0,6000
                """.trimIndent()
            )
        )

        assertEquals(1_024.0, parsed.timingPoints.single().timeMs, 0.0)
        assertEquals(2_024.0, parsed.hitObjects[0].timeMs, 0.0)
        assertEquals(3_024.0, parsed.hitObjects[1].timeMs, 0.0)
        assertEquals(6_024.0, (parsed.hitObjects[1] as OsuRawSpinner).endTimeMs, 0.0)

        val timeline = OsuTimelineCompiler.compile(parsed, 42L)
        assertEquals(1_024.0, timeline.timingPoints.single().timeMs, 0.0)
        assertEquals(2_024.0, timeline.objects[0].startTimeMs, 0.0)
        assertEquals(3_024.0, timeline.objects[1].startTimeMs, 0.0)
        assertEquals(6_024.0, timeline.objects[1].endTimeMs, 0.0)
        assertEquals(
            3_000.0,
            timeline.objects[1].endTimeMs - timeline.objects[1].startTimeMs,
            0.0
        )
    }

    @Test
    fun nanTimingPointUpdatesSamplesAndSuppressesSliderTicks() {
        val parsed = OsuBeatmapParser.parse(
            mapText(
                timingPoints = """
                    0,500,4,1,0,100,1,0
                    1000,NaN,4,3,4,35,0,0
                """.trimIndent(),
                hitObjects = """
                    100,100,500,2,0,L|300:100,1,200
                    100,100,2000,2,0,L|300:100,1,200
                """.trimIndent()
            )
        )

        assertEquals(2, parsed.timingPoints.size)
        val nanPoint = parsed.timingPoints[1]
        assertTrue(nanPoint.beatLengthMs.isNaN())
        assertEquals(false, nanPoint.uninherited)
        assertEquals(RenderSampleBank.DRUM, nanPoint.sampleBank)
        assertEquals(4, nanPoint.customSampleIndex)
        assertEquals(35, nanPoint.sampleVolume)

        val sliders = OsuTimelineCompiler.compile(parsed, 42L)
            .objects
            .map { it as OsuSliderObject }
        assertTrue(
            sliders[0].hitsoundCues.any {
                it.samples.singleOrNull()?.sampleName == "slidertick"
            }
        )
        assertEquals(
            emptyList<OsuHitsoundCue>(),
            sliders[1].hitsoundCues.filter {
                it.samples.singleOrNull()?.sampleName == "slidertick"
            }
        )
        val headSample = sliders[1].hitsoundCues.first().samples.first()
        assertEquals(RenderSampleBank.DRUM, headSample.bank)
        assertEquals(4, headSample.customSampleIndex)
        assertEquals(35, headSample.volume)
    }

    @Test
    fun inheritedSliderVelocityMultiplierIsClampedToOfficialRange() {
        assertEquals(
            0.1,
            OsuTimelineCompiler.inheritedBpmMultiplier(
                OsuRawTimingPoint(
                    timeMs = 0.0,
                    beatLengthMs = -5.0,
                    meter = 4,
                    uninherited = false
                )
            ),
            0.0
        )
        assertEquals(
            10.0,
            OsuTimelineCompiler.inheritedBpmMultiplier(
                OsuRawTimingPoint(
                    timeMs = 0.0,
                    beatLengthMs = -2_000.0,
                    meter = 4,
                    uninherited = false
                )
            ),
            0.0
        )

        val parsed = OsuBeatmapParser.parse(
            mapText(
                difficulty = """
                    [Difficulty]
                    ApproachRate:9
                    OverallDifficulty:7
                    CircleSize:4
                    SliderMultiplier:1
                    StackLeniency:0.7
                    SliderTickRate:2
                """.trimIndent(),
                timingPoints = """
                    0,500,4
                    0,-2000,4,2,0,100,0,0
                """.trimIndent(),
                hitObjects = "100,100,1000,2,0,L|300:100,1,200"
            )
        )

        val slider = OsuTimelineCompiler.compile(parsed, 42L)
            .objects.single() as OsuSliderObject
        assertEquals(10_000.0, slider.spanDurationMs, 0.0001)
    }

    @Test
    fun timingPointUninheritedFlagRequiresLeadingOne() {
        val parsed = OsuBeatmapParser.parse(
            mapText(
                timingPoints = """
                    0,500,4,2,0,100,1,0
                    1000,500,4,2,0,100,10,0
                    2000,500,4,2,0,100,2,0
                """.trimIndent(),
                hitObjects = "256,192,3000,1,0"
            )
        )

        assertEquals(
            listOf(true, true, false),
            parsed.timingPoints.map { it.uninherited }
        )
    }

    @Test
    fun zeroLengthSliderDropsRepeatsAndRetainsHeadAndTailSamples() {
        val parsed = OsuBeatmapParser.parse(
            mapText(
                hitObjects = "100,100,1000,2,0,L|100:100,3,0,1|2|4|8,1|2|3|1"
            )
        )

        val rawSlider = parsed.hitObjects.single() as OsuRawSlider
        assertEquals(1, rawSlider.spanCount)
        assertEquals(2, rawSlider.nodeSamples.size)
        assertEquals(listOf(1, 8), rawSlider.nodeSamples.map { it.hitSound })

        val slider = OsuTimelineCompiler.compile(parsed, 42L)
            .objects.single() as OsuSliderObject
        assertEquals(1, slider.spanCount)
        assertEquals(1_000.0, slider.endTimeMs, 0.0)
        assertEquals(
            listOf(listOf("hitnormal"), listOf("hitnormal", "hitclap")),
            slider.hitsoundCues.map { cue -> cue.samples.map { it.sampleName } }
        )
    }

    @Test
    fun parserCarriesHeaderAndTimingPointSampleSettings() {
        val parsed = OsuBeatmapParser.parse(
            mapText(
                timingPoints = """
                    0,500,4
                    1000,500,4,3,4,35,1,0
                """.trimIndent(),
                hitObjects = "256,192,1500,1,0,0:0:0:0:"
            )
        )

        assertEquals(RenderSampleBank.SOFT, parsed.header.defaultSampleBank)
        assertEquals(60, parsed.header.defaultSampleVolume)
        assertEquals(2.0, parsed.header.sliderTickRate, 0.0)

        assertEquals(2, parsed.timingPoints.size)
        val defaulted = parsed.timingPoints[0]
        assertEquals(RenderSampleBank.SOFT, defaulted.sampleBank)
        assertEquals(0, defaulted.customSampleIndex)
        assertEquals(60, defaulted.sampleVolume)

        val explicit = parsed.timingPoints[1]
        assertEquals(RenderSampleBank.DRUM, explicit.sampleBank)
        assertEquals(4, explicit.customSampleIndex)
        assertEquals(35, explicit.sampleVolume)
    }

    @Test
    fun parserCarriesCircleSliderNodeAndSpinnerSampleMetadata() {
        val parsed = OsuBeatmapParser.parse(
            mapText(
                hitObjects = """
                    256,192,1000,1,2,3:2:4:55:custom-kick.wav
                    128,192,2000,2,1,L|256:192,2,100,1|2|8,2|3|1,3:2:4:55:custom-slider.wav
                    256,192,4000,8,0,5000,3:2:4:55:custom-spin.wav
                """.trimIndent()
            )
        )

        val circle = parsed.hitObjects[0] as OsuRawCircle
        assertEquals(2, circle.hitSample.hitSound)
        assertEquals(RenderSampleBank.DRUM, circle.hitSample.sample.normalBank)
        assertEquals(RenderSampleBank.SOFT, circle.hitSample.sample.additionBank)
        assertEquals(4, circle.hitSample.sample.customSampleIndex)
        assertEquals(55, circle.hitSample.sample.volume)
        assertEquals("custom-kick.wav", circle.hitSample.sample.filename)

        val slider = parsed.hitObjects[1] as OsuRawSlider
        assertEquals(1, slider.baseHitSample.hitSound)
        assertEquals(RenderSampleBank.DRUM, slider.baseHitSample.sample.normalBank)
        assertEquals(RenderSampleBank.SOFT, slider.baseHitSample.sample.additionBank)
        assertEquals(3, slider.nodeSamples.size)
        assertEquals(listOf(1, 2, 8), slider.nodeSamples.map { it.hitSound })
        assertEquals(
            listOf(RenderSampleBank.SOFT, RenderSampleBank.DRUM, RenderSampleBank.NORMAL),
            slider.nodeSamples.map { it.sample.normalBank }
        )
        slider.nodeSamples.forEach { node ->
            assertEquals(RenderSampleBank.SOFT, node.sample.additionBank)
        }

        val spinner = parsed.hitObjects[2] as OsuRawSpinner
        assertEquals(0, spinner.hitSample.hitSound)
        assertEquals(RenderSampleBank.DRUM, spinner.hitSample.sample.normalBank)
        assertEquals(RenderSampleBank.SOFT, spinner.hitSample.sample.additionBank)
        assertEquals(4, spinner.hitSample.sample.customSampleIndex)
        assertEquals(55, spinner.hitSample.sample.volume)
        assertEquals("custom-spin.wav", spinner.hitSample.sample.filename)
    }

    @Test
    fun timelineCompilerCopiesTimingMetadataAndAttachesCuesToEveryObject() {
        val parsed = OsuBeatmapParser.parse(
            mapText(
                difficulty = """
                    [Difficulty]
                    ApproachRate:9
                    OverallDifficulty:5
                    CircleSize:4
                    SliderMultiplier:1
                    StackLeniency:0.7
                    SliderTickRate:2
                """.trimIndent(),
                timingPoints = "0,500,4,2,1,45,1,0",
                hitObjects = """
                    256,192,1000,1,12,1:2:3:60
                    128,192,2000,2,2,L|256:192,2,200,1|2|8,2|3|1,3:2:4:55:slider.wav
                    256,192,5000,8,0,7000,3:2:4:55:spinner.wav
                """.trimIndent()
            )
        )

        val timeline = OsuTimelineCompiler.compile(parsed, expectedBeatmapSetId = 42L)

        val timingPoint = timeline.timingPoints.single()
        assertEquals(RenderSampleBank.SOFT, timingPoint.sampleBank)
        assertEquals(1, timingPoint.customSampleIndex)
        assertEquals(45, timingPoint.sampleVolume)

        val circle = timeline.objects[0] as OsuCircleObject
        assertEquals(3, circle.hitsoundCues.single().samples.size)
        val circleSamples = circle.hitsoundCues.single().samples
        assertEquals(
            listOf(
                RenderSampleBank.NORMAL,
                RenderSampleBank.SOFT,
                RenderSampleBank.SOFT
            ),
            circleSamples.map { it.bank }
        )
        circleSamples.forEach { sample ->
            assertEquals(3, sample.customSampleIndex)
            assertEquals(60, sample.volume)
        }

        val slider = timeline.objects[1] as OsuSliderObject
        val tickTimes = slider.hitsoundCues
            .filter { cue -> cue.samples.singleOrNull()?.sampleName == "slidertick" }
            .map { it.timeMs }
        assertEquals(
            listOf(2_250.0, 2_500.0, 2_750.0, 3_250.0, 3_500.0, 3_750.0),
            tickTimes
        )
        assertEquals(2, slider.hitsoundLoops.size)
        assertEquals(
            listOf("slider:1:sliderslide", "slider:1:sliderwhistle"),
            slider.hitsoundLoops.map { it.key }
        )

        val spinner = timeline.objects[2] as OsuSpinnerObject
        assertEquals(6, spinner.hitsoundCues.size)
        assertEquals(
            5,
            spinner.hitsoundCues.count { it.samples.singleOrNull()?.sampleName == "spinnerbonus" }
        )
        val spinnerEndCue = spinner.hitsoundCues
            .single { it.samples.single().sampleName == "hitnormal" }
        assertEquals(7_000.0, spinnerEndCue.timeMs, 0.0)
        assertEquals("spinner.wav", spinnerEndCue.samples.single().customFilename)
        assertEquals("spinner:2:spinnerspin", spinner.hitsoundLoops.single().key)

        assertEquals(
            timeline.objects.flatMap { it.hitsoundCues }.sortedBy { it.timeMs },
            timeline.hitsoundCues
        )
        assertEquals(
            timeline.objects.flatMap { it.hitsoundLoops },
            timeline.hitsoundLoops
        )
        assertNotNull(timeline.hitsoundCues.firstOrNull())
    }

    @Test
    fun sliderTicksAccountForInheritedSvMultiplier() {
        val parsed = OsuBeatmapParser.parse(
            mapText(
                difficulty = """
                    [Difficulty]
                    ApproachRate:9
                    OverallDifficulty:7
                    CircleSize:4
                    SliderMultiplier:1
                    StackLeniency:0.7
                    SliderTickRate:2
                """.trimIndent(),
                timingPoints = """
                    0,500,4
                    0,-200,4,2,0,100,0,0
                """.trimIndent(),
                hitObjects = "100,100,1000,2,0,L|300:100,1,200"
            )
        )

        val slider = OsuTimelineCompiler.compile(parsed, 42L)
            .objects.single() as OsuSliderObject

        assertEquals(2_000.0, slider.spanDurationMs, 0.0)
        assertEquals(
            listOf(1_250.0, 1_500.0, 1_750.0, 2_000.0, 2_250.0, 2_500.0, 2_750.0),
            slider.hitsoundCues
                .filter { it.samples.singleOrNull()?.sampleName == "slidertick" }
                .map { it.timeMs }
        )
    }

    @Test
    fun preV8SliderTicksApplyReciprocalSvMultiplierTwice() {
        val parsed = OsuBeatmapParser.parse(
            mapText(
                difficulty = """
                    [Difficulty]
                    ApproachRate:9
                    OverallDifficulty:7
                    CircleSize:4
                    SliderMultiplier:1
                    StackLeniency:0.7
                    SliderTickRate:2
                """.trimIndent(),
                formatVersion = 7,
                timingPoints = """
                    0,500,4
                    0,-200,4,2,0,100,0,0
                """.trimIndent(),
                hitObjects = "100,100,1000,2,0,L|300:100,1,200"
            )
        )

        val slider = OsuTimelineCompiler.compile(parsed, 42L)
            .objects.single() as OsuSliderObject
        val tickTimes = slider.hitsoundCues
            .filter { it.samples.singleOrNull()?.sampleName == "slidertick" }
            .map { it.timeMs }

        assertEquals(15, tickTimes.size)
        assertEquals(1_125.0, tickTimes.first(), 0.0001)
        assertEquals(2_875.0, tickTimes.last(), 0.0001)
    }

    @Test
    fun invalidObjectBankFallsBackToNormalRatherThanTimingPointBank() {
        val parsed = OsuBeatmapParser.parse(
            mapText(
                timingPoints = "0,500,4,2,0,100,1,0",
                hitObjects = "256,192,1000,1,2,9:0:0:0:"
            )
        )

        val timeline = OsuTimelineCompiler.compile(parsed, 42L)
        val samples = (timeline.objects.single() as OsuCircleObject)
            .hitsoundCues.single()
            .samples

        assertEquals(listOf("hitnormal", "hitwhistle"), samples.map { it.sampleName })
        assertTrue(samples.all { it.bank == RenderSampleBank.NORMAL })
    }

    private fun mapText(
        difficulty: String = """
            [Difficulty]
            ApproachRate:9
            OverallDifficulty:7
            CircleSize:4
            SliderMultiplier:1.4
            StackLeniency:0.7
            SliderTickRate:2
        """.trimIndent(),
        timingPoints: String = "0,500,4,2,0,100,1,0",
        hitObjects: String,
        formatVersion: Int = 14
    ) = """
        osu file format v$formatVersion
        [General]
        AudioFilename: audio.mp3
        Mode: 0
        SampleSet: Soft
        SampleVolume: 60
        [Metadata]
        Title:Test Title
        Artist:Test Artist
        Creator:Test Creator
        Version:Hard
        BeatmapID:10
        BeatmapSetID:42
        $difficulty
        [TimingPoints]
        $timingPoints
        [HitObjects]
        $hitObjects
    """.trimIndent()
}
