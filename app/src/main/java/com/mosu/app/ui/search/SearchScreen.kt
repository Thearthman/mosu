package com.mosu.app.ui.search

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mosu.app.R
import com.mosu.app.data.api.model.Covers
import com.mosu.app.data.api.model.BeatmapsetCompact
import com.mosu.app.data.api.model.BeatmapDetail
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.RecentPlayEntity
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.data.repository.OsuRepository
import com.mosu.app.domain.download.BeatmapDownloader
import com.mosu.app.domain.download.DownloadState
import com.mosu.app.domain.download.ZipExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

data class DownloadProgress(
    val progress: Int, // 0-100
    val status: String // "Downloading", "Extracting", "Done", "Error"
)

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    repository: OsuRepository,
    db: AppDatabase,
    accessToken: String?,
    settingsManager: com.mosu.app.data.SettingsManager,
    musicController: com.mosu.app.player.MusicController,
    scrollToTop: Boolean = false,
    onScrolledToTop: () -> Unit = {}
) {
    
    // Search Query
    var searchQuery by remember { mutableStateOf("") }
    
    // Played filter mode from settings
    val defaultSearchView by settingsManager.defaultSearchView.collectAsState(initial = "played")
    val playedFilterMode by settingsManager.playedFilterMode.collectAsState(initial = "url")
    val searchAnyEnabled by settingsManager.searchAnyEnabled.collectAsState(initial = false)
    var isSupporterKnown by remember { mutableStateOf(false) }
    
    var filterMode by remember { mutableStateOf(defaultSearchView) }
    var userId by remember { mutableStateOf<String?>(null) }
    var isSupporter by remember { mutableStateOf(false) } // Default to false (safer for non-supporters)
    
    // Sync filter with default view once user info is available
    LaunchedEffect(defaultSearchView, isSupporter, isSupporterKnown) {
        if (!isSupporterKnown) return@LaunchedEffect
        val allowed = if (isSupporter) {
            listOf("played", "recent", "favorite", "most_played", "all")
        } else {
            listOf("recent", "favorite", "most_played", "all")
        }
        val target = if (defaultSearchView in allowed) defaultSearchView else if (isSupporter) "played" else "favorite"
        filterMode = target
    }
    
    // Search Results
    var searchResults by remember { mutableStateOf<List<BeatmapsetCompact>>(emptyList()) }
    var searchResultsMetadata by remember { mutableStateOf<Map<Long, Pair<Int, Int>>>(emptyMap()) } // beatmapId -> (rank, playcount)
    var selectedGenreId by remember { mutableStateOf<Int?>(null) }
    var currentCursor by remember { mutableStateOf<String?>(null) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    var flicking by remember { mutableStateOf(false) }
    
    // Download States Map (BeatmapSetId -> DownloadProgress)
    var downloadStates by remember { mutableStateOf<Map<Long, DownloadProgress>>(emptyMap()) }
    
    // Downloaded BeatmapSet IDs (from database)
    var downloadedBeatmapSetIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var mergeGroups by remember { mutableStateOf<Map<String, Set<Long>>>(emptyMap()) } // key -> setIds
    
    // Load downloaded beatmap IDs from database
    LaunchedEffect(Unit) {
        db.beatmapDao().getAllBeatmaps().collect { beatmaps ->
            downloadedBeatmapSetIds = beatmaps.map { it.beatmapSetId }.toSet()
        }
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val downloader = remember { BeatmapDownloader(context) }
    val extractor = remember { ZipExtractor(context) }
    val uriHandler = LocalUriHandler.current
    val infoCoverEnabled by settingsManager.infoCoverEnabled.collectAsState(initial = true)

    var infoDialogVisible by remember { mutableStateOf(false) }
    var infoLoading by remember { mutableStateOf(false) }
    var infoError by remember { mutableStateOf<String?>(null) }
    var infoTarget by remember { mutableStateOf<BeatmapsetCompact?>(null) }
    var infoBeatmaps by remember { mutableStateOf<List<BeatmapDetail>>(emptyList()) }
    var infoMergedSetIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var infoSetCreators by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

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

    suspend fun downloadFallbackCoverImage(beatmapSetId: Long, coverUrl: String): String? {
        if (coverUrl.isBlank()) return null

        return withContext(Dispatchers.IO) {
            try {
                val outputDir = File(context.getExternalFilesDir(null), "beatmaps/$beatmapSetId")
                if (!outputDir.exists()) outputDir.mkdirs()

                val target = File(outputDir, "cover_api.jpg")
                URL(coverUrl).openStream().use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
                target.absolutePath
            } catch (e: Exception) {
                Log.w("SearchScreen", "Failed to download fallback cover", e)
                null
            }
        }
    }
    
    // Scroll state for collapsing header
    val listState = rememberLazyListState()
    
    // Scroll to top when requested
    LaunchedEffect(scrollToTop) {
        if (scrollToTop) {
            listState.animateScrollToItem(0)
            onScrolledToTop()
        }
    }

    val genres = listOf(
        10 to stringResource(id = R.string.genre_electronic),
        3 to stringResource(id = R.string.genre_anime),
        4 to stringResource(id = R.string.genre_rock),
        5 to stringResource(id = R.string.genre_pop),
        2 to stringResource(id = R.string.genre_game),
        9 to stringResource(id = R.string.genre_hiphop),
        11 to stringResource(id = R.string.genre_metal),
        12 to stringResource(id = R.string.genre_classical),
        13 to stringResource(id = R.string.genre_folk),
        14 to stringResource(id = R.string.genre_jazz),
        7 to stringResource(id = R.string.genre_novelty),
        6 to stringResource(id = R.string.genre_other)
    )

    fun applyLocalFilters(list: List<BeatmapsetCompact>): List<BeatmapsetCompact> {
        val query = searchQuery.trim()
        return list.filter { beatmap ->
            val genreOk = selectedGenreId?.let { beatmap.genreId == it } ?: true
            val queryOk = if (query.isEmpty()) true else {
                beatmap.title.contains(query, ignoreCase = true) || beatmap.artist.contains(query, ignoreCase = true)
            }
            genreOk && queryOk
        }
    }

    fun dedupeByTitle(list: List<BeatmapsetCompact>): List<BeatmapsetCompact> {
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

    fun mergeByTitle(existing: List<BeatmapsetCompact>, incoming: List<BeatmapsetCompact>): List<BeatmapsetCompact> {
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

        val merged = map.values.toList()
        if (downloadedBeatmapSetIds.isEmpty()) return merged
        val (downloaded, others) = merged.partition { it.id in downloadedBeatmapSetIds }
        return downloaded + others
    }


    fun filterMetadataFor(list: List<BeatmapsetCompact>, metadata: Map<Long, Pair<Int, Int>>): Map<Long, Pair<Int, Int>> {
        if (metadata.isEmpty()) return emptyMap()
        val allowedIds = list.map { it.id }.toSet()
        return metadata.filterKeys { it in allowedIds }
    }

    suspend fun loadRecent(forceRefresh: Boolean = false) {
        // If database is empty or forceRefresh is true, fetch from API
        val recentFromDb = db.recentPlayDao().getRecentPlays()
        if ((recentFromDb.isEmpty() || forceRefresh) && accessToken != null && userId != null) {
            try {
                val recentEntities = repository.fetchRecentPlays(accessToken, userId!!, 100)
                db.recentPlayDao().mergeNewPlays(recentEntities)
            } catch (e: Exception) {
                // Silently fail for now, will use existing data
            }
        }
        
        val recent = db.recentPlayDao().getRecentPlays()
        val applied = applyLocalFilters(recent.map { it.toBeatmapset() })
        mergeGroups = buildMergeGroups(applied)
        searchResults = dedupeByTitle(applied)
        currentCursor = null
        searchResultsMetadata = emptyMap()
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(id = R.string.search_title),
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
        )

        if (accessToken == null) {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(id = R.string.search_not_logged), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(id = R.string.search_config_prompt),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LaunchedEffect(refreshTick, isRefreshing) {
                flicking = if (isRefreshing) true else false
            }

            val indicatorOffset by animateDpAsState(
                targetValue = if (isRefreshing) 32.dp else 8.dp,
                label = "pullRefreshOffset"
            )
            val pullRefreshState = rememberPullRefreshState(
                refreshing = isRefreshing,
                onRefresh = {
                    scope.launch {
                        if (isRefreshing) return@launch
                        isRefreshing = true
                        refreshTick++
                        try {
                            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                                listState.animateScrollToItem(0)
                            }
                            if (filterMode == "recent") {
                                val uid = userId ?: return@launch
                                val fresh = repository.fetchRecentPlays(
                                    accessToken = accessToken,
                                    userId = uid
                                )
                                db.recentPlayDao().mergeNewPlays(fresh)
                                loadRecent()
                            } else {
                                val result = repository.getPlayedBeatmaps(
                                    accessToken = accessToken,
                                    genreId = selectedGenreId,
                                    cursorString = null,
                                    searchQuery = searchQuery.trim().ifEmpty { null },
                                    filterMode = filterMode,
                                    playedFilterMode = playedFilterMode,
                                    userId = userId,
                                    isSupporter = isSupporter,
                                    searchAny = searchAnyEnabled,
                                    forceRefresh = true
                                )
                                val incoming = dedupeByTitle(result.beatmaps)
                                if (filterMode == "favorite") {
                                    searchResults = incoming
                                    currentCursor = result.cursor
                                    searchResultsMetadata = filterMetadataFor(incoming, result.metadata)
                                } else {
                                    val merged = mergeByTitle(searchResults, incoming)
                                    val mergedIds = merged.map { it.id }.toSet()
                                    searchResults = merged
                                    currentCursor = result.cursor
                                    searchResultsMetadata =
                                        (searchResultsMetadata + result.metadata).filterKeys { it in mergedIds }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("SearchScreen", "Refresh failed", e)
                        } finally {
                            isRefreshing = false
                        }
                    }
                }
            )

            // Sort results by rank if using most_played mode
            // Force sort by rank to fix ordering issues
            val sortedResults = remember(searchResults, searchResultsMetadata, filterMode) {
                if (filterMode != "recent" && searchResultsMetadata.isNotEmpty()) {
                    searchResults.sortedBy { beatmap ->
                        searchResultsMetadata[beatmap.id]?.first ?: Int.MAX_VALUE
                    }
                } else {
                    searchResults
                }
            }
            val rowAlpha = if (flicking) 0.75f else 1f

            // Collapsible header with search bar and genre filter
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header: Search bar and Genre filter
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            // Search Bar
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                placeholder = {
                                    Text(
                                        stringResource(id = R.string.search_placeholder),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = stringResource(id = R.string.search_cd_search)
                                    )
                                },
                                trailingIcon = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = {
                                                searchQuery = ""
                                                // Refresh results without search query
                                                scope.launch {
                                                    try {
                                                        if (filterMode == "recent") {
                                                        } else {
                                                            val result =
                                                                repository.getPlayedBeatmaps(
                                                                    accessToken,
                                                                    selectedGenreId,
                                                                    null,
                                                                    null,
                                                                    filterMode,
                                                                    playedFilterMode,
                                                                    userId,
                                                                    isSupporter
                                                                )
                                val deduped =
                                    dedupeByTitle(result.beatmaps)
                                mergeGroups = buildMergeGroups(result.beatmaps)
                                searchResults = deduped
                                                            currentCursor = result.cursor
                                                            searchResultsMetadata =
                                                                filterMetadataFor(
                                                                    deduped,
                                                                    result.metadata
                                                                )
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("SearchScreen", "Clear search failed", e)
                                                    }
                                                }
                                            }) {
                                                Icon(
                                                    Icons.Default.Clear,
                                                contentDescription = stringResource(id = R.string.search_cd_clear)
                                                )
                                            }
                                        }

                                        // Filter Mode Dropdown
                                        var filterMenuExpanded by remember { mutableStateOf(false) }
                                        val isSupporterAllowed = isSupporter
                                        val options = if (isSupporterAllowed) {
                                            listOf(
                                                "played",
                                                "recent",
                                                "favorite",
                                                "most_played",
                                                "all"
                                            )
                                        } else {
                                            listOf("recent", "favorite", "most_played", "all")
                                        }
                                        val optionLabels = mapOf(
                                            "played" to stringResource(id = R.string.search_filter_played),
                                            "recent" to stringResource(id = R.string.search_filter_recent),
                                            "favorite" to stringResource(id = R.string.search_filter_favorite),
                                            "most_played" to stringResource(id = R.string.search_filter_most_played),
                                            "all" to stringResource(id = R.string.search_filter_all)
                                        )
                                        val optionColors = mapOf(
                                            "played" to MaterialTheme.colorScheme.primary,
                                            "recent" to androidx.compose.ui.graphics.Color(
                                                0xFF7E57C2
                                            ),
                                            "favorite" to androidx.compose.ui.graphics.Color(
                                                0xFFFFD059
                                            ),
                                            "most_played" to androidx.compose.ui.graphics.Color(
                                                0xFF483AC2
                                            ),
                                            "all" to androidx.compose.ui.graphics.Color(0xFFF748AE)
                                        )

                                        val contentColors = mapOf(
                                            "played" to MaterialTheme.colorScheme.onPrimary,
                                            "recent" to androidx.compose.ui.graphics.Color(
                                                0xFFFFFFFF
                                            ),
                                            "favorite" to androidx.compose.ui.graphics.Color(
                                                0xFF000000
                                            ),
                                            "most_played" to androidx.compose.ui.graphics.Color(
                                                0xFFFFFFFF
                                            ),
                                            "all" to androidx.compose.ui.graphics.Color(0xFFFFFFFF)
                                        )
                                        val currentColor = optionColors[filterMode]
                                            ?: MaterialTheme.colorScheme.primary
                                        val currentContentColor = contentColors[filterMode]
                                            ?: MaterialTheme.colorScheme.primary

                                        OutlinedButton(
                                            onClick = { filterMenuExpanded = true },
                                            modifier = Modifier
                                                .width(98.dp)
                                                .height(40.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = currentColor,
                                                contentColor = currentContentColor
                                            ),
                                            contentPadding = PaddingValues(
                                                horizontal = 8.dp,
                                                vertical = 8.dp
                                            )
                                        ) {
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = optionLabels[filterMode] ?: stringResource(id = R.string.search_filter_select),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                                ),
                                                maxLines = 1
                                            )
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                contentDescription = stringResource(id = R.string.search_cd_filter)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = filterMenuExpanded,
                                            onDismissRequest = { filterMenuExpanded = false }
                                        ) {
                                            options.forEach { mode ->
                                                DropdownMenuItem(
                                                    text = { Text(optionLabels[mode] ?: mode) },
                                                    onClick = {
                                                        filterMenuExpanded = false
                                                        filterMode = mode
                                                        scope.launch {
                                                            settingsManager.saveDefaultSearchView(
                                                                mode
                                                            )
                                                            currentCursor = null
                                                            if (mode == "recent") {
                                                                loadRecent()
                                                            } else {
                                                                val result =
                                                                    repository.getPlayedBeatmaps(
                                                                        accessToken,
                                                                        selectedGenreId,
                                                                        null,
                                                                        searchQuery.trim()
                                                                            .ifEmpty { null },
                                                                        mode,
                                                                        playedFilterMode,
                                                                        userId,
                                                                        isSupporter,
                                                                        searchAnyEnabled
                                                                    )
                                                                val deduped =
                                                                    dedupeByTitle(result.beatmaps)
                                                                searchResults = deduped
                                                                currentCursor = result.cursor
                                                                searchResultsMetadata =
                                                                    filterMetadataFor(
                                                                        deduped,
                                                                        result.metadata
                                                                    )
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        scope.launch {
                                            try {
                                                currentCursor = null
                                                if (filterMode == "recent") {
                                                    loadRecent()
                                                } else {
                                                    val result = repository.getPlayedBeatmaps(
                                                        accessToken,
                                                        selectedGenreId,
                                                        null,
                                                        searchQuery.trim(),
                                                        filterMode,
                                                        playedFilterMode,
                                                        userId,
                                                        isSupporter,
                                                        searchAnyEnabled
                                                    )
                                                    val deduped = dedupeByTitle(result.beatmaps)
                                                    mergeGroups = buildMergeGroups(result.beatmaps)
                                                    searchResults = deduped
                                                    currentCursor = result.cursor
                                                    searchResultsMetadata =
                                                        filterMetadataFor(deduped, result.metadata)
                                                }
                                            } catch (e: Exception) {
                                                Log.e("SearchScreen", "Search failed", e)
                                            }
                                        }
                                    }
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Genre Filter (hide when using most_played mode)
                            val usingMostPlayed = searchResultsMetadata.isNotEmpty()
                            if (!usingMostPlayed) {
                                Text(
                                    text = stringResource(id = R.string.search_filter_genre),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                LazyRow(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                                    items(genres) { (id, name) ->
                                        Button(
                                            onClick = {
                                                selectedGenreId =
                                                    if (selectedGenreId == id) null else id
                                                currentCursor =
                                                    null // Reset cursor when changing genre
                                                scope.launch {
                                                    try {
                                                        if (filterMode == "recent") {
                                                            loadRecent()
                                                        } else {
                                                            val result =
                                                                repository.getPlayedBeatmaps(
                                                                    accessToken,
                                                                    selectedGenreId,
                                                                    null,
                                                                    searchQuery.trim()
                                                                        .ifEmpty { null },
                                                                    filterMode,
                                                                    playedFilterMode,
                                                                    userId,
                                                                    isSupporter,
                                                                    searchAnyEnabled
                                                                )
                                                            val deduped =
                                                                dedupeByTitle(result.beatmaps)
                                                            searchResults = deduped
                                                            currentCursor = result.cursor
                                                            searchResultsMetadata =
                                                                filterMetadataFor(
                                                                    deduped,
                                                                    result.metadata
                                                                )
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("SearchScreen", "Genre filter failed", e)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.padding(end = 8.dp),
                                            contentPadding = PaddingValues(
                                                horizontal = 12.dp,
                                                vertical = 8.dp
                                            ),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (selectedGenreId == id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = if (selectedGenreId == id) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        ) {
                                            Text(name, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            } else {
                                // Show info text when genre filter is hidden for most_played
                                Text(
                                    text = stringResource(id = R.string.search_genre_not_available),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                                )
                            }
                        }
                    }

                    // Search Results
                    items(sortedResults, key = { it.id }) { map ->
                        val downloadProgress = downloadStates[map.id]
                        val isDownloaded = downloadedBeatmapSetIds.contains(map.id)
                        val metadata = searchResultsMetadata[map.id] // (rank, playcount) or null

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { alpha = rowAlpha }
                                .combinedClickable(
                                    onClick = {
                                        // Short press: play if downloaded
                                        if (isDownloaded) {
                                            scope.launch {
                                                // Get the beatmap entities for this set from database
                                                val beatmaps = withContext(Dispatchers.IO) {
                                                    db.beatmapDao().getTracksForSet(map.id)
                                                }
                                                if (beatmaps.isNotEmpty()) {
                                                    // Play the first track and use the whole list as playlist
                                                    musicController.playSong(beatmaps.first(), beatmaps)
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.search_play_not_downloaded),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        } else {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.search_play_not_downloaded),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    onLongClick = {
                                        // Long press: show info dialog with vibration
                                        Log.d("SearchScreen", "Long press detected, showing info dialog")
                                        // Temporarily remove vibration to test if it's the cause of crash
                                        // vibrate()
                                        infoDialogVisible = true
                                        infoLoading = true
                                        infoError = null
                                        infoTarget = map
                                        infoBeatmaps = emptyList()
                                        val key = mergeKey(map)
                                        infoMergedSetIds = mergeGroups[key]?.toList() ?: listOf(map.id)
                                        infoSetCreators = emptyMap()
                                        scope.launch {
                                            try {
                                                val allBeatmaps = mutableListOf<BeatmapDetail>()
                                                val creators = mutableMapOf<Long, String>()
                                                val ids = infoMergedSetIds.ifEmpty { listOf(map.id) }
                                                ids.forEach { setId ->
                                                    try {
                                                        val detail = repository.getBeatmapsetDetail(
                                                            beatmapsetId = setId
                                                        )
                                                        allBeatmaps += detail.beatmaps
                                                        creators[detail.id] = detail.creator
                                                    } catch (e: Exception) {
                                                        // keep going, surface error at end
                                                        infoError = e.message ?: context.getString(R.string.search_info_load_partial_error)
                                                    }
                                                }
                                                infoBeatmaps = allBeatmaps
                                                infoSetCreators = creators
                                            } catch (e: Exception) {
                                                infoError = e.message ?: context.getString(R.string.search_info_load_error)
                                            } finally {
                                                infoLoading = false
                                            }
                                        }
                                    }
                                )
                                .padding(vertical = 8.dp)
                                // Apply horizontal padding: 0 for most_played (full width), 16dp for others
                                .padding(horizontal = if (metadata != null) 0.dp else 16.dp)
                                // Add extra right padding for most_played
                                .padding(end = if (metadata != null) 8.dp else 0.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Ranking number (for most_played mode)
                            if (metadata != null) {
                                Text(
                                    text = "#${metadata.first}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(48.dp),
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Cover Image
                            AsyncImage(
                                model = map.covers.listUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop
                            )

                            Column(
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = map.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1
                                )
                                Text(
                                    text = map.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1
                                )

                                // Download Progress Bar
                                if (downloadProgress != null) {
                                    Column(modifier = Modifier.padding(top = 4.dp)) {
                                        if (downloadProgress.status == "Downloading" && downloadProgress.progress < 100) {
                                            LinearProgressIndicator(
                                                progress = downloadProgress.progress / 100f,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        } else {
                                            LinearProgressIndicator(
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        Text(
                                            text = downloadProgress.status,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            // Playcount (for most_played mode)
                            if (metadata != null) {
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = "${metadata.second}",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(id = R.string.search_playcount_label),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            // Download Button
                            IconButton(
                                onClick = {
                                    if (!isDownloaded) {
                                        scope.launch {
                                            downloadStates =
                                                downloadStates + (map.id to DownloadProgress(
                                                    0,
                                                    context.getString(R.string.search_download_starting)
                                                ))
                                            downloader.downloadBeatmap(map.id, accessToken)
                                                .collect { state ->
                                                    when (state) {
                                                        is DownloadState.Downloading -> {
                                                            downloadStates =
                                                                downloadStates + (map.id to DownloadProgress(
                                                                    state.progress,
                                                                    context.getString(R.string.search_download_downloading)
                                                                ))
                                                        }

                                                        is DownloadState.Downloaded -> {
                                                            downloadStates =
                                                                downloadStates + (map.id to DownloadProgress(
                                                                    100,
                                                                    context.getString(R.string.search_download_extracting)
                                                                ))
                                                            try {
                                                                val extractedTracks =
                                                                    extractor.extractBeatmap(
                                                                        state.file,
                                                                        map.id
                                                                    )
                                                                val fallbackCoverPath =
                                                                    if (extractedTracks.any { track ->
                                                                            track.coverFile == null || !track.coverFile.exists()
                                                                        }
                                                                    ) {
                                                                        downloadFallbackCoverImage(
                                                                            map.id,
                                                                            map.covers.listUrl
                                                                        )
                                                                    } else {
                                                                        null
                                                                    }
                                                                extractedTracks.forEach { track ->
                                                                    val coverPath = track.coverFile?.takeIf { it.exists() }?.absolutePath
                                                                        ?: fallbackCoverPath
                                                                        ?: ""
                                                                val entity = BeatmapEntity(
                                                                    beatmapSetId = map.id,
                                                                    title = track.title,
                                                                    artist = track.artist,
                                                                    creator = map.creator,
                                                                    difficultyName = track.difficultyName,
                                                                    audioPath = track.audioFile.absolutePath,
                                                                    coverPath = coverPath,
                                                                    genreId = map.genreId
                                                                )
                                                                    db.beatmapDao()
                                                                        .insertBeatmap(entity)
                                                                // Mark this track as downloaded in any playlists that contain it
                                                                db.playlistDao().updateTrackDownloadStatus(entity.uid, true)
                                                                }
                                                                downloadStates =
                                                                    downloadStates + (map.id to DownloadProgress(
                                                                        100,
                                                                        context.getString(R.string.search_download_done)
                                                                    ))
                                                                // Remove from download states after 2 seconds
                                                                kotlinx.coroutines.delay(2000)
                                                                downloadStates =
                                                                    downloadStates - map.id
                                                            } catch (e: Exception) {
                                                                downloadStates =
                                                                    downloadStates + (map.id to DownloadProgress(
                                                                        0,
                                                                        "Error: ${e.message}"
                                                                    ))
                                                            }
                                                        }

                                                        is DownloadState.Error -> {
                                                            downloadStates =
                                                                downloadStates + (map.id to DownloadProgress(
                                                                    0,
                                                                    context.getString(R.string.search_download_failed)
                                                                ))
                                                        }

                                                        else -> {}
                                                    }
                                                }
                                        }
                                    }
                                },
                                enabled = !isDownloaded && downloadProgress == null // Disable if already downloaded or downloading
                            ) {
                                Icon(
                                    imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.Add,
                                    contentDescription = if (isDownloaded) stringResource(id = R.string.search_cd_downloaded) else stringResource(id = R.string.search_cd_download),
                                    tint = if (isDownloaded) {
                                        MaterialTheme.colorScheme.primary
                                    } else if (downloadProgress != null) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                        }
                    }

                    // Pagination / Load More
                    if (filterMode != "recent" && searchResults.isNotEmpty() && currentCursor != null) {
                        item {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isLoadingMore = true
                                        try {
                                            val result = repository.getPlayedBeatmaps(
                                                accessToken,
                                                selectedGenreId,
                                                currentCursor,
                                                searchQuery.trim().ifEmpty { null },
                                                filterMode,
                                                playedFilterMode,
                                                userId,
                                                isSupporter,
                                                searchAnyEnabled
                                            )
                                            if (result.beatmaps.isNotEmpty()) {
                                                val merged =
                                                    mergeByTitle(searchResults, result.beatmaps)
                                                val mergedIds = merged.map { it.id }.toSet()
                                                mergeGroups = unionMergeGroups(
                                                    mergeGroups,
                                                    buildMergeGroups(result.beatmaps)
                                                )
                                                searchResults = merged
                                                currentCursor = result.cursor
                                                searchResultsMetadata =
                                                    (searchResultsMetadata + result.metadata).filterKeys { it in mergedIds }
                                            } else {
                                                currentCursor = null
                                            }
                                        } catch (e: Exception) {
                                            Log.e("SearchScreen", "Load more failed", e)
                                        } finally {
                                            isLoadingMore = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                enabled = !isLoadingMore
                            ) {
                                Text(if (isLoadingMore) "Loading..." else "Load More")
                            }
                        }
                    }

                }
                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = indicatorOffset),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (infoDialogVisible && infoTarget != null) {
            val target = infoTarget!!
            val downloaded = downloadedBeatmapSetIds.contains(target.id)
            val grouped = infoBeatmaps.groupBy { it.mode }
            AlertDialog(
                onDismissRequest = { infoDialogVisible = false },
                title = {
                    Column {
                        Text(
                            text = target.title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2
                        )
                        Text(
                            text = target.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                text = {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        state = rememberLazyListState()
                    ) {
                        if (infoCoverEnabled) {
                            item {
                                AsyncImage(
                                    model = target.covers.listUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                        
                        if (infoLoading) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                }
                            }
                        } else if (infoError != null) {
                            item {
                                Text(
                                    text = infoError ?: "",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else if (grouped.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(id = R.string.info_no_details),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        } else {
                            val bySet = infoBeatmaps.groupBy { it.beatmapsetId }
                            items(bySet.entries.toList()) { (setId, list) ->
                                val modes = list.groupBy { it.mode }
                                val modeLabels = modes.keys.joinToString { modeLabel(it) }
                                val starMin = list.minOfOrNull { it.difficultyRating } ?: 0f
                                val starMax = list.maxOfOrNull { it.difficultyRating } ?: 0f
                                val url = list.firstOrNull()?.url ?: "https://osu.ppy.sh/beatmapsets/$setId"
                                val author = infoSetCreators[setId] ?: target.creator
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { uriHandler.openUri(url) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(id = R.string.search_author_prefix, author),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                            Text(
                                                text = stringResource(id = R.string.info_modes, modeLabels),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                text = if (list.size == 1) {
                                                    stringResource(id = R.string.info_star_single, starMin)
                                                } else {
                                                    stringResource(id = R.string.info_star_range, starMin, starMax)
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                            Text(
                                                text = stringResource(id = R.string.info_diff_count, list.size),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (downloaded) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    val tracks = db.beatmapDao().getTracksForSet(target.id)
                                    if (tracks.isNotEmpty()) {
                                        val allDownloaded =
                                            db.beatmapDao().getAllBeatmaps().first()
                                        musicController.playSong(tracks[0], allDownloaded)
                                    }
                                }
                            }
                        ) {
                            Text(stringResource(id = R.string.search_play))
                        }
                    }
                },
                dismissButton = {}
            )
        }
        
        // Fetch user info when access token changes (login/logout)
        LaunchedEffect(accessToken) {
            if (accessToken != null) {
                try {
                    val user = repository.getMe()
                    userId = user.id.toString()
                    isSupporter = user.isSupporter
                    isSupporterKnown = true
                } catch (e: Exception) {
                    Log.e("SearchScreen", "Failed to get user info", e)
                    isSupporter = false // Default to non-supporter on error
                    isSupporterKnown = true
                }
            } else {
                // Reset state on logout
                userId = null
                isSupporter = false
                isSupporterKnown = false
                searchResults = emptyList()
                searchResultsMetadata = emptyMap()
            }
        }
            
        // Initial Load - Show cached data immediately, then refresh
        LaunchedEffect(accessToken, filterMode, userId) {
            if (accessToken != null && userId != null) {
                val uid = userId ?: return@LaunchedEffect
                try {
                    if (filterMode == "recent") {
                        loadRecent(forceRefresh = true) // Force fetch from API on initial load
                    } else {
                        // First, try to load cached data immediately for all filter modes
                        val cachedResult = repository.getCachedPlayedBeatmaps(
                            genreId = null,
                            searchQuery = null,
                            filterMode = filterMode,
                            playedFilterMode = playedFilterMode,
                            userId = uid
                        )
                        if (cachedResult != null) {
                            val deduped = dedupeByTitle(cachedResult.beatmaps)
                            mergeGroups = buildMergeGroups(cachedResult.beatmaps)
                            searchResults = deduped
                            currentCursor = cachedResult.cursor
                            searchResultsMetadata = filterMetadataFor(deduped, cachedResult.metadata)
                        }

                        // Then refresh with fresh data in background for all filter modes
                        scope.launch {
                            try {
                                val result = repository.getPlayedBeatmaps(accessToken, null, null, null, filterMode, playedFilterMode, uid, isSupporter, searchAnyEnabled)
                                val deduped = dedupeByTitle(result.beatmaps)
                                mergeGroups = buildMergeGroups(result.beatmaps)
                                searchResults = deduped
                                currentCursor = result.cursor
                                searchResultsMetadata = filterMetadataFor(deduped, result.metadata)
                            } catch (e: Exception) {
                                Log.e("SearchScreen", "Background refresh failed", e)
                                // Don't overwrite cached data if refresh fails
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SearchScreen", "Initial load failed", e)
                }
            }
        }
    }
}

private fun RecentPlayEntity.toBeatmapset(): BeatmapsetCompact {
    val cover = coverUrl ?: ""
    return BeatmapsetCompact(
        id = beatmapSetId,
        title = title,
        artist = artist,
        creator = creator,
        covers = Covers(coverUrl = cover, listUrl = cover),
        genreId = genreId
    )
}

private fun modeLabel(mode: String): String {
    return when (mode.lowercase()) {
        "osu" -> "std"
        "mania" -> "mania"
        "taiko" -> "taiko"
        "fruits", "catch" -> "catch"
        else -> mode
    }
}
