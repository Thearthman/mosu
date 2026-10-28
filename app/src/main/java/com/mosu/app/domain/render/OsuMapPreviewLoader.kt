package com.mosu.app.domain.render

import android.content.Context
import android.net.Uri
import com.mosu.app.data.db.AppDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OsuDifficultyMap(
    val path: String,
    val difficultyName: String,
    val beatmapSetId: Long,
    val audioFilename: String,
    val beatmapId: Long? = null,
    val mode: Int = 0
)

data class OsuPreviewCatalog(
    val beatmapSetId: Long,
    val audioFilename: String,
    val difficulties: List<OsuDifficultyMap>,
    val defaultDifficultyName: String
) {
    /**
     * Stable preference key for the playing song. The set id separates songs,
     * while the normalized audio filename distinguishes multiple-audio albums.
     */
    val selectionKey: String
        get() = "$beatmapSetId|$audioFilename"

    fun resolveSavedDifficulty(savedDifficultyName: String?): String =
        difficulties.firstOrNull { it.difficultyName == savedDifficultyName }?.difficultyName
            ?: defaultDifficultyName
}

/**
 * Owns all renderer I/O. It is called from effects/background dispatchers only,
 * never from the Canvas frame callback.
 */
class OsuMapPreviewLoader(
    private val context: Context,
    private val db: AppDatabase
) {
    suspend fun loadCatalog(mediaId: Long): OsuPreviewCatalog? = withContext(Dispatchers.IO) {
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
        OsuDifficultyMatcher.selectCatalog(
            maps = maps,
            beatmapSetId = entity.beatmapSetId,
            defaultDifficultyName = entity.difficultyName,
            playedAudioFilename = directAudioName
        )
    }

    suspend fun loadTimeline(
        catalog: OsuPreviewCatalog,
        difficultyName: String
    ): OsuRenderTimeline? = withContext(Dispatchers.IO) {
        val difficulty = catalog.difficulties.firstOrNull {
            it.difficultyName == difficultyName
        } ?: return@withContext null
        OsuRenderCache.timeline(File(difficulty.path), catalog.beatmapSetId)
            ?.takeIf {
                it.beatmapSetId == catalog.beatmapSetId &&
                    it.audioFilename == catalog.audioFilename
            }
    }

    /**
     * Header discovery is intentionally ruleset-agnostic. Unsupported maps are
     * still needed to map a playing library row back to its audio group when a
     * mixed-mode set contains several audio files. [OsuDifficultyMatcher] then
     * narrows the selected group to renderable osu!standard maps.
     *
     * Each file is handled independently so one malformed or unsupported map
     * can never reject the rest of the set.
     */
    private fun scanMaps(directory: File, expectedBeatmapSetId: Long): List<OsuDifficultyMap> {
        val maps = mutableListOf<OsuDifficultyMap>()
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
                            val header = OsuRenderCache.header(file)
                                ?: return@runCatching null
                            if (
                                !OsuBeatmapParser.belongsToBeatmapSet(
                                    header.beatmapSetId,
                                    expectedBeatmapSetId
                                )
                            ) {
                                return@runCatching null
                            }
                            OsuDifficultyMap(
                                path = canonicalPath,
                                difficultyName = header.difficultyName,
                                beatmapSetId = expectedBeatmapSetId,
                                audioFilename = header.audioFilename,
                                beatmapId = header.beatmapId,
                                mode = header.mode
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
