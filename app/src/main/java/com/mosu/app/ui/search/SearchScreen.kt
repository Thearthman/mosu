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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import coil.compose.AsyncImage
import com.mosu.app.R
import androidx.compose.material3.Icon
import com.mosu.app.data.api.model.Covers
import com.mosu.app.data.api.model.BeatmapsetCompact
import com.mosu.app.data.api.model.BeatmapDetail
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.data.repository.OsuRepository
import com.mosu.app.data.services.TrackService
import com.mosu.app.domain.download.BeatmapDownloadService
import com.mosu.app.domain.download.UnifiedDownloadState
import com.mosu.app.domain.download.DownloadState
import com.mosu.app.ui.components.InfoPopup
import com.mosu.app.ui.components.InfoPopupConfig
import com.mosu.app.domain.download.ZipExtractor
import com.mosu.app.domain.download.CoverDownloadService
import com.mosu.app.domain.search.BeatmapSearchService
import com.mosu.app.domain.model.modeLabel
import com.mosu.app.domain.model.getStarRatingColor
import com.mosu.app.domain.model.getGradientColorsForRange
import com.mosu.app.domain.model.createGradientStops
import com.mosu.app.domain.model.formatPlayedAtTimestamp
import com.mosu.app.domain.search.RecentItem
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
    accountManager: com.mosu.app.data.AccountManager,
    settingsManager: com.mosu.app.data.SettingsManager,
    musicController: com.mosu.app.player.MusicController,
    downloadService: BeatmapDownloadService,
    scrollToTop: Boolean = false,
    onScrolledToTop: () -> Unit = {}
) {
    
    // Search Query
    var searchQuery by remember { mutableStateOf("") }
    
    // Played filter mode from settings
    val defaultSearchView by settingsManager.defaultSearchView.collectAsState(initial = "played")
    val playedFilterMode by settingsManager.playedFilterMode.collectAsState(initial = "url")
    val onlyLeaderboardEnabled by settingsManager.onlyLeaderboardEnabled.collectAsState(initial = true)
    
    val currentAccount by accountManager.currentAccount.collectAsState(initial = null)
    var isSupporterKnown by remember { mutableStateOf(false) }
    var userId by remember { mutableStateOf<String?>(null) }
    var isSupporter by remember { mutableStateOf(false) }
    
    // Update user info from current account
    LaunchedEffect(currentAccount) {
        val user = currentAccount?.userInfo
        if (user != null) {
            userId = user.id.toString()
            isSupporter = user.isSupporter
            isSupporterKnown = true
        } else {
            userId = null
            isSupporter = false
            isSupporterKnown = false
        }
    }
    
    var filterMode by remember { mutableStateOf(defaultSearchView) }
    
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
    var recentGroupedResults by remember { mutableStateOf<List<RecentItem>>(emptyList()) }
    var searchResultsMetadata by remember { mutableStateOf<Map<Long, Pair<Int, Int>>>(emptyMap()) } // beatmapId -> (rank, playcount)
    var recentTimestamps by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) } // beatmapId -> playedAt timestamp
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
    var downloadedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var mergeGroups by remember { mutableStateOf<Map<String, Set<Long>>>(emptyMap()) } // key -> setIds
    
    // Load downloaded beatmap IDs from database
    LaunchedEffect(Unit) {
        db.beatmapDao().getAllBeatmaps().collect { beatmaps ->
            downloadedBeatmapSetIds = beatmaps.map { it.beatmapSetId }.toSet()
            downloadedKeys = beatmaps.map { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }.toSet()
        }
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val searchService = remember { BeatmapSearchService(repository, db, context) }
    val infoCoverEnabled by settingsManager.infoCoverEnabled.collectAsState(initial = true)
    val apiSource by settingsManager.apiSource.collectAsState(initial = "osu")
    val previewManager = remember { SearchPreviewManager(context, musicController) }

    DisposableEffect(Unit) {
        onDispose {
            previewManager.release()
        }
    }

    var infoDialogVisible by remember { mutableStateOf(false) }
    var infoLoading by remember { mutableStateOf(false) }
    var infoError by remember { mutableStateOf<String?>(null) }
    var infoTarget by remember { mutableStateOf<BeatmapsetCompact?>(null) }
    var infoBeatmaps by remember { mutableStateOf<List<BeatmapDetail>>(emptyList()) }
    var infoSetCreators by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    // Effect to fetch info details when target changes and dialog is visible
    LaunchedEffect(infoTarget, infoDialogVisible) {
        if (infoDialogVisible && infoTarget != null) {
            val target = infoTarget!!
            infoLoading = true
            infoError = null
            infoBeatmaps = emptyList()
            infoSetCreators = emptyMap()

            val result = searchService.loadInfoPopup(target.title, target.artist)
            
            result.onSuccess { (beatmaps, creators) ->
                infoBeatmaps = beatmaps
                infoSetCreators = creators
            }.onFailure { e ->
                infoError = e.message ?: context.getString(R.string.search_info_load_error)
            }
            
            infoLoading = false
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

    val genres = searchService.genres.map { (id, name) ->
        id to stringResource(id = when (name) {
            "Electronic" -> R.string.genre_electronic
            "Anime" -> R.string.genre_anime
            "Rock" -> R.string.genre_rock
            "Pop" -> R.string.genre_pop
            "Game" -> R.string.genre_game
            "Hip Hop" -> R.string.genre_hiphop
            "Metal" -> R.string.genre_metal
            "Classical" -> R.string.genre_classical
            "Folk" -> R.string.genre_folk
            "Jazz" -> R.string.genre_jazz
            "Novelty" -> R.string.genre_novelty
            "Other" -> R.string.genre_other
            else -> R.string.genre_other
        })
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
                                val (groupedItems, mergeGroupsResult, timestamps) = searchService.loadRecentFiltered(accessToken, userId, searchQuery, selectedGenreId)
                                mergeGroups = mergeGroupsResult
                                recentGroupedResults = groupedItems
                                searchResults = emptyList() // Clear regular results for recent mode
                                currentCursor = null
                                searchResultsMetadata = emptyMap()
                                recentTimestamps = timestamps
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
                                    searchAny = !onlyLeaderboardEnabled,
                                    forceRefresh = true
                                )
                                val incoming = searchService.dedupeByTitle(result.beatmaps, downloadedBeatmapSetIds, downloadedKeys)
                                if (filterMode == "favorite") {
                                    searchResults = incoming
                                    currentCursor = result.cursor
                                    searchResultsMetadata = searchService.filterMetadataFor(incoming, result.metadata)
                                } else {
                                    val merged = searchService.mergeByTitle(searchResults, incoming, downloadedBeatmapSetIds, downloadedKeys)
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
                if (searchResultsMetadata.isNotEmpty()) {
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
                                    searchService.dedupeByTitle(result.beatmaps, downloadedBeatmapSetIds, downloadedKeys)
                                mergeGroups = searchService.buildMergeGroups(result.beatmaps)
                                searchResults = deduped
                                                            currentCursor = result.cursor
                                                            searchResultsMetadata =
                                                                searchService.filterMetadataFor(
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
                                                style = if (filterMode == "most_played") {
                                                    MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f
                                                    )
                                                } else {
                                                    MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                                    )
                                                },
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
                                                                val (groupedItems, mergeGroupsResult, timestamps) = searchService.loadRecentFiltered(accessToken, userId, searchQuery, selectedGenreId)
                                                                mergeGroups = mergeGroupsResult
                                                                recentGroupedResults = groupedItems
                                                                searchResults = emptyList()
                                                                currentCursor = null
                                                                searchResultsMetadata = emptyMap()
                                                                recentTimestamps = timestamps
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
                                                                    !onlyLeaderboardEnabled
                                                                )
                                                                val deduped =
                                                                    searchService.dedupeByTitle(result.beatmaps, downloadedBeatmapSetIds, downloadedKeys)
                                                                searchResults = deduped
                                                                currentCursor = result.cursor
                                                                searchResultsMetadata =
                                                                    searchService.filterMetadataFor(
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
                                                    val (groupedItems, mergeGroupsResult, timestamps) = searchService.loadRecentFiltered(accessToken, userId, searchQuery, selectedGenreId)
                                                    mergeGroups = mergeGroupsResult
                                                    recentGroupedResults = groupedItems
                                                    searchResults = emptyList()
                                                    currentCursor = null
                                                    searchResultsMetadata = emptyMap()
                                                    recentTimestamps = timestamps
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
                                                        !onlyLeaderboardEnabled
                                                    )
                                                    val deduped = searchService.dedupeByTitle(result.beatmaps, downloadedBeatmapSetIds, downloadedKeys)
                                                    mergeGroups = searchService.buildMergeGroups(result.beatmaps)
                                                    searchResults = deduped
                                                    currentCursor = result.cursor
                                                    searchResultsMetadata =
                                                        searchService.filterMetadataFor(deduped, result.metadata)
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
                                                            val (groupedItems, mergeGroupsResult, timestamps) = searchService.loadRecentFiltered(accessToken, userId, searchQuery, selectedGenreId)
                                                            mergeGroups = mergeGroupsResult
                                                            recentGroupedResults = groupedItems
                                                            searchResults = emptyList()
                                                            currentCursor = null
                                                            searchResultsMetadata = emptyMap()
                                                            recentTimestamps = timestamps
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
                                                                    !onlyLeaderboardEnabled
                                                                )
                                                            val deduped =
                                                                searchService.dedupeByTitle(result.beatmaps, downloadedBeatmapSetIds, downloadedKeys)
                                                            searchResults = deduped
                                                            currentCursor = result.cursor
                                                            searchResultsMetadata =
                                                                searchService.filterMetadataFor(
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
                    if (filterMode == "recent") {
                        items(recentGroupedResults) { item ->
                            when (item) {
                                is RecentItem.Header -> {
                                    // Timestamp header/divider
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp, horizontal = 16.dp)
                                    ) {
                                        Text(
                                            text = item.timestamp,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        )
                                        HorizontalDivider(
                                            modifier = Modifier.padding(top = 4.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                }
                                is RecentItem.Song -> {
                                    val map = item.beatmapset
                                    val downloadProgress = downloadStates[map.id]
                                    val mapKey = "${map.title.trim().lowercase()}|${map.artist.trim().lowercase()}"
                                    val isDownloaded = downloadedBeatmapSetIds.contains(map.id) || downloadedKeys.contains(mapKey)
                                    val metadata = searchResultsMetadata[map.id] // (rank, playcount) or null

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { alpha = rowAlpha }
                                .background(
                                    if (previewManager.previewingId == map.id) {
                                        Brush.horizontalGradient(
                                            0.0f to Color.Transparent,
                                            previewManager.progress to Color.Transparent,
                                            previewManager.progress to MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                            1.0f to MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                        )
                                    } else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                )
                                .combinedClickable(
                                    onClick = {
                                        // Short press: play if downloaded, otherwise preview
                                        if (isDownloaded) {
                                            scope.launch {
                                                // Get the beatmap entities for this set from database
                                                val beatmaps = withContext(Dispatchers.IO) {
                                                    val tracks = db.beatmapDao().getTracksForSet(map.id)
                                                    if (tracks.isEmpty()) {
                                                        db.beatmapDao().getTracksByTitleArtist(map.title, map.artist)
                                                    } else {
                                                        tracks
                                                    }
                                                }
                                                if (beatmaps.isNotEmpty()) {
                                                    // Play the first track and use the whole list as playlist
                                                    musicController.playSong(beatmaps.first(), beatmaps)
                                                } else {
                                                    previewManager.playPreview(map, apiSource)
                                                }
                                            }
                                        } else {
                                            previewManager.playPreview(map, apiSource)
                                        }
                                    },
                                    onLongClick = {
                                        // Long press: show info dialog with vibration
                                        Log.d("SearchScreen", "Long press detected, showing info dialog")
                                        // Temporarily remove vibration to test if it's the cause of crash
                                        // vibrate()
                                        infoTarget = map
                                        infoDialogVisible = true
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
                                Text(
                                    text = formatPlayedAtTimestamp(item.playedAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )

                                // Download Progress Bar
                                if (downloadProgress != null) {
                                    Column(modifier = Modifier.padding(top = 4.dp)) {
                                        if (downloadProgress.status == "Downloading" && downloadProgress.progress < 100) {
                                            LinearProgressIndicator(
                                                progress = { downloadProgress.progress / 100f },
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
                                            downloadService.downloadBeatmap(
                                                beatmapSetId = map.id,
                                                accessToken = accessToken,
                                                title = map.title,
                                                artist = map.artist,
                                                creator = map.creator,
                                                genreId = map.genreId,
                                                coversListUrl = map.covers.listUrl
                                            ).collect { state ->
                                                when (state) {
                                                    is UnifiedDownloadState.Progress -> {
                                                        downloadStates =
                                                            downloadStates + (map.id to DownloadProgress(
                                                                state.progress,
                                                                state.status
                                                            ))
                                                    }
                                                    is UnifiedDownloadState.Success -> {
                                                        downloadStates =
                                                            downloadStates + (map.id to DownloadProgress(
                                                                100,
                                                                context.getString(R.string.search_download_done)
                                                            ))
                                                        kotlinx.coroutines.delay(2000)
                                                        downloadStates = downloadStates - map.id
                                                    }
                                                    is UnifiedDownloadState.Error -> {
                                                        Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                                                        downloadStates = downloadStates - map.id
                                                    }
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
                            }
                        }
                    } else {
                        // Non-recent modes (original logic)
                        items(sortedResults, key = { it.id }) { map ->
                            val downloadProgress = downloadStates[map.id]
                            val mapKey = "${map.title.trim().lowercase()}|${map.artist.trim().lowercase()}"
                            val isDownloaded = downloadedBeatmapSetIds.contains(map.id) || downloadedKeys.contains(mapKey)
                            val metadata = searchResultsMetadata[map.id] // (rank, playcount) or null

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { alpha = rowAlpha }
                                    .background(
                                        if (previewManager.previewingId == map.id) 
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) 
                                        else Color.Transparent
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            // Short press: play if downloaded, otherwise preview
                                            if (isDownloaded) {
                                                scope.launch {
                                                    // Get the beatmap entities for this set from database
                                                    val beatmaps = withContext(Dispatchers.IO) {
                                                        val tracks = db.beatmapDao().getTracksForSet(map.id)
                                                        if (tracks.isEmpty()) {
                                                            db.beatmapDao().getTracksByTitleArtist(map.title, map.artist)
                                                        } else {
                                                            tracks
                                                        }
                                                    }
                                                    if (beatmaps.isNotEmpty()) {
                                                        // Play the first track and use the whole list as playlist
                                                        musicController.playSong(beatmaps.first(), beatmaps)
                                                    } else {
                                                        previewManager.playPreview(map, apiSource)
                                                    }
                                                }
                                            } else {
                                                previewManager.playPreview(map, apiSource)
                                            }
                                        },
                                        onLongClick = {
                                            // Long press: show info dialog with vibration
                                            Log.d("SearchScreen", "Long press detected, showing info dialog")
                                            // Temporarily remove vibration to test if it's the cause of crash
                                            // vibrate()
                                            infoTarget = map
                                            infoDialogVisible = true
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
                                                    progress = { downloadProgress.progress / 100f },
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
                                                downloadService.downloadBeatmap(
                                                    beatmapSetId = map.id,
                                                    accessToken = accessToken,
                                                    title = map.title,
                                                    artist = map.artist,
                                                    creator = map.creator,
                                                    genreId = map.genreId,
                                                    coversListUrl = map.covers.listUrl
                                                ).collect { state ->
                                                    when (state) {
                                                        is com.mosu.app.domain.download.UnifiedDownloadState.Progress -> {
                                                            downloadStates =
                                                                downloadStates + (map.id to DownloadProgress(
                                                                    state.progress,
                                                                    state.status
                                                                ))
                                                        }
                                                        is com.mosu.app.domain.download.UnifiedDownloadState.Success -> {
                                                            downloadStates =
                                                                downloadStates + (map.id to DownloadProgress(
                                                                    100,
                                                                    context.getString(R.string.search_download_done)
                                                                ))
                                                            kotlinx.coroutines.delay(2000)
                                                            downloadStates = downloadStates - map.id
                                                        }
                                                        is com.mosu.app.domain.download.UnifiedDownloadState.Error -> {
                                                            Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                                                            downloadStates = downloadStates - map.id
                                                        }
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
                                                !onlyLeaderboardEnabled
                                            )
                                            if (result.beatmaps.isNotEmpty()) {
                                                val merged =
                                                    searchService.mergeByTitle(searchResults, result.beatmaps, downloadedBeatmapSetIds, downloadedKeys)
                                                val mergedIds = merged.map { it.id }.toSet()
                                                mergeGroups = searchService.unionMergeGroups(
                                                    mergeGroups,
                                                    searchService.buildMergeGroups(result.beatmaps)
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
                                Text(if (isLoadingMore) stringResource(id = R.string.search_loading) else stringResource(id = R.string.search_load_more))
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
            val targetKey = "${target.title.trim().lowercase()}|${target.artist.trim().lowercase()}"
            val downloaded = downloadedBeatmapSetIds.contains(target.id) || downloadedKeys.contains(targetKey)
            // Info Popup
            InfoPopup(
                visible = true,
                onDismiss = { infoDialogVisible = false },
                target = target,
                beatmaps = infoBeatmaps,
                loading = infoLoading,
                error = infoError,
                setCreators = infoSetCreators,
                downloaded = downloaded,
                config = InfoPopupConfig(
                    infoCoverEnabled = infoCoverEnabled,
                    onDownloadClick = { beatmapset ->
                        scope.launch {
                            downloadService.downloadBeatmap(
                                beatmapSetId = beatmapset.id,
                                accessToken = accessToken,
                                title = beatmapset.title,
                                artist = beatmapset.artist,
                                creator = beatmapset.creator,
                                genreId = beatmapset.genreId,
                                coversListUrl = beatmapset.covers.listUrl
                            ).collect { state ->
                                when (state) {
                                    is UnifiedDownloadState.Progress -> {
                                        downloadStates =
                                            downloadStates + (beatmapset.id to DownloadProgress(
                                                state.progress,
                                                state.status
                                            ))
                                    }
                                    is UnifiedDownloadState.Success -> {
                                        downloadStates =
                                            downloadStates + (beatmapset.id to DownloadProgress(
                                                100,
                                                context.getString(R.string.search_download_done)
                                            ))
                                        kotlinx.coroutines.delay(2000)
                                        downloadStates = downloadStates - beatmapset.id
                                    }
                                    is UnifiedDownloadState.Error -> {
                                        Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                                        downloadStates = downloadStates - beatmapset.id
                                    }
                                }
                            }
                        }
                    },
                    onRestoreClick = { beatmapset ->
                        scope.launch {
                            downloadService.downloadBeatmap(
                                beatmapSetId = beatmapset.id,
                                accessToken = accessToken,
                                title = beatmapset.title,
                                artist = beatmapset.artist,
                                creator = beatmapset.creator,
                                genreId = beatmapset.genreId,
                                coversListUrl = beatmapset.covers.listUrl
                            ).collect { state ->
                                when (state) {
                                    is UnifiedDownloadState.Progress -> {
                                        downloadStates =
                                            downloadStates + (beatmapset.id to DownloadProgress(
                                                state.progress,
                                                state.status
                                            ))
                                    }
                                    is UnifiedDownloadState.Success -> {
                                        downloadStates =
                                            downloadStates + (beatmapset.id to DownloadProgress(
                                                100,
                                                context.getString(R.string.search_download_done)
                                            ))
                                        kotlinx.coroutines.delay(2000)
                                        downloadStates = downloadStates - beatmapset.id
                                    }
                                    is UnifiedDownloadState.Error -> {
                                        Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                                        downloadStates = downloadStates - beatmapset.id
                                    }
                                }
                            }
                        }
                    },
                    onPlayClick = { beatmapset ->
                        if (downloaded) {
                            scope.launch {
                                val tracks = db.beatmapDao().getTracksForSet(beatmapset.id)
                                if (tracks.isNotEmpty()) {
                                    val allDownloaded = db.beatmapDao().getAllBeatmaps().first()
                                    musicController.playSong(tracks[0], allDownloaded)
                                }
                            }
                        } else {
                            previewManager.playPreview(beatmapset, apiSource)
                        }
                    }
                )
            )
        }
        
        // Reset results on logout
        LaunchedEffect(accessToken) {
            if (accessToken == null) {
                searchResults = emptyList()
                recentGroupedResults = emptyList()
                searchResultsMetadata = emptyMap()
                recentTimestamps = emptyMap()
            }
        }
            
        // Initial Load - Show cached data immediately, then refresh
        LaunchedEffect(accessToken, filterMode, userId, onlyLeaderboardEnabled) {
            if (accessToken != null && userId != null) {
                val uid = userId ?: return@LaunchedEffect
                try {
                    if (filterMode == "recent") {
                        val (groupedItems, mergeGroupsResult, timestamps) = searchService.loadRecentFiltered(accessToken, userId, searchQuery, selectedGenreId, forceRefresh = true)
                        mergeGroups = mergeGroupsResult
                        recentGroupedResults = groupedItems
                        searchResults = emptyList()
                        currentCursor = null
                        searchResultsMetadata = emptyMap()
                        recentTimestamps = timestamps
                    } else {
                        // First, try to load cached data immediately for all filter modes
                        val cachedResult = repository.getCachedPlayedBeatmaps(
                            genreId = null,
                            searchQuery = null,
                            filterMode = filterMode,
                            playedFilterMode = playedFilterMode,
                            userId = uid,
                            searchAny = !onlyLeaderboardEnabled
                        )
                        if (cachedResult != null) {
                            val deduped = searchService.dedupeByTitle(cachedResult.beatmaps, downloadedBeatmapSetIds, downloadedKeys)
                            mergeGroups = searchService.buildMergeGroups(cachedResult.beatmaps)
                            searchResults = deduped
                            currentCursor = cachedResult.cursor
                            searchResultsMetadata = searchService.filterMetadataFor(deduped, cachedResult.metadata)
                        }

                        // Then refresh with fresh data in background for all filter modes
                        scope.launch {
                            try {
                                val result = repository.getPlayedBeatmaps(accessToken, null, null, null, filterMode, playedFilterMode, uid, isSupporter, !onlyLeaderboardEnabled)
                                val deduped = searchService.dedupeByTitle(result.beatmaps, downloadedBeatmapSetIds, downloadedKeys)
                                mergeGroups = searchService.buildMergeGroups(result.beatmaps)
                                searchResults = deduped
                                currentCursor = result.cursor
                                searchResultsMetadata = searchService.filterMetadataFor(deduped, result.metadata)
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

