package com.mosu.app.data.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mosu.app.data.api.RetrofitClient
import com.mosu.app.data.api.model.BeatmapPlaycount
import com.mosu.app.data.api.model.BeatmapsetCompact
import com.mosu.app.data.api.model.BestScore
import com.mosu.app.data.api.model.OsuTokenResponse
import com.mosu.app.data.api.model.OsuUserCompact
import com.mosu.app.data.api.model.RecentScore
import com.mosu.app.data.api.model.SearchResponse
import com.mosu.app.data.api.model.SayobotSearchResponse
import com.mosu.app.data.db.SearchCacheDao
import com.mosu.app.data.db.SearchCacheEntity
import com.mosu.app.data.db.RecentPlayEntity
import com.mosu.app.domain.model.sortedByTotalBeatmapPlayCountDescending
import java.time.OffsetDateTime

class OsuRepository(private val searchCacheDao: SearchCacheDao? = null) {
    private val gson = Gson()
    
    private val redirectUri = "mosu://callback"
    
    suspend fun exchangeCodeForToken(code: String, clientId: String, clientSecret: String): OsuTokenResponse {
        return RetrofitClient.api.getToken(
            clientId = clientId,
            clientSecret = clientSecret,
            code = code,
            grantType = "authorization_code",
            redirectUri = redirectUri
        )
    }

    suspend fun getMe(): OsuUserCompact {
        return RetrofitClient.api.getMe("")
    }

    suspend fun getBeatmapsetDetail(beatmapsetId: Long): com.mosu.app.data.api.model.BeatmapsetDetail {
        return RetrofitClient.api.getBeatmapsetDetail("", beatmapsetId)
    }

    suspend fun searchBeatmapsetsByTitleArtist(
        title: String,
        artist: String,
        scoreMode: String = "all"
    ): List<BeatmapsetCompact> {
        // Search using title and artist as query
        val query = "\"$title\" \"$artist\""
        val normalizedMode = scoreMode.normalizedScoreMode()

        val response = RetrofitClient.api.searchBeatmapsets(
            authHeader = "", // No auth needed for public search
            query = query,
            mode = normalizedMode.toSearchModeQuery(),
            status = "any" // Include ranked, loved, qualified, etc.
        )
        val beatmapsets = response.beatmapsets

        // Filter results to ensure exact title/artist matches (case-insensitive, trimmed)
        val targetTitle = title.trim().lowercase()
        val targetArtist = artist.trim().lowercase()

        return beatmapsets.filter { beatmapset ->
            beatmapset.title.trim().lowercase() == targetTitle &&
            beatmapset.artist.trim().lowercase() == targetArtist &&
            beatmapset.hasMode(normalizedMode)
        }
    }

    suspend fun findMostPlayedBeatmapsetByTitleArtist(title: String, artist: String): BeatmapsetCompact? {
        return searchBeatmapsetsByTitleArtist(title, artist)
            .sortedByTotalBeatmapPlayCountDescending()
            .firstOrNull()
    }

    suspend fun getUserMostPlayed(accessToken: String, userId: String): List<BeatmapPlaycount> {
        return RetrofitClient.api.getUserMostPlayed("Bearer $accessToken", userId)
    }

    private val recentPlayModes = listOf("osu", "taiko", "fruits", "mania")

    suspend fun fetchRecentPlays(
        accessToken: String,
        userId: String,
        limit: Int = 100
    ): List<RecentPlayEntity> {
        val allRecentScores = mutableListOf<RecentScore>()
        for (mode in recentPlayModes) {
            val modeScores =
                RetrofitClient.api.getUserRecentScores("Bearer $accessToken", userId, limit, includeFails = 1, mode)
            Log.d("OsuRepository", "fetchRecentPlays mode=$mode returned=${modeScores.size}")
            allRecentScores.addAll(modeScores)
        }
        val entities = mutableListOf<RecentPlayEntity>()
        for (score in allRecentScores) {
            val playedAt = score.createdAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: continue
            if (score.beatmapset == null) continue
            entities.add(score.toRecentPlayEntity(playedAt.toInstant().toEpochMilli()))
        }
        Log.d(
            "OsuRepository",
            "fetchRecentPlays mapped=${entities.size}, withRank=${entities.count { it.rank != null }}, " +
                "withStars=${entities.count { it.starRating != null }}, withPp=${entities.count { it.pp != null }}, " +
                "withBeatmapId=${entities.count { it.beatmapId != null }}"
        )
        return entities.sortedByDescending { it.playedAt }
    }

