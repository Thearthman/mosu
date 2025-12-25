package com.mosu.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mosu.app.data.api.RetrofitClient
import com.mosu.app.data.api.model.BeatmapPlaycount
import com.mosu.app.data.api.model.BeatmapsetCompact
import com.mosu.app.data.api.model.OsuTokenResponse
import com.mosu.app.data.api.model.OsuUserCompact
import com.mosu.app.data.db.SearchCacheDao
import com.mosu.app.data.db.SearchCacheEntity
import com.mosu.app.data.db.RecentPlayEntity
import java.time.OffsetDateTime

class OsuRepository(private val searchCacheDao: SearchCacheDao? = null) {
    private val api = RetrofitClient.api
    private val gson = Gson()
    
    private val redirectUri = "mosu://callback"
    
    suspend fun exchangeCodeForToken(code: String, clientId: String, clientSecret: String): OsuTokenResponse {
        return api.getToken(
            clientId = clientId,
            clientSecret = clientSecret,
            code = code,
            grantType = "authorization_code",
            redirectUri = redirectUri
        )
    }

    suspend fun getMe(accessToken: String): OsuUserCompact {
        return api.getMe("Bearer $accessToken")
    }

    suspend fun getBeatmapsetDetail(accessToken: String, beatmapsetId: Long): com.mosu.app.data.api.model.BeatmapsetDetail {
        return api.getBeatmapsetDetail("Bearer $accessToken", beatmapsetId)
    }

    suspend fun getUserMostPlayed(accessToken: String, userId: String): List<BeatmapPlaycount> {
        return api.getUserMostPlayed("Bearer $accessToken", userId)
    }

    suspend fun getRecentPlayedBeatmaps(
        accessToken: String,
        userId: String,
        limit: Int = 100
    ): List<BeatmapsetCompact> {
        val recentScores = api.getUserRecentScores("Bearer $accessToken", userId, limit)
        // Keep order as returned (newest first), de-dup by beatmapset id, filter last 7 days
        val seen = mutableSetOf<Long>()
        val ordered = mutableListOf<BeatmapsetCompact>()
        for (score in recentScores) {
            val beatmapset = score.beatmapset ?: continue
            if (seen.add(beatmapset.id)) {
                ordered.add(beatmapset)
            }
        }
        return ordered
    }

    suspend fun fetchRecentPlays(
        accessToken: String,
        userId: String,
        limit: Int = 100
    ): List<RecentPlayEntity> {
        val recentScores = api.getUserRecentScores("Bearer $accessToken", userId, limit, includeFails = 1)
        val seen = mutableSetOf<Long>()
        val entities = mutableListOf<RecentPlayEntity>()
        for (score in recentScores) {
            val playedAt = score.createdAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: continue
            val beatmapset = score.beatmapset ?: continue
            if (!seen.add(beatmapset.id)) continue
            entities.add(
                RecentPlayEntity(
                    scoreId = score.scoreId,
                    beatmapSetId = beatmapset.id,
                    title = beatmapset.title,
                    artist = beatmapset.artist,
                    creator = beatmapset.creator,
                    coverUrl = beatmapset.covers.coverUrl,
                    playedAt = playedAt.toInstant().toEpochMilli()
                )
            )
        }
        return entities
    }

