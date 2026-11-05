package com.mosu.app.domain.render

import org.junit.Assert.assertEquals
import org.junit.Test

class OsuHitsoundSchedulerTest {
    @Test
    fun positiveLatencyFiresTheCueEarly() {
        val scheduler = OsuHitsoundScheduler(timeline(cues = listOf(cue(1_000.0))))
        val sink = FakeRenderAudioSink()

        scheduler.update(frame(mapTimeMs = 890.0, latencyMs = 100), sink)
        assertEquals(emptyList<RenderSample>(), sink.oneShots)

        scheduler.update(frame(mapTimeMs = 900.0, latencyMs = 100), sink)
        assertEquals(listOf("hitnormal"), sink.oneShots.map { it.sampleName })
    }

    @Test
    fun dueCuesPlayOnlyOnceAcrossRepeatedUpdates() {
        val scheduler = OsuHitsoundScheduler(
            timeline(
                cues = listOf(
                    cue(500.0, sampleName = "hitnormal"),
                    cue(1_000.0, sampleName = "hitwhistle")
                )
            )
        )
        val sink = FakeRenderAudioSink()

        scheduler.update(frame(0.0), sink)
        scheduler.update(frame(500.0), sink)
        scheduler.update(frame(500.0), sink)
        scheduler.update(frame(1_000.0), sink)
        scheduler.update(frame(1_000.0), sink)

        assertEquals(listOf("hitnormal", "hitwhistle"), sink.oneShots.map { it.sampleName })
    }

    @Test
    fun firstPlayingFrameFiresCueAtItsExactTimestamp() {
        val scheduler = OsuHitsoundScheduler(timeline(cues = listOf(cue(0.0))))
        val sink = FakeRenderAudioSink()

        scheduler.update(frame(0.0), sink)

        assertEquals(listOf("hitnormal"), sink.oneShots.map { it.sampleName })
    }

    @Test
    fun pauseStopsLoopsAndResumeDoesNotReplayACueAtThePauseTime() {
        val loop = loop(
            key = "slider:1:sliderslide",
            startTimeMs = 0.0,
            endTimeMs = 3_000.0
        )
        val scheduler = OsuHitsoundScheduler(
            timeline(cues = listOf(cue(1_000.0)), loops = listOf(loop))
        )
        val sink = FakeRenderAudioSink()

        scheduler.update(frame(0.0, isPlaying = true), sink)
        scheduler.update(frame(1_000.0, isPlaying = false), sink)
        scheduler.update(frame(1_000.0, isPlaying = true), sink)

        assertEquals(emptyList<RenderSample>(), sink.oneShots)
        assertEquals(2, sink.stopAllCalls)
        assertEquals(listOf(loop.key, loop.key), sink.loopStarts.map { it.key })
    }

    @Test
    fun clockEpochResetStopsLoopsAndDoesNotDumpMissedCues() {
        val loop = loop(
            key = "spinner:1:spinnerspin",
            startTimeMs = 0.0,
            endTimeMs = 3_000.0
        )
        val scheduler = OsuHitsoundScheduler(
            timeline(cues = listOf(cue(2_000.0)), loops = listOf(loop))
        )
        val sink = FakeRenderAudioSink()

        scheduler.update(frame(500.0, clockEpoch = 1), sink)
        assertEquals(listOf(loop.key), sink.loopStarts.map { it.key })

        scheduler.update(frame(3_500.0, clockEpoch = 2), sink)

        assertEquals(emptyList<RenderSample>(), sink.oneShots)
        assertEquals(2, sink.stopAllCalls)
    }

