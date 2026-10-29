package com.mosu.app.ui.search

import android.util.Log
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mosu.app.R
import com.mosu.app.data.api.model.BeatmapsetCompact
import com.mosu.app.data.api.model.Covers
import com.mosu.app.domain.model.formatPlayedAtTimestamp
import com.mosu.app.domain.search.RecentItem
import com.mosu.app.player.MusicController
import com.mosu.app.ui.components.BeatmapSetActions
import com.mosu.app.ui.components.BeatmapSetData
import com.mosu.app.ui.components.BeatmapSetItem
import com.mosu.app.ui.components.BeatmapSetList
import com.mosu.app.ui.components.BeatmapSetListConfig
import com.mosu.app.ui.components.InfoPopup
import com.mosu.app.ui.components.InfoPopupConfig
import com.mosu.app.ui.components.GenreFilter
import com.mosu.app.ui.components.GenreRail
import com.mosu.app.ui.components.BeatmapSetSwipeActions
import com.mosu.app.ui.components.BeatmapSetSwipeItem
import com.mosu.app.ui.components.PlaylistOption
import com.mosu.app.ui.components.PlaylistSelectorDialog
import com.mosu.app.ui.components.ScoreModeFilterIcons
import com.mosu.app.ui.components.beatmapSetList
import com.mosu.app.ui.components.toBeatmapSetData
import com.mosu.app.ui.components.toBeatmapSetScoreData
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.data.services.TrackService
import com.mosu.app.domain.download.DownloadTask
import com.mosu.app.domain.search.usesFilterProvidedBeatmapset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewModelScope
import com.mosu.app.ui.DeferredActionViewModel

private fun Map<Long, DownloadTask>.forSong(
    setId: Long,
    title: String,
    artist: String
): DownloadTask? {
    return this[setId] ?: values.firstOrNull {
        it.title.equals(title, ignoreCase = true) &&
            it.artist.equals(artist, ignoreCase = true)
    }
}