    private fun RecentScore.toRecentPlayEntity(playedAtMillis: Long): RecentPlayEntity {
        val set = beatmapset ?: error("Recent score missing beatmapset")
        val selectedBeatmap = beatmap
        val fallbackBeatmap = selectedBeatmap?.id?.let { id -> set.beatmaps.orEmpty().firstOrNull { it.id == id } }
        return RecentPlayEntity(
            scoreId = scoreId,
            beatmapId = selectedBeatmap?.id ?: fallbackBeatmap?.id,
            beatmapSetId = set.id,
            title = set.title,
            artist = set.artist,
            creator = set.creator,
            difficultyName = selectedBeatmap?.version ?: fallbackBeatmap?.version,
            mode = selectedBeatmap?.mode ?: fallbackBeatmap?.mode,
            coverUrl = set.covers.coverUrl,
            genreId = set.genreId,
            rank = rank,
            pp = pp,
            accuracy = accuracy,
            maxCombo = maxCombo,
            passed = passed,
            starRating = selectedBeatmap?.difficultyRating ?: fallbackBeatmap?.difficultyRating,
            lengthSeconds = selectedBeatmap?.totalLength ?: fallbackBeatmap?.totalLength,
            hitLengthSeconds = selectedBeatmap?.hitLength ?: fallbackBeatmap?.hitLength,
            bpm = selectedBeatmap?.bpm ?: fallbackBeatmap?.bpm,
            ar = selectedBeatmap?.ar ?: fallbackBeatmap?.ar,
            cs = selectedBeatmap?.cs ?: fallbackBeatmap?.cs,
            drain = selectedBeatmap?.drain ?: fallbackBeatmap?.drain,
            playedAt = playedAtMillis
        )
    }

    data class PlayedBeatmapsResult(
        val beatmaps: List<BeatmapsetCompact>,
        val cursor: String?,
        val metadata: Map<Long, Pair<Int, Int?>> = emptyMap(), // beatmapsetId -> (rank, optional playcount)
        val scoreResults: Map<Long, BestScore> = emptyMap() // beatmapsetId -> best score details
    )
    
    suspend fun getCachedPlayedBeatmaps(
        genreId: Int? = null,
        searchQuery: String? = null,
        filterMode: String = "played",
        playedFilterMode: String = "url",
        userId: String? = null,
        searchAny: Boolean = false,
        scoreMode: String = "all"
    ): PlayedBeatmapsResult? {
        val safeQuery = sanitizeQuery(searchQuery)
        val normalizedMode = scoreMode.normalizedScoreMode()

        if (filterMode == "top_play") {
            if (safeQuery != null || userId == null) return null
            val cached = searchCacheDao?.getCachedResult(
                topPlayCacheKey(userId, genreId, normalizedMode)
            ) ?: return null
            return runCatching {
                val payload = gson.fromJson(cached.resultsJson, TopPlayCachePayload::class.java)
                PlayedBeatmapsResult(
                    beatmaps = payload.entries.map { it.beatmapset },
                    cursor = null,
                    metadata = payload.entries.associate {
                        it.beatmapset.id to Pair(it.rank, null)
                    },
                    scoreResults = payload.entries.associate {
                        it.beatmapset.id to it.score
                    }
                )
            }.getOrNull()
        }

        // Generate appropriate cache key based on filter mode
        val cacheKey = when (filterMode) {
            "favorite" -> "favorite_user_${userId}_ruleset_${normalizedMode}_offset_0"
            "most_played" -> "most_played_user_${userId}_ruleset_${normalizedMode}"
            "played" -> "played_genre_${genreId ?: "all"}_query_${safeQuery ?: "none"}_mode_${filterMode}_playedMode_${playedFilterMode}_searchAny_${searchAny}_ruleset_${normalizedMode}_initial"
            else -> "search_genre_${genreId ?: "all"}_query_${safeQuery ?: "none"}_mode_${filterMode}_searchAny_${searchAny}_ruleset_${normalizedMode}_initial"
        }

        // Check cache for initial load conditions (no cursor/query for most modes)
        if (safeQuery.isNullOrEmpty()) {
            val cached = searchCacheDao?.getCachedResult(cacheKey)
            if (cached != null) {
                val type = object : TypeToken<List<BeatmapsetCompact>>() {}.type
                val results: List<BeatmapsetCompact> = gson.fromJson(cached.resultsJson, type)
                return PlayedBeatmapsResult(results, cached.cursorString)
            }
        }
        return null
    }