    @Test
    fun backwardSeekWithoutAnEpochChangeAlsoClearsStaleState() {
        val loop = loop(
            key = "slider:1:sliderslide",
            startTimeMs = 0.0,
            endTimeMs = 3_000.0
        )
        val scheduler = OsuHitsoundScheduler(
            timeline(
                cues = listOf(
                    cue(1_000.0, sampleName = "hitnormal"),
                    cue(2_000.0, sampleName = "hitwhistle")
                ),
                loops = listOf(loop)
            )
        )
        val sink = FakeRenderAudioSink()

        scheduler.update(frame(0.0), sink)
        scheduler.update(frame(1_000.0), sink)
        scheduler.update(frame(2_000.0), sink)
        scheduler.update(frame(500.0), sink)

        assertEquals(listOf("hitnormal", "hitwhistle"), sink.oneShots.map { it.sampleName })
        assertEquals(2, sink.stopAllCalls)
        assertEquals(listOf(loop.key, loop.key), sink.loopStarts.map { it.key })
    }

    @Test
    fun largeForwardJumpSkipsStaleCuesButStillFiresRecentOnes() {
        val scheduler = OsuHitsoundScheduler(
            timeline(
                cues = listOf(
                    cue(1_000.0, sampleName = "stale"),
                    cue(4_900.0, sampleName = "recent"),
                    cue(5_000.0, sampleName = "boundary")
                )
            )
        )
        val sink = FakeRenderAudioSink()

        scheduler.update(frame(0.0), sink)
        scheduler.update(frame(5_000.0), sink)

        assertEquals(listOf("recent", "boundary"), sink.oneShots.map { it.sampleName })
    }

    @Test
    fun loopsStartOnceAndStopAtTheirExclusiveEndBoundary() {
        val sliderLoop = loop(
            key = "slider:1:sliderslide",
            startTimeMs = 1_000.0,
            endTimeMs = 2_000.0
        )
        val spinnerLoop = loop(
            key = "spinner:2:spinnerspin",
            startTimeMs = 1_500.0,
            endTimeMs = 2_500.0
        )
        val scheduler = OsuHitsoundScheduler(
            timeline(loops = listOf(sliderLoop, spinnerLoop))
        )
        val sink = FakeRenderAudioSink()

        listOf(900.0, 1_000.0, 1_499.0, 1_500.0, 1_750.0, 1_999.0, 2_000.0, 2_499.0, 2_500.0)
            .forEach { timeMs ->
                scheduler.update(frame(timeMs), sink)
            }

        assertEquals(
            listOf(sliderLoop.key, spinnerLoop.key),
            sink.loopStarts.map { it.key }
        )
        assertEquals(listOf(sliderLoop.key, spinnerLoop.key), sink.loopStops)
    }

    @Test
    fun layeredHitSoundsDisabledSuppressesNormalWhenAnAdditionExists() {
        val scheduler = OsuHitsoundScheduler(
            timeline(
                cues = listOf(
                    OsuHitsoundCue(
                        timeMs = 500.0,
                        samples = listOf(
                            sample("hitnormal"),
                            sample("hitwhistle"),
                            sample("hitclap")
                        )
                    )
                )
            ),
            layeredHitSounds = false
        )
        val sink = FakeRenderAudioSink()

        scheduler.update(frame(500.0), sink)

        assertEquals(
            listOf("hitwhistle", "hitclap"),
            sink.oneShots.map { it.sampleName }
        )
    }

    @Test
    fun layeredHitSoundsDisabledKeepsNormalOnlyCues() {
        val scheduler = OsuHitsoundScheduler(
            timeline(cues = listOf(cue(500.0))),
            layeredHitSounds = false
        )
        val sink = FakeRenderAudioSink()

        scheduler.update(frame(500.0), sink)

        assertEquals(listOf("hitnormal"), sink.oneShots.map { it.sampleName })
    }

    @Test
    fun layeredHitSoundsEnabledLeavesAdditionCuesUnchanged() {
        val scheduler = OsuHitsoundScheduler(
            timeline(
                cues = listOf(
                    OsuHitsoundCue(
                        timeMs = 500.0,
                        samples = listOf(sample("hitnormal"), sample("hitfinish"))
                    )
                )
            ),
            layeredHitSounds = true
        )
        val sink = FakeRenderAudioSink()

        scheduler.update(frame(500.0), sink)

        assertEquals(
            listOf("hitnormal", "hitfinish"),
            sink.oneShots.map { it.sampleName }
        )
    }

