package com.mosu.app.domain.repair

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OsuArchiveBackfillExtractorTest {
    private val tempDirectory: File = Files.createTempDirectory("mosu-backfill-test").toFile()

    @After
    fun cleanUp() {
        tempDirectory.deleteRecursively()
    }

    @Test
    fun extractsOnlyMissingOsuEntriesAndLeavesOtherAssetsUntouched() {
        val archive = createArchive(
            "set/maps/Hard.osu" to mapText("Hard"),
            "set/maps/Easy.osu" to mapText("Easy"),
            "set/audio.mp3" to "audio",
            "set/bg.jpg" to "cover"
        )
        val output = File(tempDirectory, "output")

        val result = OsuArchiveBackfillExtractor.extractMissingMaps(
            archive = archive,
            outputRoot = output,
            expectedBeatmapSetId = 42,
            missingDifficultyNames = setOf("Hard")
        )

        assertEquals(setOf("Hard"), result.addedDifficulties)
        assertTrue(result.failureReasons.isEmpty())
        assertTrue(File(output, "set/maps/Hard.osu").isFile)
        assertFalse(File(output, "set/maps/Easy.osu").exists())
        assertFalse(File(output, "set/audio.mp3").exists())
        assertFalse(File(output, "set/bg.jpg").exists())
    }

    @Test
    fun existingFilesAreNeverOverwritten() {
        val archive = createArchive("maps/Hard.osu" to mapText("Hard"))
        val output = File(tempDirectory, "output")
        val original = File(output, "maps/Hard.osu").apply {
            parentFile?.mkdirs()
            writeText("ORIGINAL MAP")
        }

        val result = OsuArchiveBackfillExtractor.extractMissingMaps(
            archive = archive,
            outputRoot = output,
            expectedBeatmapSetId = 42,
            missingDifficultyNames = setOf("Hard")
        )
        val repaired = File(output, "maps/Hard (repair 1).osu")

        assertEquals("ORIGINAL MAP", original.readText())
        assertTrue(repaired.isFile)
        assertTrue(repaired.readText().contains("Version:Hard"))
        assertEquals(setOf("Hard"), result.addedDifficulties)
    }

    @Test
    fun pathTraversalEntriesAreRejectedWithoutWritingOutsideOutputRoot() {
        val archive = createArchive("../escape.osu" to mapText("Hard"))
        val output = File(tempDirectory, "output")

        val result = OsuArchiveBackfillExtractor.extractMissingMaps(
            archive = archive,
            outputRoot = output,
            expectedBeatmapSetId = 42,
            missingDifficultyNames = setOf("Hard")
        )

        assertFalse(File(tempDirectory, "escape.osu").exists())
        assertFalse(File(output.parentFile, "escape.osu").exists())
        assertTrue(result.addedDifficulties.isEmpty())
        assertEquals(1, result.failureReasons["unsafe-path"])
    }

    @Test
    fun rejectsMapsWithWrongSetIdOrUnsupportedMode() {
        val archive = createArchive(
            "wrong.osu" to mapText("Hard", beatmapSetId = 99),
            "taiko.osu" to mapText("Insane", mode = 1)
        )
        val output = File(tempDirectory, "output")

        val result = OsuArchiveBackfillExtractor.extractMissingMaps(
            archive = archive,
            outputRoot = output,
            expectedBeatmapSetId = 42,
            missingDifficultyNames = setOf("Hard", "Insane")
        )

        assertTrue(result.addedDifficulties.isEmpty())
        assertEquals(1, result.failureReasons["wrong-set-id"])
        assertEquals(1, result.failureReasons["unsupported-mode"])
        assertFalse(output.exists())
    }

    @Test
    fun archiveCleanupRunsAfterSuccessfulExtraction() {
        val archive = createArchive("maps/Hard.osu" to mapText("Hard"))

        OsuArchiveBackfillExtractor.extractMissingMapsAndDeleteArchive(
            archive = archive,
            outputRoot = File(tempDirectory, "output"),
            expectedBeatmapSetId = 42,
            missingDifficultyNames = setOf("Hard")
        )

        assertFalse(archive.exists())
    }

    @Test
    fun archiveCleanupRunsAfterInvalidArchiveFailure() {
        val archive = File(tempDirectory, "invalid.osz").apply { writeText("not a zip") }

        val result = OsuArchiveBackfillExtractor.extractMissingMapsAndDeleteArchive(
            archive = archive,
            outputRoot = File(tempDirectory, "output"),
            expectedBeatmapSetId = 42,
            missingDifficultyNames = setOf("Hard")
        )

        assertFalse(archive.exists())
        assertTrue(result.addedDifficulties.isEmpty())
        assertEquals(1, result.failureReasons["invalid-archive"])
    }

    private fun createArchive(vararg entries: Pair<String, String>): File {
        val archive = File(tempDirectory, "set-${System.nanoTime()}.osz")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return archive
    }

    private fun mapText(
        difficulty: String,
        beatmapSetId: Long = 42,
        mode: Int = 0
    ): String =
        """
            osu file format v14
            [General]
            AudioFilename: audio.mp3
            Mode:$mode
            [Metadata]
            Title:Test
            Artist:Test
            Creator:Test
            Version:$difficulty
            BeatmapSetID:$beatmapSetId
            [Difficulty]
            OverallDifficulty:7
            [TimingPoints]
            0,500,4,2,0,100,1,0
            [HitObjects]
            256,192,1000,1,0,0:0:0:0:
        """.trimIndent()
}
