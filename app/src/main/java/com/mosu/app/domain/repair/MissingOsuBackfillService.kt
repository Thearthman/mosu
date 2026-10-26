package com.mosu.app.domain.repair

import android.content.Context
import android.util.Log
import com.mosu.app.data.SettingsManager
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.domain.download.BeatmapDownloader
import com.mosu.app.domain.download.DownloadState
import com.mosu.app.domain.render.OsuBeatmapParser
import com.mosu.app.domain.render.OsuRenderCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

enum class MissingOsuBackfillPhase {
    Scanning,
    Downloading,
    Extracting,
    Completed
}

data class MissingOsuBackfillProgress(
    val phase: MissingOsuBackfillPhase,
    val processed: Int,
    val total: Int,
    val currentSetId: Long? = null,
    val downloadProgress: Int = -1
)

data class MissingOsuBackfillResult(
    val total: Int,
    val repaired: Int,
    val skipped: Int,
    val failureReasons: Map<String, Int> = emptyMap()
)

data class OsuArchiveExtractionResult(
    val addedDifficulties: Set<String>,
    val failureReasons: Map<String, Int> = emptyMap()
)

/**
 * Writes only previously absent `.osu` files from an `.osz`. Existing files,
 * audio, covers, and every other archive entry are deliberately ignored.
 */
object OsuArchiveBackfillExtractor {
    private const val MAX_OSU_BYTES = 4 * 1024 * 1024

    fun extractMissingMaps(
        archive: File,
        outputRoot: File,
        expectedBeatmapSetId: Long,
        missingDifficultyNames: Set<String>
    ): OsuArchiveExtractionResult {
        if (missingDifficultyNames.isEmpty() || !archive.isFile) {
            return OsuArchiveExtractionResult(emptySet())
        }

        val remaining = missingDifficultyNames.toMutableSet()
        val added = linkedSetOf<String>()
        val failures = linkedMapOf<String, Int>()
        fun failure(reason: String) {
            failures[reason] = (failures[reason] ?: 0) + 1
        }

        try {
            ZipFile(archive).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory || !entry.name.endsWith(".osu", ignoreCase = true)) {
                        continue
                    }
                    val relative = normalizedRelativePath(entry.name)
                    if (relative == null) {
                        failure("unsafe-path")
                        continue
                    }
                    if (entry.size > MAX_OSU_BYTES) {
                        failure("map-too-large")
                        continue
                    }
                    val bytes = try {
                        zip.getInputStream(entry).use(::readLimited)
                    } catch (_: Exception) {
                        failure("read-failed")
                        continue
                    }
                    if (bytes == null) {
                        failure("map-too-large")
                        continue
                    }

                    val header = runCatching {
                        OsuBeatmapParser.parseHeader(bytes.toString(Charsets.UTF_8))
                    }.getOrNull()
                    if (header == null) {
                        failure("invalid-map")
                        continue
                    }
                    if (header.mode != 0) {
                        failure("unsupported-mode")
                        continue
                    }
                    if (header.beatmapSetId != expectedBeatmapSetId) {
                        failure("wrong-set-id")
                        continue
                    }
                    if (header.difficultyName !in remaining) {
                        continue
                    }

                    val destination = safeDestination(outputRoot, relative)
                    if (destination == null) {
                        failure("unsafe-path")
                        continue
                    }
                    val written = writeWithoutOverwrite(destination, bytes)
                    if (written == null) {
                        failure("write-failed")
                        continue
                    }
                    added += header.difficultyName
                    remaining -= header.difficultyName
                }
            }
        } catch (_: Exception) {
            failure("invalid-archive")
        }

        return OsuArchiveExtractionResult(
            addedDifficulties = added,
            failureReasons = failures
        )
    }

    /** Guarantees temporary archive cleanup even if extraction fails unexpectedly. */
    fun extractMissingMapsAndDeleteArchive(
        archive: File,
        outputRoot: File,
        expectedBeatmapSetId: Long,
        missingDifficultyNames: Set<String>
    ): OsuArchiveExtractionResult = try {
        extractMissingMaps(archive, outputRoot, expectedBeatmapSetId, missingDifficultyNames)
    } finally {
        archive.delete()
    }

    private fun normalizedRelativePath(name: String): String? {
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith('/') || normalized.matches(Regex("^[A-Za-z]:.*"))) return null
        val parts = normalized.split('/')
        if (parts.isEmpty() || parts.any { it.isBlank() || it == "." || it == ".." }) return null
        return parts.joinToString("/")
    }

    private fun safeDestination(root: File, relative: String): File? {
        val rootPath = runCatching { root.canonicalPath }.getOrElse { root.absolutePath }
        val candidate = File(root, relative)
        val candidatePath = runCatching { candidate.canonicalPath }.getOrElse { candidate.absolutePath }
        if (!candidatePath.startsWith(rootPath + File.separator)) return null
        return candidate
    }

    /**
     * If the archive's original relative path is occupied, use a unique repair
     * suffix rather than replacing a file that may belong to another difficulty.
     */
    private fun writeWithoutOverwrite(original: File, bytes: ByteArray): File? {
        original.parentFile?.mkdirs()
        var destination = original
        if (destination.exists()) {
            val stem = original.nameWithoutExtension
            val extension = original.extension.ifBlank { "osu" }
            var suffix = 1
            do {
                destination = File(original.parentFile, "$stem (repair $suffix).$extension")
                suffix++
            } while (destination.exists())
        }
        if (destination.exists() || !destination.createNewFile()) return null
        return try {
            destination.outputStream().use { it.write(bytes) }
            destination
        } catch (_: Exception) {
            destination.delete()
            null
        }
    }

    private fun readLimited(input: java.io.InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_OSU_BYTES) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}

