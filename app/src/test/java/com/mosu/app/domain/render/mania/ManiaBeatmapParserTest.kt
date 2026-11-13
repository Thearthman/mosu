package com.mosu.app.domain.render.mania

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ManiaBeatmapParserTest {
    private val tempDirectory: File =
        Files.createTempDirectory("mosu-mania-parser-test").toFile()

    @After
    fun cleanUp() {
        tempDirectory.deleteRecursively()
    }

    @Test
    fun parsesNotesAndHoldsInFileOrder() {
        val parsed = ManiaBeatmapParser.parse(
            maniaMap(
                hitObjects = """
                    64,192,1000,1,0,0:0:0:0:
                    128,192,1100,128,0,1600:0:0:0:0:
                """.trimIndent()
            )
        )

        assertEquals(42L, parsed.beatmapSetId)
        assertEquals(10L, parsed.beatmapId)
        assertEquals("Hard", parsed.difficultyName)
        assertEquals("audio.mp3", parsed.audioFilename)
        assertEquals(4, parsed.keyCount)
        assertEquals(2, parsed.hitObjects.size)

        val note = parsed.hitObjects[0]
        assertEquals(1_000.0, note.startTimeMs, 0.0001)
        assertEquals(1_000.0, note.endTimeMs, 0.0001)
        assertEquals(0, note.column)
        assertFalse(note.isHold)

        val hold = parsed.hitObjects[1]
        assertEquals(1_100.0, hold.startTimeMs, 0.0001)
        assertEquals(1_600.0, hold.endTimeMs, 0.0001)
        assertEquals(1, hold.column)
        assertTrue(hold.isHold)
    }

    @Test
    fun mapsLegacyPlayfieldXCoordinatesToFourKeyColumns() {
        val parsed = ManiaBeatmapParser.parse(
            maniaMap(
                hitObjects = """
                    0,192,1000,1,0,0:0:0:0:
                    127,192,1100,1,0,0:0:0:0:
                    128,192,1200,1,0,0:0:0:0:
                    255,192,1300,1,0,0:0:0:0:
                    256,192,1400,1,0,0:0:0:0:
                    384,192,1500,1,0,0:0:0:0:
                """.trimIndent()
            )
        )

        assertEquals(
            listOf(0, 0, 1, 1, 2, 3),
            parsed.hitObjects.map { it.column }
        )
    }

    @Test
    fun supportsEveryManiaKeyCountFromOneThroughTen() {
        for (keys in 1..10) {
            val parsed = ManiaBeatmapParser.parse(
                maniaMap(
                    difficulty = "CircleSize: $keys",
                    hitObjects = "256,192,1000,1,0,0:0:0:0:"
                )
            )

            assertEquals(keys, parsed.keyCount)
        }
    }

    @Test
    fun defaultsMissingCircleSizeToFourKeys() {
        val parsed = ManiaBeatmapParser.parse(
            maniaMap(difficulty = "")
        )

        assertEquals(4, parsed.keyCount)
    }

    @Test
    fun rejectsUnsupportedKeyCounts() {
        assertThrows(ManiaMapParseException::class.java) {
            ManiaBeatmapParser.parse(maniaMap(difficulty = "CircleSize: 0"))
        }
        assertThrows(ManiaMapParseException::class.java) {
            ManiaBeatmapParser.parse(maniaMap(difficulty = "CircleSize: 11"))
        }
    }

    @Test
    fun rejectsWrongModeAndMissingRequiredFields() {
        assertThrows(ManiaMapParseException::class.java) {
            ManiaBeatmapParser.parse(maniaMap(mode = "0"))
        }
        assertThrows(ManiaMapParseException::class.java) {
            ManiaBeatmapParser.parse(maniaMap(audioFilename = ""))
        }
        assertThrows(ManiaMapParseException::class.java) {
            ManiaBeatmapParser.parse(maniaMap(difficultyName = ""))
        }
        assertThrows(ManiaMapParseException::class.java) {
            ManiaBeatmapParser.parse(maniaMap().replace("Mode: 3\n", ""))
        }
    }

    @Test
    fun rejectsMalformedTimingAndHitObjectLines() {
        assertThrows(ManiaMapParseException::class.java) {
            ManiaBeatmapParser.parse(maniaMap(timingPoints = "malformed"))
        }
        assertThrows(ManiaMapParseException::class.java) {
            ManiaBeatmapParser.parse(
                maniaMap(timingPoints = "0,not-a-beat,4,2,0,100,1,0")
            )
        }
        assertThrows(ManiaMapParseException::class.java) {
            ManiaBeatmapParser.parse(
                maniaMap(hitObjects = "64,192,not-a-time,1,0")
            )
        }
        assertThrows(ManiaMapParseException::class.java) {
            ManiaBeatmapParser.parse(
                maniaMap(hitObjects = "64,192,1000,2,0")
            )
        }
        assertThrows(ManiaMapParseException::class.java) {
            ManiaBeatmapParser.parse(
                maniaMap(hitObjects = "64,192,1000,128,0")
            )
        }
    }

    @Test
    fun rejectsMapsWithoutHitObjects() {
        assertThrows(ManiaMapParseException::class.java) {
            ManiaBeatmapParser.parse(maniaMap(hitObjects = ""))
        }
    }

    @Test
    fun appliesLegacyTimingOffsetBeforeFormatVersionFive() {
        val parsed = ManiaBeatmapParser.parse(
            maniaMap(
                formatVersion = 4,
                timingPoints = "-100,500,4,2,0,100,1,0",
                hitObjects = """
                    64,192,1000,1,0,0:0:0:0:
                    128,192,1100,128,0,1600:0:0:0:0:
                """.trimIndent()
            )
        )

        assertEquals(-76.0, parsed.timingPoints.single().timeMs, 0.0001)
        assertEquals(1_024.0, parsed.hitObjects[0].startTimeMs, 0.0001)
        assertEquals(1_124.0, parsed.hitObjects[1].startTimeMs, 0.0001)
        assertEquals(1_624.0, parsed.hitObjects[1].endTimeMs, 0.0001)
    }

    @Test
    fun doesNotApplyLegacyTimingOffsetFromFormatVersionFiveOnwards() {
        val parsed = ManiaBeatmapParser.parse(
            maniaMap(formatVersion = 5, hitObjects = "64,192,1000,1,0,0:0:0:0:")
        )

        assertEquals(1_000.0, parsed.hitObjects.single().startTimeMs, 0.0001)
    }

    @Test
    fun clampsHoldEndToItsStartTime() {
        val parsed = ManiaBeatmapParser.parse(
            maniaMap(hitObjects = "64,192,1000,128,0,900:0:0:0:0:")
        )

        assertEquals(1_000.0, parsed.hitObjects.single().endTimeMs, 0.0001)
    }

    @Test
    fun parseAndValidateAcceptsLegacyMapsWithoutEmbeddedSetId() {
        val file = writeMap(
            "legacy.osu",
            maniaMap(beatmapSetId = "").replace("BeatmapSetID:\n", "")
        )

        val parsed = ManiaBeatmapParser.parseAndValidate(
            file = file,
            expectedBeatmapSetId = 42,
            expectedDifficultyName = "Hard",
            expectedAudioFilename = "audio.mp3"
        )

        assertEquals("Hard", parsed?.difficultyName)
        assertNull(parsed?.beatmapSetId)
    }

    @Test
    fun parseAndValidateRejectsIdentityMismatchesAndInvalidMaps() {
        val valid = writeMap("valid.osu", maniaMap())
        val wrongSet = writeMap("wrong-set.osu", maniaMap(beatmapSetId = "43"))
        val wrongDifficulty = writeMap(
            "wrong-difficulty.osu",
            maniaMap(difficultyName = "Insane")
        )
        val wrongAudio = writeMap("wrong-audio.osu", maniaMap(audioFilename = "other.mp3"))

        assertEquals(
            "Hard",
            ManiaBeatmapParser.parseAndValidate(
                valid,
                expectedBeatmapSetId = 42,
                expectedDifficultyName = "Hard",
                expectedAudioFilename = "audio.mp3"
            )?.difficultyName
        )
        assertNull(
            ManiaBeatmapParser.parseAndValidate(
                wrongSet,
                expectedBeatmapSetId = 42,
                expectedDifficultyName = "Hard",
                expectedAudioFilename = "audio.mp3"
            )
        )
        assertNull(
            ManiaBeatmapParser.parseAndValidate(
                wrongDifficulty,
                expectedBeatmapSetId = 42,
                expectedDifficultyName = "Hard",
                expectedAudioFilename = "audio.mp3"
            )
        )
        assertNull(
            ManiaBeatmapParser.parseAndValidate(
                wrongAudio,
                expectedBeatmapSetId = 42,
                expectedDifficultyName = "Hard",
                expectedAudioFilename = "audio.mp3"
            )
        )
    }

    private fun writeMap(name: String, content: String): File =
        File(tempDirectory, name).apply { writeText(content) }

    private fun maniaMap(
        formatVersion: Int = 14,
        audioFilename: String = "audio.mp3",
        mode: String = "3",
        beatmapSetId: String = "42",
        difficultyName: String = "Hard",
        difficulty: String = "CircleSize: 4",
        timingPoints: String = "0,500,4,2,0,100,1,0",
        hitObjects: String = "64,192,1000,1,0,0:0:0:0:"
    ): String =
        """
            osu file format v$formatVersion
            [General]
            AudioFilename: $audioFilename
            Mode: $mode
            [Metadata]
            Version: $difficultyName
            BeatmapID: 10
            BeatmapSetID: $beatmapSetId
            [Difficulty]
            $difficulty
            [TimingPoints]
            $timingPoints
            [HitObjects]
            $hitObjects
        """.trimIndent()
}
