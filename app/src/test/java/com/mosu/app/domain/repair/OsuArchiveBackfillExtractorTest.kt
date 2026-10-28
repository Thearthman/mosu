package com.mosu.app.domain.repair

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OsuArchiveBackfillExtractorTest {
    private val tempDirectory: File = Files.createTempDirectory("mosu-backfill-test").toFile()

    @After
    fun cleanUp() {
        tempDirectory.deleteRecursively()
    }

    @Test
    fun extractsAllMapEntriesAndLeavesOtherAssetsUntouched() {
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
            expectedBeatmapSetId = 42
        )

        assertEquals(setOf("Hard", "Easy"), result.addedDifficulties)
        assertTrue(result.failureReasons.isEmpty())
        assertTrue(File(output, "set/maps/Hard.osu").isFile)
        assertTrue(File(output, "set/maps/Easy.osu").isFile)
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
            expectedBeatmapSetId = 42
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
            expectedBeatmapSetId = 42
        )

        assertFalse(File(tempDirectory, "escape.osu").exists())
        assertFalse(File(output.parentFile, "escape.osu").exists())
        assertTrue(result.addedDifficulties.isEmpty())
        assertEquals(1, result.failureReasons["unsafe-path"])
    }

    @Test
    fun extractsNonStandardModesButRejectsMapsWithWrongSetId() {
        val archive = createArchive(
            "wrong.osu" to mapText("Hard", beatmapSetId = 99),
            "taiko.osu" to mapText("Insane", mode = 1)
        )
        val output = File(tempDirectory, "output")

        val result = OsuArchiveBackfillExtractor.extractMissingMaps(
            archive = archive,
            outputRoot = output,
            expectedBeatmapSetId = 42
        )

        assertEquals(setOf("Insane"), result.addedDifficulties)
        assertEquals(1, result.failureReasons["wrong-set-id"])
        assertFalse(File(output, "wrong.osu").exists())
        assertTrue(File(output, "taiko.osu").isFile)
    }

    @Test
    fun extractsLegacyMapsWithoutAnEmbeddedSetId() {
        val archive = createArchive(
            "legacy.osu" to mapText("Legacy").replace("BeatmapSetID:42\n", "")
        )
        val output = File(tempDirectory, "output")

        val result = OsuArchiveBackfillExtractor.extractMissingMaps(
            archive = archive,
            outputRoot = output,
            expectedBeatmapSetId = 42
        )

        assertEquals(setOf("Legacy"), result.addedDifficulties)
        assertTrue(result.failureReasons.isEmpty())
        assertTrue(File(output, "legacy.osu").isFile)
    }

    @Test
    fun repairsEveryLegacyStandardAndNonStandardDifficultyInTheSet() {
        val archive = createArchive(
            "maps/Easy.osu" to legacyMapText("Easy"),
            "maps/Normal.osu" to legacyMapText("Normal"),
            "maps/Hard.osu" to legacyMapText("Hard"),
            "maps/Insane.osu" to legacyMapText("Insane"),
            "maps/Taiko.osu" to legacyMapText("Taiko", mode = 1)
        )
        val output = File(tempDirectory, "output")

        val result = OsuArchiveBackfillExtractor.extractMissingMaps(
            archive = archive,
            outputRoot = output,
            expectedBeatmapSetId = 42
        )

        assertEquals(
            setOf("Easy", "Normal", "Hard", "Insane", "Taiko"),
            result.addedDifficulties
        )
        assertTrue(result.failureReasons.isEmpty())
        listOf("Easy", "Normal", "Hard", "Insane", "Taiko").forEach { difficulty ->
            assertTrue(File(output, "maps/$difficulty.osu").isFile)
        }
    }

    @Test
    fun repeatedRepairDoesNotDuplicateAlreadyValidMaps() {
        val archive = createArchive(
            "maps/Easy.osu" to mapText("Easy"),
            "maps/Hard.osu" to mapText("Hard")
        )
        val output = File(tempDirectory, "output")

        OsuArchiveBackfillExtractor.extractMissingMaps(
            archive = archive,
            outputRoot = output,
            expectedBeatmapSetId = 42
        )
        val second = OsuArchiveBackfillExtractor.extractMissingMaps(
            archive = archive,
            outputRoot = output,
            expectedBeatmapSetId = 42
        )

        assertTrue(second.addedDifficulties.isEmpty())
        assertTrue(second.failureReasons.isEmpty())
        assertEquals(
            setOf("Easy.osu", "Hard.osu"),
            output.resolve("maps").listFiles().orEmpty().map { it.name }.toSet()
        )
    }

    @Test
    fun archiveCleanupRunsAfterSuccessfulExtraction() {
        val archive = createArchive("maps/Hard.osu" to mapText("Hard"))

        OsuArchiveBackfillExtractor.extractMissingMapsAndDeleteArchive(
            archive = archive,
            outputRoot = File(tempDirectory, "output"),
            expectedBeatmapSetId = 42
        )

        assertFalse(archive.exists())
    }

    @Test
    fun archiveCleanupRunsAfterInvalidArchiveFailure() {
        val archive = File(tempDirectory, "invalid.osz").apply { writeText("not a zip") }

        val result = OsuArchiveBackfillExtractor.extractMissingMapsAndDeleteArchive(
            archive = archive,
            outputRoot = File(tempDirectory, "output"),
            expectedBeatmapSetId = 42
        )

        assertFalse(archive.exists())
        assertTrue(result.addedDifficulties.isEmpty())
        assertEquals(1, result.failureReasons["invalid-archive"])
    }

    @Test
    fun cancellationIsPropagatedInsteadOfBeingReportedAsAnArchiveFailure() {
        val archive = createArchive("maps/Hard.osu" to mapText("Hard"))

        assertThrows(CancellationException::class.java) {
            OsuArchiveBackfillExtractor.extractMissingMaps(
                archive = archive,
                outputRoot = File(tempDirectory, "output"),
                expectedBeatmapSetId = 42,
                checkCancelled = { throw CancellationException("cancelled") }
            )
        }
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

    private fun legacyMapText(difficulty: String, mode: Int = 0): String =
        mapText(difficulty = difficulty, mode = mode)
            .replace("BeatmapSetID:42\n", "")
}