    suspend fun getPlayedBeatmaps(
        accessToken: String,
        genreId: Int? = null,
        cursorString: String? = null,
        searchQuery: String? = null,
        filterMode: String = "played",
        playedFilterMode: String = "url",
        userId: String? = null,
        isSupporter: Boolean = true, // Assume supporter unless specified
        searchAny: Boolean = false,
        forceRefresh: Boolean = false,
        scoreMode: String = "all"
    ): PlayedBeatmapsResult {
        val safeQuery = sanitizeQuery(searchQuery)
        val normalizedMode = scoreMode.normalizedScoreMode()

        if (filterMode == "top_play") {
            if (cursorString != null || userId == null) {
                return PlayedBeatmapsResult(emptyList(), null)
            }
            if (!forceRefresh) {
                getCachedPlayedBeatmaps(
                    genreId = genreId,
                    searchQuery = safeQuery,
                    filterMode = filterMode,
                    playedFilterMode = playedFilterMode,
                    userId = userId,
                    searchAny = searchAny,
                    scoreMode = normalizedMode
                )?.let { return it }
            }
            return getTopPlayBeatmaps(
                accessToken = accessToken,
                userId = userId,
                genreId = genreId,
                safeQuery = safeQuery,
                scoreMode = normalizedMode
            )
        }

        // Explicit most_played view
        if (filterMode == "most_played" && userId != null && cursorString == null) {
            val mostPlayedData = RetrofitClient.api.getUserMostPlayed("Bearer $accessToken", userId, limit = 100)
            val modeFilteredData = filterMostPlayedByMode(mostPlayedData, normalizedMode)

            val groupedData = modeFilteredData.groupBy { it.beatmapset.title }
            val deduplicatedData = groupedData.map { (_, items) ->
                val firstItem = items.first()
                val totalPlaycount = items.sumOf { it.count }
                BeatmapPlaycount(
                    beatmapId = firstItem.beatmapId,
                    count = totalPlaycount,
                    beatmapset = firstItem.beatmapset
                )
            }

            val sortedData = deduplicatedData.sortedByDescending { it.count }
            val metadata = mutableMapOf<Long, Pair<Int, Int?>>()
            sortedData.forEachIndexed { index, item ->
                metadata[item.beatmapset.id] = Pair(index + 1, item.count)
            }
            val beatmaps = sortedData.map { it.beatmapset }
            val filteredBySearch = if (!searchQuery.isNullOrEmpty()) {
                beatmaps.filter {
                    val query = safeQuery ?: return@filter true
                    it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true)
                }
            } else {
                beatmaps
            }

            val filteredByGenre = if (genreId != null) {
                filteredBySearch.filter { it.genreId == genreId }
            } else filteredBySearch

            return PlayedBeatmapsResult(filteredByGenre, null, metadata)
        }

        // Auto-fallback: If non-supporter tries "played" filter with URL mode, use most_played endpoint
        val effectivePlayedMode = if (filterMode == "played" && playedFilterMode == "url" && !isSupporter) {
            "most_played" // Force most_played for non-supporters
        } else {
            playedFilterMode
        }
        
        // If filterMode is "played" and effective mode is "most_played", use getUserMostPlayed
        if (filterMode == "played" && effectivePlayedMode == "most_played" && userId != null && cursorString == null) {
            // Use most played data - note: this doesn't support pagination/cursor
            val mostPlayedData = RetrofitClient.api.getUserMostPlayed("Bearer $accessToken", userId, limit = 100) // Get top 100
            val modeFilteredData = filterMostPlayedByMode(mostPlayedData, normalizedMode)
            
            // Group by song title (to combine same songs from different mappers + different difficulties)
            val groupedData = modeFilteredData.groupBy { it.beatmapset.title }
            val deduplicatedData = groupedData.map { (_, items) ->
                val firstItem = items.first() // Keep first (highest individual rank) for beatmapset data
                val totalPlaycount = items.sumOf { it.count } // Sum all playcounts across all difficulties and mappers
                BeatmapPlaycount(
                    beatmapId = firstItem.beatmapId,
                    count = totalPlaycount,
                    beatmapset = firstItem.beatmapset
                )
            }
            
            // Sort by total playcount descending
            val sortedData = deduplicatedData.sortedByDescending { it.count }
            
            // Build metadata map with ranking and summed playcount
            val metadata = mutableMapOf<Long, Pair<Int, Int?>>()
            sortedData.forEachIndexed { index, item ->
                metadata[item.beatmapset.id] = Pair(index + 1, item.count) // rank (1-based), summed playcount
            }
            
            val beatmaps = sortedData.map { it.beatmapset }
            
            // Apply search query filter if specified
            val filteredBySearch = if (!searchQuery.isNullOrEmpty()) {
                beatmaps.filter { 
                    val query = safeQuery ?: return@filter true
                    it.title.contains(query, ignoreCase = true) || 
                    it.artist.contains(query, ignoreCase = true)
                }
            } else {
                beatmaps
            }

            val filteredByGenre = if (genreId != null) {
                filteredBySearch.filter { it.genreId == genreId }
            } else filteredBySearch
            
            return PlayedBeatmapsResult(filteredByGenre, null, metadata) // No cursor for most played
        }
        