@Composable
private fun DownloadTasksContent(
    downloadManager: com.mosu.app.domain.download.DownloadManager,
    tasks: Map<Long, DownloadTask>,
    accessToken: String?,
    modifier: Modifier = Modifier
) {
    com.mosu.app.ui.components.TaskView(
        tasks = tasks,
        onCancel = { downloadManager.cancel(it) },
        onRetry = { downloadManager.retry(it, accessToken) },
        onPauseAll = { downloadManager.pauseAll() },
        onResumeAll = { downloadManager.resumeAll() },
        onResume = { downloadManager.resume(it) },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    deferredActionViewModel: DeferredActionViewModel,
    accessToken: String?,
    accountManager: com.mosu.app.data.AccountManager,
    settingsManager: com.mosu.app.data.SettingsManager,
    musicController: MusicController,
    downloadManager: com.mosu.app.domain.download.DownloadManager,
    scrollToTop: Boolean = false,
    onScrolledToTop: () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    isLandscape: Boolean = false,
    genreBarEnabled: Boolean = true,
    landscapeTaskViewRequest: Int = 0,
    onLandscapeTaskViewRequestConsumed: () -> Unit = {}
) {
    val vm = viewModel
    LaunchedEffect(genreBarEnabled) {
        if (!genreBarEnabled) vm.selectedGenreIds = emptySet()
    }

    // Played filter mode from settings
    val defaultSearchView by settingsManager.defaultSearchView.collectAsState(initial = "played")
    val playedFilterMode by settingsManager.playedFilterMode.collectAsState(initial = "url")
    val onlyLeaderboardEnabled by settingsManager.onlyLeaderboardEnabled.collectAsState(initial = true)
    val recentPlayHistoryEnabled by settingsManager.recentPlayHistoryEnabled.collectAsState(initial = false)
    val scoreModeFilter by settingsManager.scoreModeFilter.collectAsState(initial = "osu")

    LaunchedEffect(scoreModeFilter) {
        vm.scoreModeFilter = scoreModeFilter
    }

    val currentUserInfo by accountManager.currentUserInfo.collectAsState(initial = null)

    // Update user info from the signed-in account
    LaunchedEffect(currentUserInfo) {
        val user = currentUserInfo
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
            listOf("played", "recent", "favorite", "most_played", "top_play", "all")
        } else {
            listOf("recent", "favorite", "most_played", "top_play", "all")
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
    val playlists by remember { vm.db.playlistDao().getPlaylists() }.collectAsState(initial = emptyList())
    val searchHistory by remember { vm.db.searchHistoryDao().getRecentSearches() }.collectAsState(initial = emptyList())
    
    // Deletion state from shared ViewModel
    val pendingDeletions by deferredActionViewModel.pendingLibrarySetDeletions.collectAsState()
    
    // Download tasks from global manager
    val downloadTasks by downloadManager.tasks.collectAsState()
    
    val activeDownloadCount = remember(downloadTasks) {
        downloadTasks.values.count { 
            it.status == com.mosu.app.domain.download.DownloadStatus.Downloading || 
            it.status == com.mosu.app.domain.download.DownloadStatus.Queued || 
            it.status == com.mosu.app.domain.download.DownloadStatus.Extracting ||
            it.status == com.mosu.app.domain.download.DownloadStatus.Paused
        }
    }

    val playlistTracks by remember { vm.db.playlistDao().getAllPlaylistTracks() }.collectAsState(initial = emptyList())
    val playlistMembership = playlistTracks
        .groupBy { it.playlistId }
        .mapValues { entry -> entry.value.map { "${it.beatmapSetId}|${it.difficultyName}" }.toSet() }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showTaskView by remember { mutableStateOf(false) }
    LaunchedEffect(landscapeTaskViewRequest) {
        if (landscapeTaskViewRequest > 0) {
            showTaskView = true
            onLandscapeTaskViewRequestConsumed()
        }
    }
    var dialogTrack by remember { mutableStateOf<BeatmapEntity?>(null) }
    var dialogSelection by remember { mutableStateOf<Set<Long>>(emptySet()) }

    suspend fun runSearch(query: String, recordHistory: Boolean = true, forceRefresh: Boolean = false) {
        val token = accessToken ?: return
        val normalizedQuery = query.replace(Regex("[\\p{Cntrl}]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        val shouldForceRefresh = forceRefresh
        val requestMode = vm.filterMode
        val requestScoreMode = vm.scoreModeFilter
        val requestUserId = vm.userId
        val requestOnlyLeaderboard = onlyLeaderboardEnabled
        val requestPlayedMode = playedFilterMode
        val requestShowPlayHistory = recentPlayHistoryEnabled
        val requestGenreIds = vm.selectedGenreIds
        val requestLoadKey = vm.initialLoadKey(
            accessToken = token,
            mode = requestMode,
            scoreMode = requestScoreMode,
            uid = requestUserId,
            onlyLeaderboard = requestOnlyLeaderboard,
            playedMode = requestPlayedMode,
            searchAny = !requestOnlyLeaderboard,
            showPlayHistory = requestShowPlayHistory,
            genreIds = requestGenreIds
        )
        try {
            vm.cancelInitialLoad()
            vm.currentCursor = null
            if (recordHistory && normalizedQuery.isNotEmpty()) {
                vm.recordSearchHistory(normalizedQuery)
            }
            if (requestMode == "recent") {
                val (groupedItems, mergeGroupsResult, timestamps) = vm.searchService.loadRecentFiltered(
                    token,
                    requestUserId,
                    normalizedQuery,
                    requestGenreIds,
                    requestScoreMode,
                    showPlayHistory = requestShowPlayHistory
                )
                vm.mergeGroups = mergeGroupsResult
                vm.recentGroupedResults = groupedItems
                vm.searchResults = emptyList()
                vm.searchResultsMetadata = emptyMap()
                vm.searchScoreResults = emptyMap()
                vm.recentTimestamps = timestamps
            } else {
                val result = vm.repository.getPlayedBeatmaps(
                    accessToken = token,
                    genreIds = requestGenreIds,
                    cursorString = null,
                    searchQuery = normalizedQuery.ifEmpty { null },
                    filterMode = requestMode,
                    playedFilterMode = requestPlayedMode,
                    userId = requestUserId,
                    isSupporter = vm.isSupporter,
                    searchAny = !requestOnlyLeaderboard,
                    forceRefresh = shouldForceRefresh,
                    scoreMode = requestScoreMode
                )
                val deduped = vm.searchService.dedupeSearchResultsForMode(
                    list = result.beatmaps,
                    downloadedBeatmapSetIds = vm.downloadedBeatmapSetIds,
                    filterMode = requestMode
                )
                vm.mergeGroups = vm.searchService.buildMergeGroups(result.beatmaps)
                vm.searchResults = deduped
                vm.currentCursor = result.cursor
                vm.searchResultsMetadata = vm.searchService.filterMetadataFor(deduped, result.metadata)
                vm.searchScoreResults = result.scoreResults
            }
            vm.markInitialLoadHandled(requestLoadKey)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("SearchScreen", "Search failed", e)
        }
    }

    var appliedGenreIds by remember { mutableStateOf(vm.selectedGenreIds) }

    fun applyGenreSelection(genreIds: Set<Int>) {
        if (vm.selectedGenreIds == genreIds) return
        vm.selectedGenreIds = genreIds
        vm.currentCursor = null
    }

    LaunchedEffect(vm.selectedGenreIds) {
        if (vm.selectedGenreIds == appliedGenreIds) return@LaunchedEffect
        appliedGenreIds = vm.selectedGenreIds
        runSearch(vm.searchQuery, recordHistory = false, forceRefresh = true)
    }

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

    // Define Unified Actions
    val actions = remember(
        accessToken,
        vm.downloadedBeatmapSetIds,
        vm.downloadedKeys,
        vm.scoreModeFilter,
        vm.filterMode,
        snackbarHostState
    ) {
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
                    if (vm.filterMode.usesFilterProvidedBeatmapset()) {
                        downloadManager.enqueue(
                            setId = set.id,
                            title = set.title,
                            artist = set.artist,
                            creator = set.creator ?: "",
                            accessToken = accessToken,
                            genreId = set.genreId,
                            coverUrl = set.coverUrl
                        )
                    } else {
                        scope.launch {
                            val target = try {
                                vm.repository.findMostPlayedBeatmapsetByTitleArtist(
                                    title = set.title,
                                    artist = set.artist,
                                    scoreMode = vm.scoreModeFilter
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(
                                    "SearchScreen",
                                    "Failed to resolve most-played download target for ${set.title}",
                                    e
                                )
                                null
                            }

                            if (target == null) {
                                snackbarHostState?.showSnackbar(
                                    context.getString(
                                        R.string.search_download_target_resolve_failed,
                                        set.title
                                    )
                                )
                                return@launch
                            }

                            downloadManager.enqueue(
                                setId = target.id,
                                title = target.title,
                                artist = target.artist,
                                creator = target.creator,
                                accessToken = accessToken,
                                genreId = target.genreId,
                                coverUrl = target.covers.listUrl
                            )
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

    val beatmapSets = remember(
        sortedResults,
        downloadTasks,
        vm.searchResultsMetadata,
        vm.searchScoreResults,
        vm.downloadedBeatmapSetIds,
        vm.downloadedKeys,
        previewManager.previewingId,
        pendingDeletions,
        vm.filterMode
    ) {
        sortedResults
            .map { map ->
                val metadata = vm.searchResultsMetadata[map.id]
                val suppliedByFilter = vm.filterMode.usesFilterProvidedBeatmapset()
                val task = if (suppliedByFilter) {
                    downloadTasks[map.id]
                } else {
                    downloadTasks.forSong(map.id, map.title, map.artist)
                }
                val mapKey = "${map.title.trim().lowercase()}|${map.artist.trim().lowercase()}"
                val isActuallyDownloaded = if (suppliedByFilter) {
                    vm.downloadedBeatmapSetIds.contains(map.id)
                } else {
                    vm.downloadedBeatmapSetIds.contains(map.id) ||
                        vm.downloadedKeys.contains(mapKey)
                }
                val isDownloaded = isActuallyDownloaded && map.id !in pendingDeletions
                
                val showProgress = task != null && 
                    (task.status == com.mosu.app.domain.download.DownloadStatus.Downloading || 
                     task.status == com.mosu.app.domain.download.DownloadStatus.Queued || 
                     task.status == com.mosu.app.domain.download.DownloadStatus.Extracting)

                BeatmapSetData(
                    id = map.id,
                    title = map.title,
                    artist = map.artist,
                    creator = map.creator,
                    coverUrl = map.covers.listUrl,
                    isExpandable = false,
                    isDownloaded = isDownloaded,
                    downloadProgress = if (showProgress) task?.progress else null,
                    ranking = metadata?.first,
                    playCount = metadata?.second,
                    genreId = map.genreId,
                    score = vm.searchScoreResults[map.id]?.toBeatmapSetScoreData()
                )
            }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (isLandscape && genreBarEnabled) {
            GenreRail(
                selectedGenreIds = vm.selectedGenreIds,
                onGenreSelected = ::applyGenreSelection,
                modifier = Modifier.fillMaxHeight()
            )
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
        if (!isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.search_title),
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
                )

                Box {
                    IconButton(onClick = { showTaskView = true }) {
                        BadgedBox(
                            badge = {
                                if (activeDownloadCount > 0) {
                                    Badge {
                                        Text(activeDownloadCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Task View"
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showTaskView,
                        onDismissRequest = { showTaskView = false },
                        modifier = Modifier.width(320.dp)
                    ) {
                        Box(modifier = Modifier.heightIn(max = 400.dp)) {
                            DownloadTasksContent(
                                downloadManager = downloadManager,
                                tasks = downloadTasks,
                                accessToken = accessToken,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        if (isLandscape && showTaskView) {
            Dialog(
                onDismissRequest = { showTaskView = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier.widthIn(min = 320.dp, max = 400.dp),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 6.dp
                ) {
                    Box(modifier = Modifier.heightIn(max = 480.dp)) {
                        DownloadTasksContent(
                            downloadManager = downloadManager,
                            tasks = downloadTasks,
                            accessToken = accessToken,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

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
                                val (groupedItems, mergeGroupsResult, timestamps) = vm.searchService.loadRecentFiltered(
                                    accessToken,
                                    vm.userId,
                                    vm.searchQuery,
                                    vm.selectedGenreIds,
                                    vm.scoreModeFilter,
                                    showPlayHistory = recentPlayHistoryEnabled
                                )
                                vm.mergeGroups = mergeGroupsResult
                                vm.recentGroupedResults = groupedItems
                                vm.searchResults = emptyList() // Clear regular results for recent mode
                                vm.currentCursor = null
                                vm.searchResultsMetadata = emptyMap()
                                vm.searchScoreResults = emptyMap()
                                vm.recentTimestamps = timestamps
                            } else {
                                val result = vm.repository.getPlayedBeatmaps(
                                    accessToken = accessToken,
                                    genreIds = vm.selectedGenreIds,
                                    cursorString = null,
                                    searchQuery = vm.searchQuery.trim().ifEmpty { null },
                                    filterMode = vm.filterMode,
                                    playedFilterMode = playedFilterMode,
                                    userId = vm.userId,
                                    isSupporter = vm.isSupporter,
                                    searchAny = !onlyLeaderboardEnabled,
                                    forceRefresh = true,
                                    scoreMode = vm.scoreModeFilter
                                )
                                val incoming = vm.searchService.dedupeSearchResultsForMode(
                                    list = result.beatmaps,
                                    downloadedBeatmapSetIds = vm.downloadedBeatmapSetIds,
                                    filterMode = vm.filterMode
                                )
                                vm.mergeGroups = vm.searchService.buildMergeGroups(result.beatmaps)
                                vm.searchResults = incoming
                                vm.currentCursor = result.cursor
                                vm.searchResultsMetadata = vm.searchService.filterMetadataFor(incoming, result.metadata)
                                vm.searchScoreResults = result.scoreResults
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
                BeatmapSetList(
                    sets = beatmapSets,
                    actions = actions,
                    modifier = Modifier.fillMaxSize(),
                    listState = listState,
                    config = BeatmapSetListConfig(
                        showDividers = false,
                        showScrollbar = true,
                        scrollBarOnLeft = isLandscape,
                        scrollBarPadding = PaddingValues(horizontal = 6.dp),
                        expandedIds = vm.expandedBeatmapSets,
                        onExpansionChanged = { id, isExpanded ->
                            vm.expandedBeatmapSets = if (isExpanded) {
                                vm.expandedBeatmapSets + id
                            } else {
                                vm.expandedBeatmapSets - id
                            }
                        },
                        header = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Item 1: Search Bar (1 unit tall)
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
                                                            runSearch("", recordHistory = false, forceRefresh = true)
                                                        }
                                                    }) {
                                                        Icon(
                                                            Icons.Default.Clear,
                                                            contentDescription = stringResource(id = R.string.search_cd_clear)
                                                        )
                                                    }
                                                }

                                                ScoreModeFilterIcons(
                                                    selectedMode = vm.scoreModeFilter,
                                                    onModeSelected = { mode ->
                                                        if (vm.scoreModeFilter != mode) {
                                                            vm.scoreModeFilter = mode
                                                            scope.launch {
                                                                settingsManager.saveScoreModeFilter(mode)
                                                            }
                                                        }
                                                    },
                                                    minimised = isLandscape ||
                                                        LocalConfiguration.current.screenWidthDp <= 380,
                                                    modifier = Modifier.padding(end = 4.dp)
                                                )

                                                var filterMenuExpanded by remember { mutableStateOf(false) }
                                                val isSupporterAllowed = vm.isSupporter
                                                val options = if (isSupporterAllowed) {
                                                    listOf("played", "recent", "favorite", "most_played", "top_play", "all")
                                                } else {
                                                    listOf("recent", "favorite", "most_played", "top_play", "all")
                                                }
                                                val optionLabels = mapOf(
                                                    "played" to stringResource(id = R.string.search_filter_played),
                                                    "recent" to stringResource(id = R.string.search_filter_recent),
                                                    "favorite" to stringResource(id = R.string.search_filter_favorite),
                                                    "most_played" to stringResource(id = R.string.search_filter_most_played),
                                                    "top_play" to stringResource(id = R.string.search_filter_top_play),
                                                    "all" to stringResource(id = R.string.search_filter_all)
                                                )
                                                val optionColors = mapOf(
                                                    "played" to MaterialTheme.colorScheme.primary,
                                                    "recent" to androidx.compose.ui.graphics.Color(0xFF7E57C2),
                                                    "favorite" to androidx.compose.ui.graphics.Color(0xFFFFD059),
                                                    "most_played" to androidx.compose.ui.graphics.Color(0xFF483AC2),
                                                    "top_play" to androidx.compose.ui.graphics.Color(0xFF0077B6),
                                                    "all" to androidx.compose.ui.graphics.Color(0xFFF748AE)
                                                )
                                                val contentColors = mapOf(
                                                    "played" to MaterialTheme.colorScheme.onPrimary,
                                                    "recent" to Color.White,
                                                    "favorite" to Color.Black,
                                                    "most_played" to Color.White,
                                                    "top_play" to Color.White,
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
                                                                        val (groupedItems, mergeGroupsResult, timestamps) = vm.searchService.loadRecentFiltered(
                                                                            accessToken,
                                                                            vm.userId,
                                                                            vm.searchQuery,
                                                                            vm.selectedGenreIds,
                                                                            vm.scoreModeFilter,
                                                                            showPlayHistory = recentPlayHistoryEnabled
                                                                        )
                                                                        vm.mergeGroups = mergeGroupsResult
                                                                        vm.recentGroupedResults = groupedItems
                                                                        vm.searchResults = emptyList()
                                                                        vm.searchResultsMetadata = emptyMap()
                                                                        vm.searchScoreResults = emptyMap()
                                                                        vm.recentTimestamps = timestamps
                                                                    } else {
                                                                        val result = vm.repository.getPlayedBeatmaps(
                                                                            accessToken, vm.selectedGenreIds, null,
                                                                            vm.searchQuery.trim().ifEmpty { null },
                                                                            mode, playedFilterMode, vm.userId, vm.isSupporter, !onlyLeaderboardEnabled,
                                                                            scoreMode = vm.scoreModeFilter
                                                                        )
                                                                        val deduped = vm.searchService.dedupeSearchResultsForMode(
                                                                            list = result.beatmaps,
                                                                            downloadedBeatmapSetIds = vm.downloadedBeatmapSetIds,
                                                                            filterMode = vm.filterMode
                                                                        )
                                                                        vm.mergeGroups = vm.searchService.buildMergeGroups(result.beatmaps)
                                                                        vm.searchResults = deduped
                                                                        vm.currentCursor = result.cursor
                                                                        vm.searchResultsMetadata = vm.searchService.filterMetadataFor(deduped, result.metadata)
                                                                        vm.searchScoreResults = result.scoreResults
                                                                    }
                                                                    // Update load key so initial LaunchedEffect doesn't duplicate
                                                                    vm.lastInitialLoadKey = "$accessToken|${vm.filterMode}|${vm.scoreModeFilter}|${vm.userId}|$onlyLeaderboardEnabled|$playedFilterMode|${!onlyLeaderboardEnabled}|${vm.selectedGenreIds.sorted().joinToString(",")}|$recentPlayHistoryEnabled"
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
                                                scope.launch { runSearch(vm.searchQuery) }
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

                                if (vm.searchQuery.isBlank() && searchHistory.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.search_history_title),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )

                                        LazyRow(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            items(searchHistory, key = { it.query }) { history ->
                                                AssistChip(
                                                    onClick = {
                                                        vm.searchQuery = history.query
                                                        scope.launch { runSearch(history.query) }
                                                    },
                                                    label = {
                                                        Text(
                                                            text = history.query,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            Icons.Default.History,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    },
                                                    trailingIcon = {
                                                        IconButton(
                                                            onClick = { vm.deleteSearchHistory(history.query) },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Clear,
                                                                contentDescription = stringResource(id = R.string.search_history_delete),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                )
                                            }
                                        }

                                        IconButton(onClick = { vm.clearSearchHistory() }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(id = R.string.search_history_clear)
                                            )
                                        }
                                    }
                                }

                                // Portrait uses the same pill menu as Library; landscape uses the rail.
                                if (!isLandscape && genreBarEnabled) {
                                    GenreFilter(
                                        selectedGenreIds = vm.selectedGenreIds,
                                        onGenreSelected = ::applyGenreSelection,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                            .padding(horizontal = 16.dp)
                                    )
                                }
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
                    swipeEnabled = { it.isDownloaded || it.id in pendingDeletions }
                ) {
                    // Search Results
                    if (vm.filterMode == "recent") {
                        val filteredRecentItems = vm.recentGroupedResults
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
                                    val playedSetId = item.play.beatmapSetId
                                    val task = downloadTasks[playedSetId]
                                    val isActuallyDownloaded =
                                        vm.downloadedBeatmapSetIds.contains(playedSetId)
                                    val isDownloaded =
                                        isActuallyDownloaded && playedSetId !in pendingDeletions
                                    val highlight = previewManager.previewingId == playedSetId
                                    val showProgress = task != null &&
                                        (task.status == com.mosu.app.domain.download.DownloadStatus.Downloading ||
                                            task.status == com.mosu.app.domain.download.DownloadStatus.Queued ||
                                            task.status == com.mosu.app.domain.download.DownloadStatus.Extracting)

                                    // Recent cards represent a specific play, so download that exact
                                    // beatmapset rather than resolving another set with the same title.
                                    val recentActions = actions.copy(
                                        onSecondaryAction = { set ->
                                            if (!set.isDownloaded && set.downloadProgress == null) {
                                                downloadManager.enqueue(
                                                    setId = playedSetId,
                                                    title = item.play.title,
                                                    artist = item.play.artist,
                                                    creator = item.play.creator,
                                                    accessToken = accessToken,
                                                    genreId = item.play.genreId,
                                                    coverUrl = item.play.coverUrl ?: map.covers.listUrl
                                                )
                                            }
                                        }
                                    )

                                    val setData = item.play.toBeatmapSetData().copy(
                                        coverUrl = item.play.coverUrl ?: map.covers.listUrl,
                                        isDownloaded = isDownloaded,
                                        downloadProgress = if (showProgress) task?.progress else null,
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

                                    if (isActuallyDownloaded) {
                                        BeatmapSetSwipeItem(
                                            swipeActions = BeatmapSetSwipeActions(
                                                onDelete = {
                                                    deferredActionViewModel.addPendingLibrarySet(playedSetId)
                                                },
                                                onDeleteRevert = {
                                                    deferredActionViewModel.removePendingLibrarySet(playedSetId)
                                                },
                                                onDeleteConfirmed = {
                                                    // Actual deletion after timeout (already running in viewModelScope from BeatmapSetSwipeItem)
                                                    val tracksToDelete = withContext(Dispatchers.IO) {
                                                        vm.db.beatmapDao().getTracksForSet(playedSetId)
                                                    }
                                                    tracksToDelete.forEach { track ->
                                                        TrackService.deleteTrack(track, vm.db, context)
                                                    }
                                                    deferredActionViewModel.removePendingLibrarySet(playedSetId)
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
                                                actions = recentActions,
                                                highlight = highlight,
                                                backgroundBrush = previewBrush,
                                                backgroundColor = Color.Transparent
                                            )
                                        }
                                    } else {
                                        BeatmapSetItem(
                                            set = setData,
                                            actions = recentActions,
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
                            swipeEnabled = { it.isDownloaded || it.id in pendingDeletions }
                        )
                    }

                    // Pagination / Load More
                    if (vm.filterMode != "recent" && vm.currentCursor != null) {
                        item {
                            Button(
                                onClick = {
                                    scope.launch {
                                        vm.isLoadingMore = true
                                        try {
                                            val result = vm.repository.getPlayedBeatmaps(
                                                accessToken,
                                                vm.selectedGenreIds,
                                                vm.currentCursor,
                                                vm.searchQuery.trim().ifEmpty { null },
                                                vm.filterMode,
                                                playedFilterMode,
                                                vm.userId,
                                                vm.isSupporter,
                                                !onlyLeaderboardEnabled,
                                                scoreMode = vm.scoreModeFilter
                                            )
                                            if (result.beatmaps.isNotEmpty()) {
                                                val merged =
                                                    vm.searchService.mergeSearchResultsForMode(
                                                        existing = vm.searchResults,
                                                        incoming = result.beatmaps,
                                                        downloadedBeatmapSetIds = vm.downloadedBeatmapSetIds,
                                                        filterMode = vm.filterMode
                                                    )
                                                val mergedIds = merged.map { it.id }.toSet()
                                                vm.mergeGroups = vm.searchService.unionMergeGroups(
                                                    vm.mergeGroups,
                                                    vm.searchService.buildMergeGroups(result.beatmaps)
                                                )
                                                vm.searchResults = merged
                                                vm.currentCursor = result.cursor
                                                vm.searchResultsMetadata =
                                                    (vm.searchResultsMetadata + result.metadata).filterKeys { it in mergedIds }
                                                vm.searchScoreResults =
                                                    (vm.searchScoreResults + result.scoreResults)
                                                        .filterKeys { it in mergedIds }
                                            } else {
                                                // A page can be empty after local multi-genre filtering
                                                // while more unfiltered pages still remain.
                                                vm.currentCursor = result.cursor
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
                        downloadManager.enqueue(
                            setId = beatmapset.id,
                            title = beatmapset.title,
                            artist = beatmapset.artist,
                            creator = beatmapset.creator,
                            accessToken = accessToken,
                            genreId = beatmapset.genreId,
                            coverUrl = beatmapset.covers.listUrl
                        )
                    },
                    onRestoreClick = { beatmapset ->
                        downloadManager.enqueue(
                            setId = beatmapset.id,
                            title = beatmapset.title,
                            artist = beatmapset.artist,
                            creator = beatmapset.creator,
                            accessToken = accessToken,
                            genreId = beatmapset.genreId,
                            coverUrl = beatmapset.covers.listUrl
                        )
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
                        TrackService.addTrackToPlaylist(playlistId, beatmapSetId, track.title, track.artist, track.difficultyName, vm.db, context)
                    }
                },
                onRemoveFromPlaylist = { playlistId, beatmapSetId ->
                    scope.launch {
                        TrackService.removeTrackFromPlaylist(playlistId, beatmapSetId, track.difficultyName, vm.db, context)
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
        LaunchedEffect(
            accessToken,
            vm.filterMode,
            vm.scoreModeFilter,
            vm.userId,
            onlyLeaderboardEnabled,
            playedFilterMode,
            recentPlayHistoryEnabled
        ) {
            if (accessToken != null) {
                vm.performInitialLoad(
                    accessToken = accessToken,
                    mode = vm.filterMode,
                    uid = vm.userId,
                    onlyLeaderboard = onlyLeaderboardEnabled,
                    playedMode = playedFilterMode,
                    searchAny = !onlyLeaderboardEnabled,
                    showPlayHistory = recentPlayHistoryEnabled,
                    scoreMode = vm.scoreModeFilter
                )
            }
        }
        }
    }
}