    @Test
    fun layeredHitSoundsDisabledSuppressesSlideLoopWhenSliderWhistles() {
        val slideLoop = OsuHitsoundLoop(
            key = "slider:7:sliderslide",
            startTimeMs = 500.0,
            endTimeMs = 2_000.0,
            sample = sample("sliderslide")
        )
        val whistleLoop = OsuHitsoundLoop(
            key = "slider:7:sliderwhistle",
            startTimeMs = 500.0,
            endTimeMs = 2_000.0,
            sample = sample("sliderwhistle")
        )
        val otherSlideLoop = OsuHitsoundLoop(
            key = "slider:8:sliderslide",
            startTimeMs = 500.0,
            endTimeMs = 2_000.0,
            sample = sample("sliderslide")
        )
        val scheduler = OsuHitsoundScheduler(
            timeline(loops = listOf(slideLoop, whistleLoop, otherSlideLoop)),
            layeredHitSounds = false
        )
        val sink = FakeRenderAudioSink()

        scheduler.update(frame(600.0), sink)

        assertEquals(
            listOf(whistleLoop.key, otherSlideLoop.key),
            sink.loopStarts.map { it.key }
        )
    }

    private fun frame(
        mapTimeMs: Double,
        clockEpoch: Long = 1L,
        isPlaying: Boolean = true,
        latencyMs: Int = 0
    ) = RenderAudioFrame(
        mapTimeMs = mapTimeMs,
        clockEpoch = clockEpoch,
        isPlaying = isPlaying,
        latencyMs = latencyMs
    )

    private fun cue(
        timeMs: Double,
        sampleName: String = "hitnormal"
    ) = OsuHitsoundCue(
        timeMs = timeMs,
        samples = listOf(sample(sampleName))
    )

    private fun loop(
        key: String,
        startTimeMs: Double,
        endTimeMs: Double
    ) = OsuHitsoundLoop(
        key = key,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        sample = sample("sliderslide")
    )

    private fun sample(sampleName: String) = RenderSample(
        sampleName = sampleName,
        bank = RenderSampleBank.NORMAL
    )

    private fun timeline(
        cues: List<OsuHitsoundCue> = emptyList(),
        loops: List<OsuHitsoundLoop> = emptyList()
    ): OsuRenderTimeline {
        val anchor = OsuCircleObject(
            startTimeMs = 0.0,
            position = RenderPoint(256f, 192f),
            stackHeight = 0,
            comboNumber = 1,
            comboColourIndex = 0,
            objectScale = 1f,
            preemptMs = 1_000.0,
            fadeInMs = 400.0,
            hitsoundCues = cues,
            hitsoundLoops = loops
        )
        return OsuRenderTimeline(
            beatmapSetId = 42L,
            difficultyName = "Hard",
            audioFilename = "audio.mp3",
            approachRate = 9f,
            overallDifficulty = 5f,
            circleSize = 4f,
            preemptMs = 1_000.0,
            fadeInMs = 400.0,
            sliderMultiplier = 1.4f,
            objects = listOf(anchor),
            timingPoints = emptyList(),
            comboColours = emptyList(),
            sliderTrackOverride = null,
            sliderBorder = null
        )
    }

    private class FakeRenderAudioSink : RenderAudioSink {
        val oneShots = mutableListOf<RenderSample>()
        val loopStarts = mutableListOf<StartedLoop>()
        val loopStops = mutableListOf<String>()
        var stopAllCalls = 0

        override fun playSample(sample: RenderSample) {
            oneShots += sample
        }

        override fun startLoop(key: String, sample: RenderSample) {
            loopStarts += StartedLoop(key, sample)
        }

        override fun stopLoop(key: String) {
            loopStops += key
        }

        override fun stopAll() {
            stopAllCalls++
        }
    }

    private data class StartedLoop(
        val key: String,
        val sample: RenderSample
    )
}