        // Otherwise use URL-based filtering (original logic)
        // Generate cache key (only cache first page without search query)
        val cacheKey = "played_genre_${genreId ?: "all"}_query_${safeQuery ?: "none"}_mode_${filterMode}_playedMode_${playedFilterMode}_searchAny_${searchAny}_ruleset_${normalizedMode}_initial"
        
        // Only use cache for initial load (no cursor) without search query
        if (cursorString == null && safeQuery.isNullOrEmpty() && !forceRefresh) {
            val cached = searchCacheDao?.getCachedResult(cacheKey)
            if (cached != null) {
                // Cache hit and fresh
                val type = object : TypeToken<List<BeatmapsetCompact>>() {}.type
                val results: List<BeatmapsetCompact> = gson.fromJson(cached.resultsJson, type)
                // Return cached results WITH cached cursor
                return PlayedBeatmapsResult(results, cached.cursorString)
            }
        }
        
        // Fetch from API
        val response = RetrofitClient.api.searchBeatmapsets(
            authHeader = "Bearer $accessToken",
            played = if (filterMode == "played") "played" else null,
            genre = genreId,
            mode = normalizedMode.toSearchModeQuery(),
            cursorString = cursorString,
            query = safeQuery,
            status = when {
                filterMode == "favorite" -> "favourites"
                searchAny -> "any"
                else -> null
            }
        )
        
        val responseBeatmapsets = response.beatmapsets.filter { it.hasMode(normalizedMode) }

        // Populate the cache on first load and replace it only on explicit refresh.
        if (cursorString == null && safeQuery.isNullOrEmpty()) {
            searchCacheDao?.let {
                val existing = it.getCachedResult(cacheKey)
                if (forceRefresh || existing == null) {
                    val existingList = existing?.resultsJson?.let { json ->
                        val type = object : TypeToken<List<BeatmapsetCompact>>() {}.type
                        runCatching { gson.fromJson<List<BeatmapsetCompact>>(json, type) }.getOrDefault(emptyList())
                    } ?: emptyList()
                    val toStore = if (filterMode == "played") {
                        // Merge while keeping existing order, appending only new ids
                        val seenIds = existingList.map { bm -> bm.id }.toMutableSet()
                        val merged = existingList.toMutableList()
                        responseBeatmapsets.forEach { bm ->
                            if (seenIds.add(bm.id)) merged.add(bm)
                        }
                        merged
                    } else {
                        // Preserve API order for other modes (e.g., favorite)
                        responseBeatmapsets
                    }

                    it.insertCache(SearchCacheEntity(
                        queryKey = cacheKey,
                        resultsJson = gson.toJson(toStore),
                        cursorString = response.cursorString // Cache the cursor too!
                    ))
                }
            }
        }
        
