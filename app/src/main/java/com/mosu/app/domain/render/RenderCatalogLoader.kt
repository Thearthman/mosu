package com.mosu.app.domain.render

import android.content.Context
import android.net.Uri
import com.mosu.app.data.db.AppDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns renderer discovery I/O. It is called from effects/background dispatchers,
 * never from the Canvas frame callback.
 */
class RenderCatalogLoader(
    private val context: Context,
    private val db: AppDatabase,
    private val supportedModes: Set<RenderMode>
) {
    suspend fun loadCatalog(mediaId: Long): RenderCatalog? = withContext(Dispatchers.IO) {
        val entity = db.beatmapDao().getBeatmapByUid(mediaId) ?: return@withContext null
        val directory = File(
            context.getExternalFilesDir(null),
            "beatmaps/${entity.beatmapSetId}"
        )
        if (!directory.isDirectory) return@withContext null

        val maps = scanMaps(directory, entity.beatmapSetId)
        if (maps.isEmpty()) return@withContext null

        val directAudioName = entity.audioPath
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { Uri.parse(it).lastPathSegment }.getOrNull() }
        RenderDifficultyMatcher.selectCatalog(
            maps = maps,
            beatmapSetId = entity.beatmapSetId,
            defaultDifficultyName = entity.difficultyName,
            playedAudioFilename = directAudioName,
            supportedModes = supportedModes
        )
    }

    /**
     * Header discovery is intentionally ruleset-agnostic. Unsupported maps are
     * still needed to map a playing library row back to its audio group when a
     * mixed-mode set contains several audio files. [RenderDifficultyMatcher]
     * then narrows the selected group to modes with a registered renderer.
     *
     * Each file is handled independently so one malformed map cannot reject the
     * rest of the set.
     */
    private fun scanMaps(directory: File, expectedBeatmapSetId: Long): List<RenderDifficulty> {
        val maps = mutableListOf<RenderDifficulty>()
        val pendingDirectories = ArrayDeque<File>()
        pendingDirectories.add(directory)

        while (pendingDirectories.isNotEmpty()) {
            val currentDirectory = pendingDirectories.removeFirst()
            val children = runCatching { currentDirectory.listFiles() }
                .getOrNull()
                .orEmpty()
            children.forEach { file ->
                when {
                    file.isDirectory -> pendingDirectories.add(file)
                    file.isFile && file.extension.equals("osu", ignoreCase = true) -> {
                        val map = runCatching {
                            val canonicalPath = file.canonicalPath
                            val header = OsuFileHeaderCache.header(file)
                                ?: return@runCatching null
                            if (
                                !OsuFileHeaderReader.belongsToBeatmapSet(
                                    header.beatmapSetId,
                                    expectedBeatmapSetId
                                )
                            ) {
                                return@runCatching null
                            }
                            val mode = RenderMode.fromOsuMode(header.mode)
                                ?: return@runCatching null
                            RenderDifficulty(
                                key = RenderDifficultyKey(mode, header.difficultyName),
                                path = canonicalPath,
                                beatmapSetId = expectedBeatmapSetId,
                                audioFilename = header.audioFilename,
                                beatmapId = header.beatmapId
                            )
                        }.getOrNull()
                        if (map != null) maps += map
                    }
                }
            }
        }

        return maps.distinctBy { it.path }
    }
}