    data class PlayedBeatmapsResult(
        val beatmaps: List<BeatmapsetCompact>,
        val cursor: String?,
        val metadata: Map<Long, Pair<Int, Int>> = emptyMap() // beatmapId -> (rank, playcount) for most_played
    )
    
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
        forceRefresh: Boolean = false
    ): PlayedBeatmapsResult {
        val safeQuery = sanitizeQuery(searchQuery)

        if (filterMode == "favorite" && userId != null && cursorString != "cache") {
            val offset = cursorString?.toIntOrNull() ?: 0
            val limit = 50
            val favourites = api.getUserFavoriteBeatmapsets(
                authHeader = "Bearer $accessToken",
                userId = userId,
                limit = limit,
                offset = offset
            )
            val filtered = if (!safeQuery.isNullOrEmpty()) {
                favourites.filter {
                    it.title.contains(safeQuery, ignoreCase = true) ||
                            it.artist.contains(safeQuery, ignoreCase = true)
                }
            } else favourites
            val nextCursor = if (favourites.size < limit) null else (offset + limit).toString()
            return PlayedBeatmapsResult(filtered, nextCursor)
        }

        // Explicit most_played view
        if (filterMode == "most_played" && userId != null && cursorString == null) {
            val mostPlayedData = api.getUserMostPlayed("Bearer $accessToken", userId, limit = 100)

            val groupedData = mostPlayedData.groupBy { it.beatmapset.title }
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
            val metadata = mutableMapOf<Long, Pair<Int, Int>>()
            sortedData.forEachIndexed { index, item ->
                metadata[item.beatmapset.id] = Pair(index + 1, item.count)
            }
            val beatmaps = sortedData.map { it.beatmapset }
            val searchFiltered = if (!searchQuery.isNullOrEmpty()) {
                beatmaps.filter {
                    val query = safeQuery ?: return@filter true
                    it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true)
                }
            } else {
                beatmaps
            }
            return PlayedBeatmapsResult(searchFiltered, null, metadata)
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
            val mostPlayedData = api.getUserMostPlayed("Bearer $accessToken", userId, limit = 100) // Get top 100
            
            // Group by song title (to combine same songs from different mappers + different difficulties)
            val groupedData = mostPlayedData.groupBy { it.beatmapset.title }
            val deduplicatedData = groupedData.map { (title, items) ->
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
            val metadata = mutableMapOf<Long, Pair<Int, Int>>()
            sortedData.forEachIndexed { index, item ->
                metadata[item.beatmapset.id] = Pair(index + 1, item.count) // rank (1-based), summed playcount
            }
            
            val beatmaps = sortedData.map { it.beatmapset }
            
            // Genre filter is NOT applied for most_played (UI should hide it)
            // Apply search query filter if specified
            val searchFiltered = if (!searchQuery.isNullOrEmpty()) {
                beatmaps.filter { 
                    val query = safeQuery ?: return@filter true
                    it.title.contains(query, ignoreCase = true) || 
                    it.artist.contains(query, ignoreCase = true)
                }
            } else {
                beatmaps
            }
            
            return PlayedBeatmapsResult(searchFiltered, null, metadata) // No cursor for most played
        }
        
        // Otherwise use URL-based filtering (original logic)
        // Generate cache key (only cache first page without search query)
        val cacheKey = "played_genre_${genreId ?: "all"}_query_${safeQuery ?: "none"}_mode_${filterMode}_playedMode_${playedFilterMode}_initial"
        
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
        val response = api.searchBeatmapsets(
            authHeader = "Bearer $accessToken",
            played = if (filterMode == "played") "played" else null,
            genre = genreId,
            cursorString = cursorString,
            query = safeQuery,
            status = when {
                filterMode == "favorite" -> "favourites"
                searchAny -> "any"
                else -> null
            }
        )
        
        // Save to cache only for initial load without search
        if (cursorString == null && safeQuery.isNullOrEmpty()) {
            searchCacheDao?.let {
                val existing = it.getCachedResult(cacheKey)
                val existingList = existing?.resultsJson?.let { json ->
                    val type = object : TypeToken<List<BeatmapsetCompact>>() {}.type
                    runCatching { gson.fromJson<List<BeatmapsetCompact>>(json, type) }.getOrDefault(emptyList())
                } ?: emptyList()
                val toStore = if (filterMode == "played") {
                    // Merge while keeping existing order, appending only new ids
                    val seenIds = existingList.map { bm -> bm.id }.toMutableSet()
                    val merged = existingList.toMutableList()
                    response.beatmapsets.forEach { bm ->
                        if (seenIds.add(bm.id)) merged.add(bm)
                    }
                    merged
                } else {
                    // Preserve API order for other modes (e.g., favorite)
                    response.beatmapsets
                }

                it.insertCache(SearchCacheEntity(
                    queryKey = cacheKey,
                    resultsJson = gson.toJson(toStore),
                    cursorString = response.cursorString // Cache the cursor too!
                ))
            }
        }
        
        return PlayedBeatmapsResult(response.beatmapsets, response.cursorString)
    }

    private fun sanitizeQuery(query: String?): String? {
        val trimmed = query?.trim() ?: return null
        if (trimmed.isEmpty()) return null
        val noControl = trimmed.replace(Regex("[\\p{Cntrl}]"), "")
        val cleaned = buildString {
            noControl.forEach { ch ->
                when {
                    ch.isLetterOrDigit() -> append(ch)
                    ch.isWhitespace() -> append(' ')
                    ch in listOf('-', '_', '.', ',', '\'', '"', '/', ':', ';', '!', '?', '(', ')', '[', ']', '{', '}', '+', '@', '#') -> append(ch)
                    else -> append(' ')
                }
            }
        }
        val collapsedSpaces = cleaned.replace(Regex("\\s+"), " ").trim()
        return collapsedSpaces.ifEmpty { null }
    }
}

