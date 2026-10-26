package com.mosu.app.domain.render

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OsuBeatmapParserTest {
    private val tempDirectory: File = Files.createTempDirectory("mosu-parser-test").toFile()

    @After
    fun cleanUp() {
        tempDirectory.deleteRecursively()
    }

    @Test
    fun parsesHeaderAndFallsBackToOverallDifficultyForApproachRate() {
        val header = OsuBeatmapParser.parseHeader(
            mapText(
                beatmapSetId = 123,
                difficulty = """
                    [Difficulty]
                    OverallDifficulty:7.25
                    CircleSize:4.2
                    SliderMultiplier:1.7
                    StackLeniency:0.4
                """.trimIndent()
            )
        )

        assertEquals(123L, header.beatmapSetId)
        assertEquals(10L, header.beatmapId)
        assertEquals(0, header.mode)
        assertEquals("audio.mp3", header.audioFilename)
        assertEquals("Hard", header.difficultyName)
        assertEquals(7.25f, header.approachRate, 0.0001f)
        assertEquals(7.25f, header.overallDifficulty, 0.0001f)
        assertEquals(4.2f, header.circleSize, 0.0001f)
        assertEquals(1.7f, header.sliderMultiplier, 0.0001f)
        assertEquals(0.4f, header.stackLeniency, 0.0001f)
    }

    @Test
    fun rejectsMissingRequiredHeaderFields() {
        assertThrows(OsuMapParseException::class.java) {
            OsuBeatmapParser.parseHeader(
                """
                    osu file format v14
                    [General]
                    Mode:0
                    [Metadata]
                    BeatmapSetID:1
                    Version:Hard
                """.trimIndent()
            )
        }
        assertThrows(OsuMapParseException::class.java) {
            OsuBeatmapParser.parseHeader(
                """
                    osu file format v14
                    [General]
                    AudioFilename:audio.mp3
                    [Metadata]
                    BeatmapSetID:1
                """.trimIndent()
            )
        }
    }

    @Test
    fun parsesPresentBlankAndAbsentBeatmapIdsAsNullable() {
        assertEquals(10L, OsuBeatmapParser.parseHeader(mapText()).beatmapId)
        assertNull(
            OsuBeatmapParser.parseHeader(
                mapText().replace("BeatmapID:10", "BeatmapID:")
            ).beatmapId
        )
        assertNull(
            OsuBeatmapParser.parseHeader(
                mapText().replace("BeatmapID:10\n", "")
            ).beatmapId
        )
        assertNull(
            OsuBeatmapParser.parseHeader(
                mapText().replace("BeatmapID:10", "BeatmapID:0")
            ).beatmapId
        )
    }

    @Test
    fun parsesStandardCirclesSlidersAndSpinners() {
        val parsed = OsuBeatmapParser.parse(
            mapText(
                hitObjects = """
                    256,192,1000,1,0,0:0:0:0:
                    128,192,2000,2,0,L|256:192,2,100
                    256,192,3000,8,0,4000
                """.trimIndent()
            )
        )

        assertEquals(3, parsed.hitObjects.size)
        assertTrue(parsed.hitObjects[0] is OsuRawCircle)
        val slider = parsed.hitObjects[1] as OsuRawSlider
        assertEquals(OsuCurveType.LINEAR, slider.curveType)
        assertEquals(2, slider.spanCount)
        assertEquals(100f, slider.pathLength, 0.0001f)
        val spinner = parsed.hitObjects[2] as OsuRawSpinner
        assertEquals(4_000.0, spinner.endTimeMs, 0.0001)
    }

    @Test
    fun simultaneousHitObjectsRetainTheirFileOrder() {
        val parsed = OsuBeatmapParser.parse(
            mapText(
                hitObjects = """
                    300,192,1000,1,0,0:0:0:0:
                    100,192,1000,1,0,0:0:0:0:
                """.trimIndent()
            )
        )

        assertEquals(listOf(300f, 100f), parsed.hitObjects.map { it.position.x })
    }

    @Test
    fun rejectsManiaHoldNotesAndUnknownTypes() {
        assertThrows(OsuMapParseException::class.java) {
            OsuBeatmapParser.parse(
                mapText(hitObjects = "256,192,1000,128,0,1500:0:0:0:0:")
            )
        }
        assertThrows(OsuMapParseException::class.java) {
            OsuBeatmapParser.parse(
                mapText(hitObjects = "256,192,1000,32,0,0:0:0:0:")
            )
        }
    }

    @Test
    fun rejectsUnsupportedSliderCurvesAndMalformedObjects() {
        assertThrows(OsuMapParseException::class.java) {
            OsuBeatmapParser.parse(
                mapText(hitObjects = "0,0,1000,2,0,X|50:50,1,100")
            )
        }
        assertThrows(OsuMapParseException::class.java) {
            OsuBeatmapParser.parse(mapText(hitObjects = "0,0,not-a-time,1,0"))
        }
    }

    @Test
    fun parseAndValidateRequiresStandardModeAndMatchingSetId() {
        val standardFile = writeMap("standard.osu", mapText(beatmapSetId = 42))
        val taikoFile = writeMap("taiko.osu", mapText(beatmapSetId = 42, mode = 1))
        val wrongSetFile = writeMap("wrong.osu", mapText(beatmapSetId = 43))

        assertEquals(42L, OsuBeatmapParser.parseAndValidate(standardFile, 42)?.header?.beatmapSetId)
        assertNull(OsuBeatmapParser.parseAndValidate(taikoFile, 42))
        assertNull(OsuBeatmapParser.parseAndValidate(wrongSetFile, 42))
    }

    @Test
    fun audioFilenameNormalizationHandlesSeparatorsAndCase() {
        assertEquals(
            "subfolder/audio.mp3",
            OsuBeatmapParser.normalizeAudioFilename("  \\SubFolder\\Audio.MP3 ")
        )
    }

    private fun writeMap(name: String, content: String): File =
        File(tempDirectory, name).apply { writeText(content) }

    private fun mapText(
        beatmapSetId: Long = 42,
        mode: Int = 0,
        difficulty: String = """
            [Difficulty]
            ApproachRate:9
            OverallDifficulty:7
            CircleSize:4
            SliderMultiplier:1.4
            StackLeniency:0.7
        """.trimIndent(),
        hitObjects: String = "256,192,1000,1,0,0:0:0:0:"
    ): String =
        """
            osu file format v14
            [General]
            AudioFilename: audio.mp3
            Mode: $mode
            [Metadata]
            Title:Test Title
            Artist:Test Artist
            Creator:Test Creator
            Version:Hard
            BeatmapID:10
            BeatmapSetID:$beatmapSetId
            $difficulty
            [TimingPoints]
            0,500,4,2,0,100,1,0
            [Colours]
            Combo1 : 255,128,64
            Combo2 : 12:34:56
            [HitObjects]
            $hitObjects
        """.trimIndent()
}
