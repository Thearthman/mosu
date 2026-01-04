package com.mosu.app.domain.search

import android.util.Log
import com.mosu.app.data.api.model.BeatmapsetCompact
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.RecentPlayEntity
import com.mosu.app.data.repository.OsuRepository
import com.mosu.app.domain.model.toBeatmapset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap

class BeatmapSearchService(
    private val repository: OsuRepository,
    private val db: AppDatabase
) {

    val genres = listOf(
        10 to "Electronic",
        3 to "Anime",
        4 to "Rock",
        5 to "Pop",
        2 to "Game",
        9 to "Hip Hop",
        11 to "Metal",
        12 to "Classical",
        13 to "Folk",
        14 to "Jazz",
        7 to "Novelty",
        6 to "Other"
    )

    fun mergeKey(beatmap: BeatmapsetCompact): String {
        return "${beatmap.title.trim().lowercase()}|${beatmap.artist.trim().lowercase()}"
    }

    fun buildMergeGroups(list: List<BeatmapsetCompact>): Map<String, Set<Long>> {
        return list.groupBy { mergeKey(it) }.mapValues { entry -> entry.value.map { it.id }.toSet() }
    }

    fun unionMergeGroups(existing: Map<String, Set<Long>>, incoming: Map<String, Set<Long>>): Map<String, Set<Long>> {
        if (incoming.isEmpty()) return existing
        val result = existing.toMutableMap()
        incoming.forEach { (k, v) ->
            val prev = result[k]
            result[k] = if (prev == null) v else prev + v
        }
        return result
    }

    fun applyLocalFilters(list: List<BeatmapsetCompact>, query: String, selectedGenreId: Int?): List<BeatmapsetCompact> {
        val trimmedQuery = query.trim()
        return list.filter { beatmap ->
            val genreOk = selectedGenreId?.let { beatmap.genreId == it } ?: true
            val queryOk = if (trimmedQuery.isEmpty()) true else {
                beatmap.title.contains(trimmedQuery, ignoreCase = true) || beatmap.artist.contains(trimmedQuery, ignoreCase = true)
            }
            genreOk && queryOk
        }
    }

    fun dedupeByTitle(list: List<BeatmapsetCompact>, downloadedBeatmapSetIds: Set<Long>): List<BeatmapsetCompact> {
        val map = LinkedHashMap<String, BeatmapsetCompact>()
        list.forEach { beatmap ->
            val key = "${beatmap.title.trim().lowercase()}|${beatmap.artist.trim().lowercase()}"
            val existing = map[key]
            val isDownloaded = beatmap.id in downloadedBeatmapSetIds
            val existingDownloaded = existing?.id?.let { it in downloadedBeatmapSetIds } ?: false
            when {
                existing == null -> map[key] = beatmap
                !existingDownloaded && isDownloaded -> map[key] = beatmap // prefer downloaded variant
                else -> { /* keep existing */ }
            }
        }
        return map.values.toList()
    }

    fun mergeByTitle(existing: List<BeatmapsetCompact>, incoming: List<BeatmapsetCompact>, downloadedBeatmapSetIds: Set<Long>): List<BeatmapsetCompact> {
        if (incoming.isEmpty()) return existing

        val map = LinkedHashMap<String, BeatmapsetCompact>()
        fun putIfBetter(beatmap: BeatmapsetCompact) {
            val key = "${beatmap.title.trim().lowercase()}|${beatmap.artist.trim().lowercase()}"
            val existingVal = map[key]
            val isDownloaded = beatmap.id in downloadedBeatmapSetIds
            val existingDownloaded = existingVal?.id?.let { it in downloadedBeatmapSetIds } ?: false
            when {
                existingVal == null -> map[key] = beatmap
                !existingDownloaded && isDownloaded -> map[key] = beatmap
                else -> { /* keep existing */ }
            }
        }

        existing.forEach { putIfBetter(it) }
        incoming.forEach { putIfBetter(it) }

        return map.values.toList()
    }

    fun filterMetadataFor(list: List<BeatmapsetCompact>, metadata: Map<Long, Pair<Int, Int>>): Map<Long, Pair<Int, Int>> {
        if (metadata.isEmpty()) return emptyMap()
        val allowedIds = list.map { it.id }.toSet()
        return metadata.filterKeys { it in allowedIds }
    }

    suspend fun loadRecent(
        accessToken: String?,
        userId: String?,
        forceRefresh: Boolean = false
    ): Triple<List<BeatmapsetCompact>, Map<String, Set<Long>>, Map<Long, Pair<Int, Int>>> {
        // If database is empty or forceRefresh is true, fetch from API
        val recentFromDb = db.recentPlayDao().getRecentPlays()
        if ((recentFromDb.isEmpty() || forceRefresh) && accessToken != null && userId != null) {
            try {
                val recentEntities = repository.fetchRecentPlays(accessToken, userId, 100)
                db.recentPlayDao().mergeNewPlays(recentEntities)
            } catch (e: Exception) {
                // Silently fail for now, will use existing data
                Log.w("BeatmapSearchService", "Failed to fetch recent plays from API", e)
            }
        }

        val recent = db.recentPlayDao().getRecentPlays()
        val beatmaps = recent.map { it.toBeatmapset() }
        val mergeGroups = buildMergeGroups(beatmaps)

        return Triple(beatmaps, mergeGroups, emptyMap())
    }
}
