package com.mosu.app.ui.search

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mosu.app.R
import com.mosu.app.data.api.model.BeatmapsetCompact
import com.mosu.app.data.api.model.Covers
import com.mosu.app.domain.download.BeatmapDownloadService
import com.mosu.app.domain.download.UnifiedDownloadState
import com.mosu.app.domain.model.formatPlayedAtTimestamp
import com.mosu.app.domain.search.RecentItem
import com.mosu.app.player.MusicController
import com.mosu.app.ui.components.BeatmapSetActions
import com.mosu.app.ui.components.BeatmapSetData
import com.mosu.app.ui.components.BeatmapSetItem
import com.mosu.app.ui.components.BeatmapSetListConfig
import com.mosu.app.ui.components.DraggableScrollbar
import com.mosu.app.ui.components.InfoPopup
import com.mosu.app.ui.components.InfoPopupConfig
import com.mosu.app.ui.components.BeatmapSetSwipeActions
import com.mosu.app.ui.components.BeatmapSetSwipeItem
import com.mosu.app.ui.components.PlaylistOption
import com.mosu.app.ui.components.PlaylistSelectorDialog
import com.mosu.app.ui.components.beatmapSetList
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.data.services.TrackService
import com.mosu.app.ui.components.DownloadProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.lifecycle.viewModelScope
import com.mosu.app.ui.DeferredActionViewModel

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    deferredActionViewModel: DeferredActionViewModel,
    accessToken: String?,
    accountManager: com.mosu.app.data.AccountManager,
    settingsManager: com.mosu.app.data.SettingsManager,
    musicController: MusicController,
    downloadService: BeatmapDownloadService,
    scrollToTop: Boolean = false,
    onScrolledToTop: () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null
) {
    val vm = viewModel

    // Played filter mode from settings
    val defaultSearchView by settingsManager.defaultSearchView.collectAsState(initial = "played")
    val playedFilterMode by settingsManager.playedFilterMode.collectAsState(initial = "url")
    val onlyLeaderboardEnabled by settingsManager.onlyLeaderboardEnabled.collectAsState(initial = true)

    val currentAccount by accountManager.currentAccount.collectAsState(initial = null)

    // Update user info from current account
    LaunchedEffect(currentAccount) {
        val user = currentAccount?.userInfo
        if (user != null) {
            vm.userId = user.id.toString()
            vm.isSupporter = user.isSupporter
            vm.isSupporterKnown = true
        } else {
            vm.userId = null
            vm.isSupporter = false
            vm.isSupporterKnown = false
        }
    }

    // Sync filter with default view once user info is available (only on first load or setting change)
    LaunchedEffect(defaultSearchView, vm.isSupporter, vm.isSupporterKnown) {
        if (!vm.isSupporterKnown) return@LaunchedEffect
        if (vm.lastSyncedDefaultView == defaultSearchView) return@LaunchedEffect
        vm.lastSyncedDefaultView = defaultSearchView
        val allowed = if (vm.isSupporter) {
            listOf("played", "recent", "favorite", "most_played", "all")
        } else {
            listOf("recent", "favorite", "most_played", "all")
        }
        val target = if (defaultSearchView in allowed) defaultSearchView else if (vm.isSupporter) "played" else "favorite"
        vm.filterMode = target
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val infoCoverEnabled by settingsManager.infoCoverEnabled.collectAsState(initial = true)
    val preferredMirror: String by settingsManager.preferredMirror.collectAsState(initial = "nerinyan")
    val previewManager = remember { SearchPreviewManager(context, musicController) }

    DisposableEffect(Unit) {
        onDispose {
            previewManager.release()
        }
    }

    // Stop preview when music starts playing (e.g. from MiniPlayer controls)
    val isMusicPlaying by musicController.isPlaying.collectAsState()
    LaunchedEffect(isMusicPlaying) {
        if (isMusicPlaying && previewManager.previewingId != null) {
            previewManager.stop()
        }
    }

    // Playlist dialog state
    val playlists by vm.db.playlistDao().getPlaylists().collectAsState(initial = emptyList())
    
    // Deletion state from shared ViewModel
    val pendingDeletions by deferredActionViewModel.pendingLibrarySetDeletions.collectAsState()

    val playlistTracks by vm.db.playlistDao().getAllPlaylistTracks().collectAsState(initial = emptyList())
    val playlistMembership = playlistTracks
        .groupBy { it.playlistId }
        .mapValues { entry -> entry.value.map { "${it.beatmapSetId}|${it.difficultyName}" }.toSet() }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var dialogTrack by remember { mutableStateOf<BeatmapEntity?>(null) }
    var dialogSelection by remember { mutableStateOf<Set<Long>>(emptySet()) }

    fun openPlaylistDialog(set: BeatmapSetData) {
        scope.launch {
            val track = withContext(Dispatchers.IO) {
                val tracks = vm.db.beatmapDao().getTracksForSet(set.id)
                if (tracks.isNotEmpty()) tracks.first()
                else vm.db.beatmapDao().getTracksByTitleArtist(set.title, set.artist).firstOrNull()
            }
            if (track != null) {
                dialogTrack = track
                dialogSelection = playlists
                    .filter { playlistMembership[it.id]?.contains("${track.beatmapSetId}|${track.difficultyName}") == true }
                    .map { it.id }
                    .toSet()
                showPlaylistDialog = true
            }
        }
    }

    // Effect to fetch info details when target changes and dialog is visible
    LaunchedEffect(vm.infoTarget, vm.infoDialogVisible) {
        if (vm.infoDialogVisible && vm.infoTarget != null) {
            vm.loadInfoPopup(context.getString(R.string.search_info_load_error))
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

    val genres = vm.searchService.genres.map { (id, name) ->
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

    // Define Unified Actions
    val actions = remember(accessToken, vm.downloadedBeatmapSetIds, vm.downloadedKeys, snackbarHostState) {
        BeatmapSetActions(
            onClick = { set ->
                if (set.isDownloaded) {
                    scope.launch {
                        // Get the beatmap entities for this set from database
                        val beatmaps = withContext(Dispatchers.IO) {
                            val tracks = vm.db.beatmapDao().getTracksForSet(set.id)
                            if (tracks.isEmpty()) {
                                vm.db.beatmapDao().getTracksByTitleArtist(set.title, set.artist)
                            } else {
                                tracks
                            }
                        }
                        if (beatmaps.isNotEmpty()) {
                            previewManager.stop()
                            musicController.playSong(beatmaps.first(), beatmaps)
                        } else {
                            // Fallback to preview if DB query fails? Or user data inconsistent
                            // Re-create simple object for preview
                            val compact = BeatmapsetCompact(set.id, set.title, set.artist, set.creator ?: "", Covers(set.coverUrl ?: "", set.coverUrl ?: ""), status = "unknown")
                            previewManager.playPreview(compact, preferredMirror)
                        }
                    }
                } else {
                    if (previewManager.previewingId == set.id) {
                        previewManager.stop()
                    } else {
                        val compact = BeatmapsetCompact(set.id, set.title, set.artist, set.creator ?: "", Covers(set.coverUrl ?: "", set.coverUrl ?: ""), status = "unknown")
                        previewManager.playPreview(compact, preferredMirror)
                    }
                }
            },
            onLongClick = { set ->
                val compact = BeatmapsetCompact(set.id, set.title, set.artist, set.creator ?: "", Covers(set.coverUrl ?: "", set.coverUrl ?: ""), status = "unknown")
                vm.infoTarget = compact
                vm.infoDialogVisible = true
            },
            onSecondaryAction = { set ->
                if (!set.isDownloaded && set.downloadProgress == null) {
                    scope.launch {
                        val downloadId = set.id
                        val mergeGroupIds = vm.mergeGroups[vm.searchService.mergeKey(set.toCompact())] ?: setOf(downloadId)

                        vm.downloadStates = vm.downloadStates + mergeGroupIds.associateWith {
                            DownloadProgress(
                                0,
                                context.getString(R.string.search_download_starting)
                            )
                        }
                        downloadService.downloadBeatmap(
                            beatmapSetId = downloadId,
                            accessToken = accessToken,
                            title = set.title,
                            artist = set.artist,
                            creator = set.creator ?: "",
                            genreId = null,
                            coversListUrl = set.coverUrl ?: ""
                        ).collect { state ->
                            when (state) {
                                is UnifiedDownloadState.Progress -> {
                                    val progress = DownloadProgress(state.progress, state.status)
                                    vm.downloadStates = vm.downloadStates + mergeGroupIds.associateWith { progress }
                                }
                                is UnifiedDownloadState.Success -> {
                                    val doneProgress = DownloadProgress(100, context.getString(R.string.search_download_done))
                                    vm.downloadStates = vm.downloadStates + mergeGroupIds.associateWith { doneProgress }
                                    delay(2000)
                                    vm.downloadStates = vm.downloadStates - mergeGroupIds
                                }
                                is UnifiedDownloadState.Error -> {
                                    Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                                    vm.downloadStates = vm.downloadStates - mergeGroupIds
                                }
                            }
                        }
                    }
                }
            },
            onSwipeLeft = { set ->
                if (set.isDownloaded) {
                    deferredActionViewModel.addPendingLibrarySet(set.id)
                }
            },
            onSwipeLeftRevert = { set ->
                deferredActionViewModel.removePendingLibrarySet(set.id)
            },
            onSwipeLeftConfirmed = { set ->
                // Actual deletion after timeout (already running in viewModelScope from BeatmapSetSwipeItem)
                val tracksToDelete = withContext(Dispatchers.IO) {
                    val tracks = vm.db.beatmapDao().getTracksForSet(set.id)
                    if (tracks.isEmpty()) vm.db.beatmapDao().getTracksByTitleArtist(set.title, set.artist)
                    else tracks
                }
                tracksToDelete.forEach { track ->
                    TrackService.deleteTrack(track, vm.db, context)
                }
                deferredActionViewModel.removePendingLibrarySet(set.id)
            },
            onSwipeLeftMessage = { set ->
                context.getString(R.string.snackbar_removed_from_library, set.title)
            },
            onSwipeRight = { set ->
                if (set.isDownloaded) {
                    openPlaylistDialog(set)
                }
            },
            swipeLeftIcon = Icons.Default.Delete,
            swipeRightIcon = Icons.Default.Add,
            snackbarHostState = snackbarHostState,
            coroutineScope = deferredActionViewModel.viewModelScope
        )
    }

    // Transform search results to BeatmapSetData
    val sortedResults = remember(vm.searchResults, vm.searchResultsMetadata, vm.filterMode) {
        if (vm.searchResultsMetadata.isNotEmpty()) {
            vm.searchResults.sortedBy { beatmap ->
                vm.searchResultsMetadata[beatmap.id]?.first ?: Int.MAX_VALUE
            }
        } else {
            vm.searchResults
        }
    }

    val beatmapSets = remember(sortedResults, vm.downloadStates, vm.searchResultsMetadata, vm.downloadedBeatmapSetIds, vm.downloadedKeys, previewManager.previewingId, pendingDeletions) {
        sortedResults
            .filter { it.id !in pendingDeletions }
            .map { map ->
                val metadata = vm.searchResultsMetadata[map.id]
            val progress = vm.downloadStates[map.id]
            val mapKey = "${map.title.trim().lowercase()}|${map.artist.trim().lowercase()}"
            val isDownloaded = vm.downloadedBeatmapSetIds.contains(map.id) || vm.downloadedKeys.contains(mapKey)

            BeatmapSetData(
                id = map.id,
                title = map.title,
                artist = map.artist,
                creator = map.creator,
                coverUrl = map.covers.listUrl,
                isExpandable = false,
                isDownloaded = isDownloaded,
                downloadProgress = progress?.progress,
                ranking = metadata?.first,
                playCount = metadata?.second,
                genreId = map.genreId
            )
        }
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
            var flicking by remember { mutableStateOf(false) }
            LaunchedEffect(vm.refreshTick, vm.isRefreshing) {
                flicking = if (vm.isRefreshing) true else false
            }

            val indicatorOffset by animateDpAsState(
                targetValue = if (vm.isRefreshing) 32.dp else 8.dp,
                label = "pullRefreshOffset"
            )
            val pullRefreshState = rememberPullRefreshState(
                refreshing = vm.isRefreshing,
                onRefresh = {
                    scope.launch {
                        if (vm.isRefreshing) return@launch
                        vm.isRefreshing = true
                        vm.refreshTick++
                        try {
                            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                                listState.animateScrollToItem(0)
                            }
                            if (vm.filterMode == "recent") {
                                val uid = vm.userId ?: return@launch
                                val fresh = vm.repository.fetchRecentPlays(
                                    accessToken = accessToken,
                                    userId = uid
                                )
                                vm.db.recentPlayDao().mergeNewPlays(fresh)
                                val (groupedItems, mergeGroupsResult, timestamps) = vm.searchService.loadRecentFiltered(accessToken, vm.userId, vm.searchQuery, vm.selectedGenreId)
                                vm.mergeGroups = mergeGroupsResult
                                vm.recentGroupedResults = groupedItems
                                vm.searchResults = emptyList() // Clear regular results for recent mode
                                vm.currentCursor = null
                                vm.searchResultsMetadata = emptyMap()
                                vm.recentTimestamps = timestamps
                            } else {
                                val result = vm.repository.getPlayedBeatmaps(
                                    accessToken = accessToken,
                                    genreId = vm.selectedGenreId,
                                    cursorString = null,
                                    searchQuery = vm.searchQuery.trim().ifEmpty { null },
                                    filterMode = vm.filterMode,
                                    playedFilterMode = playedFilterMode,
                                    userId = vm.userId,
                                    isSupporter = vm.isSupporter,
                                    searchAny = !onlyLeaderboardEnabled,
                                    forceRefresh = true
                                )
                                val incoming = vm.searchService.dedupeByTitle(result.beatmaps, vm.downloadedBeatmapSetIds, vm.downloadedKeys)
                                vm.mergeGroups = vm.searchService.buildMergeGroups(result.beatmaps)
                                vm.searchResults = incoming
                                vm.currentCursor = result.cursor
                                vm.searchResultsMetadata = vm.searchService.filterMetadataFor(incoming, result.metadata)
                            }
                        } catch (e: Exception) {
                            Log.e("SearchScreen", "Refresh failed", e)
                        } finally {
                            vm.isRefreshing = false
                        }
                    }
                }
            )

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
                    // Item 1: Search Bar (1 unit tall)
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            TextField(
                                value = vm.searchQuery,
                                onValueChange = { vm.searchQuery = it },
                                modifier = Modifier.fillMaxSize(),
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
                                        if (vm.searchQuery.isNotEmpty()) {
                                            IconButton(onClick = {
                                                vm.searchQuery = ""
                                                scope.launch {
                                                    try {
                                                        if (vm.filterMode != "recent") {
                                                            val result = vm.repository.getPlayedBeatmaps(
                                                                accessToken, vm.selectedGenreId, null, null,
                                                                vm.filterMode, playedFilterMode, vm.userId, vm.isSupporter
                                                            )
                                                            val deduped = vm.searchService.dedupeByTitle(result.beatmaps, vm.downloadedBeatmapSetIds, vm.downloadedKeys)
                                                            vm.mergeGroups = vm.searchService.buildMergeGroups(result.beatmaps)
                                                            vm.searchResults = deduped
                                                            vm.currentCursor = result.cursor
                                                            vm.searchResultsMetadata = vm.searchService.filterMetadataFor(deduped, result.metadata)
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

                                        var filterMenuExpanded by remember { mutableStateOf(false) }
                                        val isSupporterAllowed = vm.isSupporter
                                        val options = if (isSupporterAllowed) {
                                            listOf("played", "recent", "favorite", "most_played", "all")
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
                                            "recent" to androidx.compose.ui.graphics.Color(0xFF7E57C2),
                                            "favorite" to androidx.compose.ui.graphics.Color(0xFFFFD059),
                                            "most_played" to androidx.compose.ui.graphics.Color(0xFF483AC2),
                                            "all" to androidx.compose.ui.graphics.Color(0xFFF748AE)
                                        )
                                        val contentColors = mapOf(
                                            "played" to MaterialTheme.colorScheme.onPrimary,
                                            "recent" to Color.White,
                                            "favorite" to Color.Black,
                                            "most_played" to Color.White,
                                            "all" to Color.White
                                        )
                                        val currentColor = optionColors[vm.filterMode] ?: MaterialTheme.colorScheme.primary
                                        val currentContentColor = contentColors[vm.filterMode] ?: MaterialTheme.colorScheme.primary

                                        OutlinedButton(
                                            onClick = { filterMenuExpanded = true },
                                            modifier = Modifier.width(98.dp).height(36.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = currentColor,
                                                contentColor = currentContentColor
                                            ),
                                            contentPadding = PaddingValues(horizontal = 4.dp)
                                        ) {
                                            Text(
                                                text = optionLabels[vm.filterMode] ?: "",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                maxLines = 1
                                            )
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
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
                                                        vm.filterMode = mode
                                                        scope.launch {
                                                            settingsManager.saveDefaultSearchView(mode)
                                                            vm.lastSyncedDefaultView = mode
                                                            vm.currentCursor = null
                                                            if (mode == "recent") {
                                                                val (groupedItems, mergeGroupsResult, timestamps) = vm.searchService.loadRecentFiltered(accessToken, vm.userId, vm.searchQuery, vm.selectedGenreId)
                                                                vm.mergeGroups = mergeGroupsResult
                                                                vm.recentGroupedResults = groupedItems
                                                                vm.searchResults = emptyList()
                                                                vm.searchResultsMetadata = emptyMap()
                                                                vm.recentTimestamps = timestamps
                                                            } else {
                                                                val result = vm.repository.getPlayedBeatmaps(
                                                                    accessToken, vm.selectedGenreId, null,
                                                                    vm.searchQuery.trim().ifEmpty { null },
                                                                    mode, playedFilterMode, vm.userId, vm.isSupporter, !onlyLeaderboardEnabled
                                                                )
                                                                val deduped = vm.searchService.dedupeByTitle(result.beatmaps, vm.downloadedBeatmapSetIds, vm.downloadedKeys)
                                                                vm.mergeGroups = vm.searchService.buildMergeGroups(result.beatmaps)
                                                                vm.searchResults = deduped
                                                                vm.currentCursor = result.cursor
                                                                vm.searchResultsMetadata = vm.searchService.filterMetadataFor(deduped, result.metadata)
                                                            }
                                                            // Update load key so initial LaunchedEffect doesn't duplicate
                                                            vm.lastInitialLoadKey = "$accessToken|${vm.filterMode}|${vm.userId}|$onlyLeaderboardEnabled"
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
                                                vm.currentCursor = null
                                                if (vm.filterMode == "recent") {
                                                    val (groupedItems, mergeGroupsResult, timestamps) = vm.searchService.loadRecentFiltered(accessToken, vm.userId, vm.searchQuery, vm.selectedGenreId)
                                                    vm.mergeGroups = mergeGroupsResult
                                                    vm.recentGroupedResults = groupedItems
                                                    vm.searchResults = emptyList()
                                                    vm.searchResultsMetadata = emptyMap()
                                                    vm.recentTimestamps = timestamps
                                                } else {
                                                    val result = vm.repository.getPlayedBeatmaps(
                                                        accessToken, vm.selectedGenreId, null, vm.searchQuery.trim(),
                                                        vm.filterMode, playedFilterMode, vm.userId, vm.isSupporter, !onlyLeaderboardEnabled
                                                    )
                                                    val deduped = vm.searchService.dedupeByTitle(result.beatmaps, vm.downloadedBeatmapSetIds, vm.downloadedKeys)
                                                    vm.mergeGroups = vm.searchService.buildMergeGroups(result.beatmaps)
                                                    vm.searchResults = deduped
                                                    vm.currentCursor = result.cursor
                                                    vm.searchResultsMetadata = vm.searchService.filterMetadataFor(deduped, result.metadata)
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
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    // Item 2: Genre bar (1 unit tall)
                    item {
                        val usingMostPlayed = vm.searchResultsMetadata.isNotEmpty()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (!usingMostPlayed) {
                                LazyRow(modifier = Modifier.fillMaxWidth()) {
                                    items(genres) { (id, name) ->
                                        Button(
                                            onClick = {
                                                vm.selectedGenreId = if (vm.selectedGenreId == id) null else id
                                                vm.currentCursor = null
                                                scope.launch {
                                                    try {
                                                        if (vm.filterMode == "recent") {
                                                            val (groupedItems, mergeGroupsResult, timestamps) = vm.searchService.loadRecentFiltered(accessToken, vm.userId, vm.searchQuery, vm.selectedGenreId)
                                                            vm.mergeGroups = mergeGroupsResult
                                                            vm.recentGroupedResults = groupedItems
                                                            vm.searchResults = emptyList()
                                                            vm.searchResultsMetadata = emptyMap()
                                                            vm.recentTimestamps = timestamps
                                                        } else {
                                                            val result = vm.repository.getPlayedBeatmaps(
                                                                accessToken, vm.selectedGenreId, null,
                                                                vm.searchQuery.trim().ifEmpty { null },
                                                                vm.filterMode, playedFilterMode, vm.userId, vm.isSupporter, !onlyLeaderboardEnabled
                                                            )
                                                            val deduped = vm.searchService.dedupeByTitle(result.beatmaps, vm.downloadedBeatmapSetIds, vm.downloadedKeys)
                                                            vm.mergeGroups = vm.searchService.buildMergeGroups(result.beatmaps)
                                                            vm.searchResults = deduped
                                                            vm.currentCursor = result.cursor
                                                            vm.searchResultsMetadata = vm.searchService.filterMetadataFor(deduped, result.metadata)
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("SearchScreen", "Genre filter failed", e)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.padding(end = 8.dp).height(40.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (vm.selectedGenreId == id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = if (vm.selectedGenreId == id) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        ) {
                                            Text(name, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = stringResource(id = R.string.search_genre_not_available),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    // Search Results
                    if (vm.filterMode == "recent") {
                        val filteredRecentItems = vm.recentGroupedResults.filter { item ->
                            when (item) {
                                is RecentItem.Song -> item.beatmapset.id !in pendingDeletions
                                else -> true
                            }
                        }
                        items(filteredRecentItems) { item ->
                            when (item) {
                                is RecentItem.Header -> {
                                    // Timestamp header/divider
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(60.dp) // Exact match for unified height
                                            .padding(vertical = 12.dp, horizontal = 16.dp)
                                    ) {
                                        Text(
                                            text = item.timestamp,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        HorizontalDivider(
                                            modifier = Modifier.padding(top = 4.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                }
                                is RecentItem.Song -> {
                                    val map = item.beatmapset
                                    val progress = vm.downloadStates[map.id]
                                    val mapKey = "${map.title.trim().lowercase()}|${map.artist.trim().lowercase()}"
                                    val isDownloaded = vm.downloadedBeatmapSetIds.contains(map.id) || vm.downloadedKeys.contains(mapKey)
                                    val highlight = previewManager.previewingId == map.id

                                    val setData = BeatmapSetData(
                                        id = map.id,
                                        title = map.title,
                                        artist = map.artist,
                                        creator = map.creator,
                                        coverUrl = map.covers.listUrl,
                                        isExpandable = false,
                                        isDownloaded = isDownloaded,
                                        downloadProgress = progress?.progress,
                                        lastPlayed = formatPlayedAtTimestamp(item.playedAt),
                                        genreId = map.genreId
                                    )

                                    val previewBrush = if (highlight) {
                                        Brush.horizontalGradient(
                                            0.0f to Color.Transparent,
                                            previewManager.progress to Color.Transparent,
                                            previewManager.progress to MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                            1.0f to MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                        )
                                    } else null

                                    if (isDownloaded) {
                                        BeatmapSetSwipeItem(
                                            swipeActions = BeatmapSetSwipeActions(
                                                onDelete = {
                                                    deferredActionViewModel.addPendingLibrarySet(map.id)
                                                },
                                                onDeleteRevert = {
                                                    deferredActionViewModel.removePendingLibrarySet(map.id)
                                                },
                                                onDeleteConfirmed = {
                                                    // Actual deletion after timeout (already running in viewModelScope from BeatmapSetSwipeItem)
                                                    val tracksToDelete = withContext(Dispatchers.IO) {
                                                        val tracks = vm.db.beatmapDao().getTracksForSet(map.id)
                                                        if (tracks.isEmpty()) vm.db.beatmapDao().getTracksByTitleArtist(map.title, map.artist)
                                                        else tracks
                                                    }
                                                    tracksToDelete.forEach { track ->
                                                        TrackService.deleteTrack(track, vm.db, context)
                                                    }
                                                    deferredActionViewModel.removePendingLibrarySet(map.id)
                                                },
                                                onDeleteMessage = context.getString(R.string.snackbar_removed_from_library, map.title),
                                                onSwipeRight = { openPlaylistDialog(setData) }
                                            ),
                                            backgroundBrush = previewBrush,
                                            backgroundColor = Color.Transparent,
                                            startToEndIcon = Icons.Default.Add,
                                            endToStartIcon = Icons.Default.Delete,
                                            snackbarHostState = snackbarHostState,
                                            externalScope = deferredActionViewModel.viewModelScope
                                        ) {
                                            BeatmapSetItem(
                                                set = setData,
                                                actions = actions,
                                                highlight = highlight,
                                                backgroundBrush = previewBrush,
                                                backgroundColor = Color.Transparent
                                            )
                                        }
                                    } else {
                                        BeatmapSetItem(
                                            set = setData,
                                            actions = actions,
                                            highlight = highlight,
                                            backgroundBrush = previewBrush,
                                            backgroundColor = Color.Transparent
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Non-recent modes
                        beatmapSetList(
                            sets = beatmapSets,
                            actions = actions,
                            config = BeatmapSetListConfig(
                                showDividers = false,
                                expandedIds = vm.expandedBeatmapSets,
                                onExpansionChanged = { id, isExpanded ->
                                    vm.expandedBeatmapSets = if (isExpanded) {
                                        vm.expandedBeatmapSets + id
                                    } else {
                                        vm.expandedBeatmapSets - id
                                    }
                                }
                            ),
                            highlightedSetId = previewManager.previewingId,
                            backgroundBrush = { set ->
                                if (previewManager.previewingId == set.id) {
                                    Brush.horizontalGradient(
                                        0.0f to Color.Transparent,
                                        previewManager.progress to Color.Transparent,
                                        previewManager.progress to MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                        1.0f to MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                    )
                                } else null
                            },
                            swipeEnabled = { it.isDownloaded }
                        )
                    }

                    // Pagination / Load More
                    if (vm.filterMode != "recent" && vm.searchResults.isNotEmpty() && vm.currentCursor != null) {
                        item {
                            Button(
                                onClick = {
                                    scope.launch {
                                        vm.isLoadingMore = true
                                        try {
                                            val result = vm.repository.getPlayedBeatmaps(
                                                accessToken,
                                                vm.selectedGenreId,
                                                vm.currentCursor,
                                                vm.searchQuery.trim().ifEmpty { null },
                                                vm.filterMode,
                                                playedFilterMode,
                                                vm.userId,
                                                vm.isSupporter,
                                                !onlyLeaderboardEnabled
                                            )
                                            if (result.beatmaps.isNotEmpty()) {
                                                val merged =
                                                    vm.searchService.mergeByTitle(vm.searchResults, result.beatmaps, vm.downloadedBeatmapSetIds, vm.downloadedKeys)
                                                val mergedIds = merged.map { it.id }.toSet()
                                                vm.mergeGroups = vm.searchService.unionMergeGroups(
                                                    vm.mergeGroups,
                                                    vm.searchService.buildMergeGroups(result.beatmaps)
                                                )
                                                vm.searchResults = merged
                                                vm.currentCursor = result.cursor
                                                vm.searchResultsMetadata =
                                                    (vm.searchResultsMetadata + result.metadata).filterKeys { it in mergedIds }
                                            } else {
                                                vm.currentCursor = null
                                            }
                                        } catch (e: Exception) {
                                            Log.e("SearchScreen", "Load more failed", e)
                                        } finally {
                                            vm.isLoadingMore = false
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp) // Exact match for unified height
                                    .padding(8.dp),
                                enabled = !vm.isLoadingMore
                            ) {
                                Text(if (vm.isLoadingMore) stringResource(id = R.string.search_loading) else stringResource(id = R.string.search_load_more))
                            }
                        }
                    }

                }
                PullRefreshIndicator(
                    refreshing = vm.isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = indicatorOffset),
                    contentColor = MaterialTheme.colorScheme.primary
                )

                DraggableScrollbar(
                    state = listState,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }

        if (vm.infoDialogVisible && vm.infoTarget != null) {
            val target = vm.infoTarget!!
            val targetKey = "${target.title.trim().lowercase()}|${target.artist.trim().lowercase()}"
            val downloaded = vm.downloadedBeatmapSetIds.contains(target.id) || vm.downloadedKeys.contains(targetKey)
            // Info Popup
            InfoPopup(
                visible = true,
                onDismiss = { vm.infoDialogVisible = false },
                target = target,
                sets = vm.infoSets,
                loading = vm.infoLoading,
                error = vm.infoError,
                downloadedIds = vm.downloadedBeatmapSetIds,
                downloadedKeys = vm.downloadedKeys,
                config = InfoPopupConfig(
                    infoCoverEnabled = infoCoverEnabled,
                    onDownloadClick = { beatmapset ->
                        scope.launch {
                            val downloadId = beatmapset.id
                            val mergeGroupIds = vm.mergeGroups[vm.searchService.mergeKey(beatmapset)] ?: setOf(downloadId)

                            vm.downloadStates = vm.downloadStates + mergeGroupIds.associateWith { DownloadProgress(0, context.getString(R.string.search_download_starting)) }

                            downloadService.downloadBeatmap(
                                beatmapSetId = downloadId,
                                accessToken = accessToken,
                                title = beatmapset.title,
                                artist = beatmapset.artist,
                                creator = beatmapset.creator,
                                genreId = beatmapset.genreId,
                                coversListUrl = beatmapset.covers.listUrl
                            ).collect { state ->
                                when (state) {
                                    is UnifiedDownloadState.Progress -> {
                                        val progress = DownloadProgress(state.progress, state.status)
                                        vm.downloadStates = vm.downloadStates + mergeGroupIds.associateWith { progress }
                                    }
                                    is UnifiedDownloadState.Success -> {
                                        val doneProgress = DownloadProgress(100, context.getString(R.string.search_download_done))
                                        vm.downloadStates = vm.downloadStates + mergeGroupIds.associateWith { doneProgress }
                                        kotlinx.coroutines.delay(2000)
                                        vm.downloadStates = vm.downloadStates - mergeGroupIds
                                    }
                                    is UnifiedDownloadState.Error -> {
                                        Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                                        vm.downloadStates = vm.downloadStates - mergeGroupIds
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
                                        vm.downloadStates =
                                            vm.downloadStates + (beatmapset.id to DownloadProgress(
                                                state.progress,
                                                state.status
                                            ))
                                    }
                                    is UnifiedDownloadState.Success -> {
                                        vm.downloadStates =
                                            vm.downloadStates + (beatmapset.id to DownloadProgress(
                                                100,
                                                context.getString(R.string.search_download_done)
                                            ))
                                        kotlinx.coroutines.delay(2000)
                                        vm.downloadStates = vm.downloadStates - beatmapset.id
                                    }
                                    is UnifiedDownloadState.Error -> {
                                        Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                                        vm.downloadStates = vm.downloadStates - beatmapset.id
                                    }
                                }
                            }
                        }
                    },
                    onPlayClick = { beatmapset ->
                        if (downloaded) {
                            scope.launch {
                                val tracks = vm.db.beatmapDao().getTracksForSet(beatmapset.id)
                                if (tracks.isNotEmpty()) {
                                    previewManager.stop()
                                    val allDownloaded = vm.db.beatmapDao().getAllBeatmaps().first()
                                    musicController.playSong(tracks[0], allDownloaded)
                                }
                            }
                        } else {
                            previewManager.playPreview(beatmapset, preferredMirror)
                        }
                    }
                )
            )
        }

        if (showPlaylistDialog && dialogTrack != null) {
            val track = dialogTrack!!
            val playlistOptions = playlists.map { PlaylistOption(it.id, it.name) }

            PlaylistSelectorDialog(
                playlists = playlistOptions,
                selectedPlaylistIds = dialogSelection,
                onSelectionChanged = { dialogSelection = it },
                onAddToPlaylist = { playlistId, beatmapSetId ->
                    scope.launch {
                        TrackService.addTrackToPlaylist(playlistId, beatmapSetId, track.title, track.artist, track.difficultyName, vm.db)
                    }
                },
                onRemoveFromPlaylist = { playlistId, beatmapSetId ->
                    scope.launch {
                        TrackService.removeTrackFromPlaylist(playlistId, beatmapSetId, track.difficultyName, vm.db)
                    }
                },
                beatmapSetId = track.beatmapSetId,
                onDismiss = { showPlaylistDialog = false }
            )
        }

        // Reset results on logout
        LaunchedEffect(accessToken) {
            if (accessToken == null) {
                vm.resetOnLogout()
            }
        }

        // Initial Load - Show cached data immediately, then refresh
        LaunchedEffect(accessToken, vm.filterMode, vm.userId, onlyLeaderboardEnabled, playedFilterMode) {
            if (accessToken != null) {
                vm.performInitialLoad(
                    accessToken = accessToken,
                    mode = vm.filterMode,
                    uid = vm.userId,
                    onlyLeaderboard = onlyLeaderboardEnabled,
                    playedMode = playedFilterMode,
                    searchAny = !onlyLeaderboardEnabled
                )
            }
        }
    }
}
