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
    val audioFilename: String
)

data class OsuPreviewCatalog(
    val beatmapSetId: Long,
    val audioFilename: String,
    val difficulties: List<OsuDifficultyMap>,
    val defaultDifficultyName: String
)

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

        val validMaps = try {
            directory.walkTopDown()
                .filter { it.isFile && it.extension.equals("osu", ignoreCase = true) }
                .mapNotNull { file ->
                    val canonicalPath = runCatching { file.canonicalPath }.getOrNull()
                        ?: return@mapNotNull null
                    val header = runCatching { OsuRenderCache.header(file) }.getOrNull()
                        ?: return@mapNotNull null
                    if (
                        header.mode != 0 ||
                        header.beatmapSetId != entity.beatmapSetId
                    ) {
                        return@mapNotNull null
                    }
                    OsuDifficultyMap(
                        path = canonicalPath,
                        difficultyName = header.difficultyName,
                        beatmapSetId = entity.beatmapSetId,
                        audioFilename = header.audioFilename
                    )
                }
                .toList()
        } catch (_: Exception) {
            return@withContext null
        }
        if (validMaps.isEmpty()) return@withContext null

        val directAudioName = entity.audioPath
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { Uri.parse(it).lastPathSegment }.getOrNull() }
        OsuDifficultyMatcher.selectCatalog(
            maps = validMaps,
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
}