        return PlayedBeatmapsResult(responseBeatmapsets, response.cursorString)
    }

    private suspend fun getTopPlayBeatmaps(
        accessToken: String,
        userId: String,
        genreId: Int?,
        safeQuery: String?,
        scoreMode: String
    ): PlayedBeatmapsResult {
        val rulesets = if (scoreMode == "all") scoreModes else listOf(scoreMode)
        val scores = mutableListOf<BestScore>()

        rulesets.forEach { ruleset ->
            var offset = 0
            while (offset < TOP_PLAY_LIMIT) {
                val page = RetrofitClient.api.getUserBestScores(
                    authHeader = "Bearer $accessToken",
                    userId = userId,
                    mode = ruleset,
                    limit = TOP_PLAY_PAGE_SIZE,
                    offset = offset,
                    legacyOnly = 0
                )
                scores.addAll(page)
                if (page.size < TOP_PLAY_PAGE_SIZE) break
                offset += TOP_PLAY_PAGE_SIZE
            }
        }

        val bestScoreBySet = LinkedHashMap<Long, BestScore>()
        scores.forEach { score ->
            val beatmapset = score.beatmapset ?: return@forEach
            val pp = score.pp ?: return@forEach
            val existing = bestScoreBySet[beatmapset.id]
            if (existing == null || pp > (existing.pp ?: Float.NEGATIVE_INFINITY)) {
                bestScoreBySet[beatmapset.id] = score
            }
        }

        val rankedScores = bestScoreBySet.values
            .sortedByDescending { it.pp ?: Float.NEGATIVE_INFINITY }
            .take(TOP_PLAY_LIMIT)

        val metadata = rankedScores.mapIndexed { index, score ->
            val beatmapset = score.beatmapset ?: error("Ranked top play missing beatmapset")
            beatmapset.id to Pair(index + 1, null)
        }.toMap()

        val filteredBeatmapsets = rankedScores
            .mapNotNull { it.beatmapset }
            .filter { beatmapset ->
                val genreMatches = genreId == null || beatmapset.genreId == genreId
                val queryMatches = safeQuery == null ||
                    beatmapset.title.contains(safeQuery, ignoreCase = true) ||
                    beatmapset.artist.contains(safeQuery, ignoreCase = true)
                genreMatches && queryMatches
            }

        val filteredIds = filteredBeatmapsets.map { it.id }.toSet()
        val scoreResults = rankedScores
            .mapNotNull { score -> score.beatmapset?.let { it.id to score } }
            .filter { (setId, _) -> setId in filteredIds }
            .toMap()

        if (safeQuery == null) {
            val cacheEntries = filteredBeatmapsets.mapNotNull { beatmapset ->
                val score = scoreResults[beatmapset.id] ?: return@mapNotNull null
                val rank = metadata[beatmapset.id]?.first ?: return@mapNotNull null
                TopPlayCacheEntry(
                    beatmapset = beatmapset,
                    score = score,
                    rank = rank
                )
            }
            searchCacheDao?.insertCache(
                SearchCacheEntity(
                    queryKey = topPlayCacheKey(userId, genreId, scoreMode),
                    resultsJson = gson.toJson(TopPlayCachePayload(cacheEntries))
                )
            )
        }

        return PlayedBeatmapsResult(filteredBeatmapsets, null, metadata, scoreResults)
    }

    private fun filterMostPlayedByMode(
        playcounts: List<BeatmapPlaycount>,
        scoreMode: String
    ): List<BeatmapPlaycount> {
        if (scoreMode == "all") return playcounts
        return playcounts.filter { playcount ->
            val mode = playcount.beatmap?.mode
                ?: playcount.beatmapset.beatmaps.firstOrNull { it.id == playcount.beatmapId }?.mode
            mode.matchesScoreMode(scoreMode)
        }
    }

    private fun sanitizeQuery(query: String?): String? {
        val trimmed = query?.trim() ?: return null
        if (trimmed.isEmpty()) return null
        // Remove control characters that could cause issues
        val noControl = trimmed.replace(Regex("[\\p{Cntrl}]"), "")
        // Normalize whitespace
        val collapsedSpaces = noControl.replace(Regex("\\s+"), " ").trim()
        return collapsedSpaces.ifEmpty { null }
    }

    private fun topPlayCacheKey(userId: String, genreId: Int?, scoreMode: String): String {
        return "top_play_user_${userId}_genre_${genreId ?: "all"}_ruleset_${scoreMode}_initial"
    }

    companion object {
        private const val TOP_PLAY_LIMIT = 200
        private const val TOP_PLAY_PAGE_SIZE = 100
        private val scoreModes = listOf("osu", "taiko", "fruits", "mania")
    }
}

private data class TopPlayCachePayload(
    val entries: List<TopPlayCacheEntry> = emptyList()
)

private data class TopPlayCacheEntry(
    val beatmapset: BeatmapsetCompact,
    val score: BestScore,
    val rank: Int
)

private fun String.normalizedScoreMode(): String {
    val normalized = lowercase()
    return if (normalized in listOf("osu", "taiko", "fruits", "mania", "all")) normalized else "all"
}

private fun String.toSearchModeQuery(): Int? {
    return when (this) {
        "osu" -> 0
        "taiko" -> 1
        "fruits" -> 2
        "mania" -> 3
        else -> null
    }
}

private fun BeatmapsetCompact.hasMode(scoreMode: String): Boolean {
    if (scoreMode == "all") return true
    return beatmaps.any { it.mode.matchesScoreMode(scoreMode) }
}

private fun String?.matchesScoreMode(scoreMode: String): Boolean {
    if (scoreMode == "all") return true
    if (this == null) return false
    return when (scoreMode) {
        "fruits" -> equals("fruits", ignoreCase = true) || equals("catch", ignoreCase = true)
        else -> equals(scoreMode, ignoreCase = true)
    }
}