class MissingOsuBackfillService(
    private val context: Context,
    private val db: AppDatabase,
    private val settingsManager: SettingsManager,
    private val archiveProvider: suspend (
        beatmapSetId: Long,
        onDownloadProgress: suspend (Int) -> Unit
    ) -> File? = { beatmapSetId, onDownloadProgress ->
        val state = BeatmapDownloader(context, settingsManager)
            .downloadBeatmap(beatmapSetId)
            .onEach { downloadState ->
                if (downloadState is DownloadState.Downloading) {
                    onDownloadProgress(downloadState.progress)
                }
            }
            .first { it is DownloadState.Downloaded || it is DownloadState.Error }
        (state as? DownloadState.Downloaded)?.file
    }
) {
    suspend fun repairAll(
        onProgress: suspend (MissingOsuBackfillProgress) -> Unit = {}
    ): MissingOsuBackfillResult = withContext(Dispatchers.IO) {
        onProgress(
            MissingOsuBackfillProgress(
                phase = MissingOsuBackfillPhase.Scanning,
                processed = 0,
                total = 0
            )
        )
        val setIds = db.beatmapDao().getDistinctSetIds()
        onProgress(
            MissingOsuBackfillProgress(
                phase = MissingOsuBackfillPhase.Scanning,
                processed = 0,
                total = setIds.size
            )
        )
        val affected = setIds.mapIndexedNotNull { index, setId ->
            val tracks = db.beatmapDao().getTracksForSet(setId)
            val validNames = validStoredDifficulties(setId)
            val missing = tracks.asSequence()
                .map { it.difficultyName }
                .filter { it.isNotBlank() && it !in validNames }
                .toSet()
            onProgress(
                MissingOsuBackfillProgress(
                    phase = MissingOsuBackfillPhase.Scanning,
                    processed = index + 1,
                    total = setIds.size,
                    currentSetId = setId
                )
            )
            if (missing.isEmpty()) null else MissingSet(setId, missing)
        }

        var repaired = 0
        val failures = linkedMapOf<String, Int>()
        affected.forEachIndexed { index, missingSet ->
            onProgress(
                MissingOsuBackfillProgress(
                    phase = MissingOsuBackfillPhase.Downloading,
                    processed = index,
                    total = affected.size,
                    currentSetId = missingSet.beatmapSetId
                )
            )
            var archive: File? = null
            try {
                archive = archiveProvider(missingSet.beatmapSetId) { downloadProgress ->
                    onProgress(
                        MissingOsuBackfillProgress(
                            phase = MissingOsuBackfillPhase.Downloading,
                            processed = index,
                            total = affected.size,
                            currentSetId = missingSet.beatmapSetId,
                            downloadProgress = downloadProgress
                        )
                    )
                }
                if (archive == null || !archive.isFile) {
                    recordFailure(failures, "download-failed")
                } else {
                    onProgress(
                        MissingOsuBackfillProgress(
                            phase = MissingOsuBackfillPhase.Extracting,
                            processed = index,
                            total = affected.size,
                            currentSetId = missingSet.beatmapSetId
                        )
                    )
                    val directory = File(
                        context.getExternalFilesDir(null),
                        "beatmaps/${missingSet.beatmapSetId}"
                    )
                    val result = OsuArchiveBackfillExtractor.extractMissingMaps(
                        archive = archive,
                        outputRoot = directory,
                        expectedBeatmapSetId = missingSet.beatmapSetId,
                        missingDifficultyNames = missingSet.difficulties
                    )
                    result.failureReasons.forEach { (reason, count) ->
                        repeat(count) { recordFailure(failures, reason) }
                    }
                    if (result.addedDifficulties.isNotEmpty()) {
                        repaired++
                        OsuRenderCache.invalidateSet(context, missingSet.beatmapSetId)
                    } else {
                        recordFailure(
                            failures,
                            if (result.failureReasons.isEmpty()) "maps-not-found" else "no-usable-map"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Missing .osu repair failed for set ${missingSet.beatmapSetId}", e)
                recordFailure(failures, e::class.java.simpleName)
            } finally {
                runCatching { archive?.delete() }
                runCatching {
                    File(context.cacheDir, "${missingSet.beatmapSetId}.osz").delete()
                }
            }
            onProgress(
                MissingOsuBackfillProgress(
                    phase = MissingOsuBackfillPhase.Downloading,
                    processed = index + 1,
                    total = affected.size
                )
            )
        }
        onProgress(
            MissingOsuBackfillProgress(
                phase = MissingOsuBackfillPhase.Completed,
                processed = affected.size,
                total = affected.size
            )
        )

        MissingOsuBackfillResult(
            total = affected.size,
            repaired = repaired,
            skipped = affected.size - repaired,
            failureReasons = failures
        )
    }

    private fun validStoredDifficulties(beatmapSetId: Long): Set<String> {
        val directory = File(
            context.getExternalFilesDir(null),
            "beatmaps/$beatmapSetId"
        )
        if (!directory.isDirectory) return emptySet()
        return try {
            directory.walkTopDown()
                .filter { it.isFile && it.extension.equals("osu", ignoreCase = true) }
                .mapNotNull { file ->
                    val parsed = OsuBeatmapParser.parseAndValidate(file, beatmapSetId)
                        ?: return@mapNotNull null
                    parsed.takeIf { it.hitObjects.isNotEmpty() }
                        ?.header
                        ?.difficultyName
                }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun recordFailure(reasons: MutableMap<String, Int>, reason: String) {
        reasons[reason] = (reasons[reason] ?: 0) + 1
    }

    private data class MissingSet(
        val beatmapSetId: Long,
        val difficulties: Set<String>
    )

    companion object {
        private const val TAG = "MissingOsuBackfill"
    }
}
