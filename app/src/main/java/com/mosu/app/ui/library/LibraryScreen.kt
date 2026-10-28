package com.mosu.app.ui.library

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.mosu.app.R
import com.mosu.app.data.api.model.BeatmapDetail
import com.mosu.app.data.api.model.BeatmapsetCompact
import com.mosu.app.data.api.model.Covers
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.data.repository.OsuRepository
import com.mosu.app.data.services.TrackService
import com.mosu.app.domain.search.BeatmapSearchService
import com.mosu.app.player.MusicController
import com.mosu.app.ui.components.BeatmapSetActions
import com.mosu.app.ui.components.BeatmapSetData
import com.mosu.app.ui.components.BeatmapSetList
import com.mosu.app.ui.components.BeatmapSetListConfig
import com.mosu.app.ui.components.BeatmapTrackData
import com.mosu.app.ui.components.DownloadProgress
import com.mosu.app.ui.components.GenreFilter
import com.mosu.app.ui.components.GenreRail
import com.mosu.app.ui.components.InfoPopup
import com.mosu.app.ui.components.InfoPopupConfig
import com.mosu.app.ui.components.PlaylistOption
import com.mosu.app.ui.components.PlaylistSelectorDialog
import com.mosu.app.ui.components.beatmapSetList
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mosu.app.ui.DeferredActionViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    db: AppDatabase,
    musicController: MusicController,
    repository: OsuRepository,
    downloadManager: com.mosu.app.domain.download.DownloadManager,
    accessToken: String? = null,
    scrollToTop: Boolean = false,
    onScrolledToTop: () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    deferredActionViewModel: DeferredActionViewModel = viewModel(),
    isLandscape: Boolean = false,
    genreBarEnabled: Boolean = true,
    librarySearchQuery: String = "",
    onLibrarySearchQueryChange: (String) -> Unit = {},
    landscapeFindCurrentRequest: Int = 0,
    onLandscapeFindCurrentRequestConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val downloadedMaps by remember { db.beatmapDao().getAllBeatmaps() }.collectAsState(initial = emptyList())
    val downloadedBeatmapSetIds = remember(downloadedMaps) { downloadedMaps.map { it.beatmapSetId }.toSet() }
    val downloadedKeys = remember(downloadedMaps) {
        downloadedMaps.map { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }.toSet()
    }
    val playlists by remember { db.playlistDao().getPlaylists() }.collectAsState(initial = emptyList())
    val playlistTracks by remember { db.playlistDao().getAllPlaylistTracks() }.collectAsState(initial = emptyList())
    val playlistMembership = playlistTracks
        .groupBy { it.playlistId }
        .mapValues { entry -> entry.value.map { "${it.beatmapSetId}|${it.difficultyName}" }.toSet() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val searchService = remember { BeatmapSearchService(repository, db, context) }

    // Scroll to top when requested
    LaunchedEffect(scrollToTop) {
        if (scrollToTop) {
            listState.animateScrollToItem(0)
            onScrolledToTop()
        }
    }
    var highlightSetId by remember { mutableStateOf<Long?>(null) }
    var buttonBlink by remember { mutableStateOf(false) }
    var expandedBeatmapSets by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var highlightTrackId by remember { mutableStateOf<Long?>(null) }
    val nowPlaying by musicController.nowPlaying.collectAsState()
    val playingTitle = nowPlaying?.title?.toString()?.trim()?.lowercase()

    // Info Popup State
    var infoDialogVisible by remember { mutableStateOf(false) }
    var infoLoading by remember { mutableStateOf(false) }
    var infoError by remember { mutableStateOf<String?>(null) }
    var infoTarget by remember { mutableStateOf<BeatmapsetCompact?>(null) }
    var infoSets by remember { mutableStateOf<List<BeatmapsetCompact>>(emptyList()) }

    // Long press handler for song items
    val onSongLongPress: (BeatmapEntity) -> Unit = { track ->
        // Create a BeatmapsetCompact from the track data
        val beatmapset = BeatmapsetCompact(
            id = track.beatmapSetId,
            title = track.title,
            artist = track.artist,
            creator = track.creator,
            covers = Covers(
                coverUrl = "", // Not available in local data
                listUrl = ""   // Not available in local data
            ),
            genreId = track.genreId,
            status = "unknown",
            beatmaps = emptyList()
        )

        infoTarget = beatmapset
        infoDialogVisible = true
    }

    // Effect to fetch info details when target changes and dialog is visible
    LaunchedEffect(infoTarget, infoDialogVisible) {
        if (infoDialogVisible && infoTarget != null) {
            val target = infoTarget!!
            infoLoading = true
            infoError = null
            infoSets = emptyList()

            val result = searchService.loadInfoPopup(target.title, target.artist)
            
            result.onSuccess { sets ->
                infoSets = sets
            }.onFailure { e ->
                infoError = e.message ?: context.getString(R.string.search_info_load_error)
            }
            
            infoLoading = false
        }
    }

    // Genre Filter State
    var selectedGenreIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    LaunchedEffect(genreBarEnabled) {
        if (!genreBarEnabled) selectedGenreIds = emptySet()
    }

    // Deletion states from shared ViewModel
    val pendingDeletions by deferredActionViewModel.pendingLibraryTrackDeletions.collectAsState()
    val pendingSetDeletions by deferredActionViewModel.pendingLibrarySetDeletions.collectAsState()

    // Download Tasks from global manager
    val downloadTasks by downloadManager.tasks.collectAsState()

    // Search State
    var isSearchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val effectiveSearchQuery = librarySearchQuery

    // Monitor keyboard visibility - collapse when keyboard is hidden and no search text
    val isKeyboardVisible = WindowInsets.isImeVisible

    LaunchedEffect(isKeyboardVisible, librarySearchQuery) {
        if (!isLandscape && !isKeyboardVisible && librarySearchQuery.isEmpty() && isSearchExpanded) {
            isSearchExpanded = false
            focusManager.clearFocus()
        }
    }

    // Filter maps by selected genre and search query
    val filteredMaps = remember(
        downloadedMaps,
        selectedGenreIds,
        effectiveSearchQuery,
        pendingDeletions,
        pendingSetDeletions
    ) {
        val genreFiltered = downloadedMaps.filter {
            val genreMatches = selectedGenreIds.isEmpty() || run {
                val effectiveGenreId = when (it.genreId) {
                    null, 0, 1 -> 6 // Treat null, Any, or Unspecified as Other
                    else -> it.genreId
                }
                effectiveGenreId in selectedGenreIds
            }
            genreMatches &&
                it.uid !in pendingDeletions &&
                it.beatmapSetId !in pendingSetDeletions
        }

        if (effectiveSearchQuery.isNotBlank()) {
            genreFiltered.filter { map ->
                map.title.contains(effectiveSearchQuery, ignoreCase = true) ||
                map.artist.contains(effectiveSearchQuery, ignoreCase = true) ||
                map.difficultyName.contains(effectiveSearchQuery, ignoreCase = true) ||
                map.creator.contains(effectiveSearchQuery, ignoreCase = true)
            }
        } else {
            genreFiltered
        }
    }
    
    // Group maps by Set ID
    val groupedMaps = remember(filteredMaps) {
        filteredMaps.groupBy { it.beatmapSetId }
    }

    var showPlaylistDialog by remember { mutableStateOf(false) }
    var dialogTrack by remember { mutableStateOf<BeatmapEntity?>(null) }
    var dialogSelection by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val dialogSelectionCache = remember { mutableStateMapOf<Long, Set<Long>>() }

    fun openPlaylistDialog(track: BeatmapEntity) {
        dialogTrack = track
        dialogSelection = dialogSelectionCache[track.uid]
            ?: playlists
                .filter { playlistMembership[it.id]?.contains("${track.beatmapSetId}|${track.difficultyName}") == true }
                .map { it.id }
                .toSet()
        showPlaylistDialog = true
    }

    // Keep dialog checkboxes in sync with latest membership while dialog is open
    LaunchedEffect(playlistTracks, dialogTrack, playlists) {
        val track = dialogTrack ?: return@LaunchedEffect
        val latest = playlists
            .filter { playlistMembership[it.id]?.contains("${track.beatmapSetId}|${track.difficultyName}") == true }
            .map { it.id }
            .toSet()
        dialogSelection = latest
        dialogSelectionCache[track.uid] = latest
    }

    // Transform to BeatmapSetData for the list
    val beatmapSets = remember(groupedMaps, expandedBeatmapSets, downloadTasks) {
        groupedMaps.map { (setId, tracks) ->
            val first = tracks.first()
            val isAlbumSet = tracks.any { it.isAlbum }
            val task = downloadTasks[setId]
            
            val showProgress = task != null && 
                (task.status == com.mosu.app.domain.download.DownloadStatus.Downloading || 
                 task.status == com.mosu.app.domain.download.DownloadStatus.Queued || 
                 task.status == com.mosu.app.domain.download.DownloadStatus.Extracting)

            BeatmapSetData(
                id = setId,
                title = first.title,
                artist = first.artist,
                creator = first.creator,
                coverPath = first.coverPath,
                isExpandable = isAlbumSet,
                genreId = first.genreId,
                downloadProgress = if (showProgress) task?.progress else null,
                tracks = tracks.map { track ->
                    BeatmapTrackData(
                        id = track.uid,
                        difficultyName = track.difficultyName,
                        title = track.title,
                        artist = track.artist,
                        creator = track.creator,
                        isDownloaded = true,
                        beatmapSetId = track.beatmapSetId,
                        genreId = track.genreId
                    )
                }
            )
        }
    }

    // Define Actions
    val actions = remember(filteredMaps, snackbarHostState, accessToken, downloadTasks) {
        BeatmapSetActions(
            onClick = { set ->
                // For single tracks (not expandable), play the track
                if (!set.isExpandable && set.tracks.isNotEmpty()) {
                    val track = filteredMaps.find { it.uid == set.tracks.first().id }
                    if (track != null) musicController.playSong(track, filteredMaps)
                }
            },
            onTrackPlay = { trackData ->
                // Play specific track from album
                val track = filteredMaps.find { it.uid == trackData.id }
                if (track != null) musicController.playSong(track, filteredMaps)
            },
            onTrackSwipeLeft = { trackData ->
                // UI-only delete: hide it
                deferredActionViewModel.addPendingLibraryTrack(trackData.id)
            },
            onTrackSwipeLeftRevert = { trackData ->
                // Redo: just un-hide
                deferredActionViewModel.removePendingLibraryTrack(trackData.id)
            },
            onTrackSwipeLeftConfirmed = { trackData ->
                // Actual deletion after timeout (already running in viewModelScope from BeatmapSetSwipeItem)
                val track = downloadedMaps.find { it.uid == trackData.id }
                if (track != null) {
                    TrackService.deleteTrack(track, db, context)
                }
                deferredActionViewModel.removePendingLibraryTrack(trackData.id)
            },
            onTrackSwipeLeftMessage = { trackData ->
                context.getString(R.string.snackbar_removed_from_library, trackData.difficultyName)
            },
            onTrackSwipeRight = { trackData ->
                // Add to playlist
                val track = filteredMaps.find { it.uid == trackData.id }
                if (track != null) openPlaylistDialog(track)
            },
            onSwipeLeft = { set ->
                // UI-only delete: hide the whole set
                deferredActionViewModel.addPendingLibrarySet(set.id)
            },
            onSwipeLeftRevert = { set ->
                // Redo: un-hide the set
                deferredActionViewModel.removePendingLibrarySet(set.id)
            },
            onSwipeLeftConfirmed = { set ->
                // Actual deletion after timeout (already running in viewModelScope from BeatmapSetSwipeItem)
                val tracksToDelete = downloadedMaps.filter { it.beatmapSetId == set.id }
                tracksToDelete.forEach { track ->
                    TrackService.deleteTrack(track, db, context)
                }
                deferredActionViewModel.removePendingLibrarySet(set.id)
            },
            onSwipeLeftMessage = { set ->
                context.getString(R.string.snackbar_removed_from_library, set.title)
            },
            onSwipeRight = { set ->
                // Add to playlist
                val track = filteredMaps.find { it.beatmapSetId == set.id }
                if (track != null) openPlaylistDialog(track)
            },
            onLongClick = { set ->
                 val track = filteredMaps.find { it.beatmapSetId == set.id }
                 if (track != null) onSongLongPress(track)
            },
            swipeLeftIcon = Icons.Default.Delete,
            swipeRightIcon = Icons.Default.Add,
            snackbarHostState = snackbarHostState,
            coroutineScope = deferredActionViewModel.viewModelScope
        )
    }

    fun findCurrentlyPlaying() {
        val match = filteredMaps.firstOrNull {
            // Try matching by title first (for standalone songs)
            it.title.trim().lowercase() == playingTitle ||
                // Then try matching by difficulty name (for songs in beatmapsets)
                it.difficultyName.trim().lowercase() == playingTitle
        }
        if (match != null) {
            scope.launch {
                // 1. If it's a track in an album, ensure album is expanded first
                val isAlbumTrack = groupedMaps[match.beatmapSetId]?.any { it.isAlbum } == true
                if (isAlbumTrack) {
                    expandedBeatmapSets = expandedBeatmapSets + match.beatmapSetId
                    highlightTrackId = match.uid
                } else {
                    highlightSetId = match.beatmapSetId
                }

                // 2. Wait for recomposition to ensure LazyColumn knows about new items
                delay(50)

                // 3. Calculate actual flattened index
                val genreHeaderItemCount = if (!isLandscape && genreBarEnabled) 1 else 0
                var flattenedIndex = genreHeaderItemCount
                for (set in beatmapSets) {
                    if (set.id == match.beatmapSetId) {
                        if (isAlbumTrack) {
                            val sortedSongs = set.tracks.sortedBy { it.difficultyName }
                            val trackIndex = sortedSongs.indexOfFirst { it.id == match.uid }
                            if (trackIndex != -1) {
                                flattenedIndex += 1 + trackIndex // +1 for header
                            }
                        }
                        break
                    }
                    flattenedIndex += 1 // Header
                    if (expandedBeatmapSets.contains(set.id)) {
                        flattenedIndex += set.tracks.size
                    }
                }

                // 4. Scroll to item with offset to center it (roughly)
                if (flattenedIndex >= 0) {
                    listState.animateScrollToItem(flattenedIndex)
                }

                // 5. Highlight sequence
                repeat(3) {
                    delay(150)
                    highlightTrackId = null
                    highlightSetId = null
                    delay(150)
                    if (isAlbumTrack) {
                        highlightTrackId = match.uid
                    } else {
                        highlightSetId = match.beatmapSetId
                    }
                }
                highlightTrackId = null
                highlightSetId = null
            }
        } else {
            scope.launch {
                repeat(3) {
                    buttonBlink = true
                    delay(150)
                    buttonBlink = false
                    delay(150)
                }
            }
        }
    }

    LaunchedEffect(landscapeFindCurrentRequest) {
        if (landscapeFindCurrentRequest > 0) {
            findCurrentlyPlaying()
            onLandscapeFindCurrentRequestConsumed()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (isLandscape && genreBarEnabled) {
                GenreRail(
                    selectedGenreIds = selectedGenreIds,
                    onGenreSelected = { selectedGenreIds = it },
                    modifier = Modifier.fillMaxHeight()
                )
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val libraryTitle = stringResource(id = R.string.library_title)
            val titleStyle = MaterialTheme.typography.displayMedium
            val textMeasurer = rememberTextMeasurer()
            val titleWidth = with(LocalDensity.current) {
                textMeasurer.measure(text = libraryTitle, style = titleStyle).size.width.toDp()
            }

            // Portrait header. Landscape owns the title and search action in the app rails.
            if (!isLandscape) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                // Animated width for collapsible search bar
                val expandedSearchBarWidth = (maxWidth - titleWidth).coerceAtLeast(50.dp)
                val searchBarWidth by animateDpAsState(
                    targetValue = if (isSearchExpanded || librarySearchQuery.isNotEmpty()) {
                        expandedSearchBarWidth
                    } else {
                        50.dp
                    },
                    animationSpec = tween(durationMillis = 300),
                    label = "searchBarWidth"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = libraryTitle,
                        style = titleStyle, // Apple Music style large title
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 16.dp, bottom = 16.dp)
                    )

                    // In landscape the collapsed search action lives above the player rail.
                    if (!isLandscape || isSearchExpanded || librarySearchQuery.isNotEmpty()) {
                        Box(
                        modifier = Modifier
                            .width(searchBarWidth)
                            .height(50.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .focusRequester(searchFocusRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    isSearchExpanded = true
                                }
                            }
                        ) {
                        // Search icon (fades/disappears based on width)
                        if (searchBarWidth < 120.dp) {
                            val iconAlpha = 1f - ((searchBarWidth - 50.dp) / (120.dp - 50.dp)).coerceIn(0f, 1f)

                            IconButton(
                                onClick = {
                                    if (!isSearchExpanded) {
                                        isSearchExpanded = true
                                        searchFocusRequester.requestFocus()
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Open search",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = iconAlpha)
                                )
                            }
                        }

                        // Text field content with dynamic positioning
                        BasicTextField(
                            value = librarySearchQuery,
                            onValueChange = {
                                onLibrarySearchQueryChange(it)
                                // Keep expanded when there's text
                                if (it.isNotEmpty()) {
                                    isSearchExpanded = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    start = if (searchBarWidth >= 120.dp) 18.dp else 10.dp, // More space when icon hidden
                                    end = 30.dp,  // Space for clear button
                                    bottom = 1.dp
                                ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    // Search is already triggered by the filtering logic
                                }
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (librarySearchQuery.isEmpty() && searchBarWidth >= 125.dp) {
                                        Text(
                                            text = stringResource(id = R.string.library_search_placeholder),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        // Clear button
                        if (librarySearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onLibrarySearchQueryChange("") },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 10.dp)
                                    .size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = stringResource(id = R.string.search_cd_clear),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        }
                    }
                }
            }
            }

            // BeatmapSet List
            BeatmapSetList(
                sets = beatmapSets,
                actions = actions,
                modifier = Modifier.fillMaxWidth().weight(1f),
                listState = listState,
                highlightedSetId = highlightSetId,
                highlightedTrackId = highlightTrackId,
                config = BeatmapSetListConfig(
                    showScrollbar = true,
                    scrollBarPadding = PaddingValues(horizontal = 4.dp),
                    expandedIds = expandedBeatmapSets,
                    onExpansionChanged = { id, isExpanded ->
                        expandedBeatmapSets = if (isExpanded) {
                            expandedBeatmapSets + id
                        } else {
                            expandedBeatmapSets - id
                        }
                    },
                    header = if (!isLandscape && genreBarEnabled) {
                        {
                            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Text(
                                    text = stringResource(id = R.string.library_filter_genre),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                GenreFilter(
                                    selectedGenreIds = selectedGenreIds,
                                    onGenreSelected = { selectedGenreIds = it },
                                    modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp)
                                )
                            }
                        }
                    } else {
                        null
                    }
                )
            )
        }
        }

        if (showPlaylistDialog && dialogTrack != null) {
            val track = dialogTrack!!
            val playlistOptions = playlists.map { PlaylistOption(it.id, it.name) }

            PlaylistSelectorDialog(
                playlists = playlistOptions,
                selectedPlaylistIds = dialogSelection,
                onSelectionChanged = { newSelection ->
                    dialogSelection = newSelection
                    dialogSelectionCache[track.uid] = newSelection
                },
                onAddToPlaylist = { playlistId, beatmapSetId ->
                    scope.launch {
                        TrackService.addTrackToPlaylist(playlistId, beatmapSetId, track.title, track.artist, track.difficultyName, db, context)
                    }
                },
                onRemoveFromPlaylist = { playlistId, beatmapSetId ->
                    scope.launch {
                        TrackService.removeTrackFromPlaylist(playlistId, beatmapSetId, track.difficultyName, db, context)
                    }
                },
                beatmapSetId = track.beatmapSetId,
                onDismiss = { showPlaylistDialog = false }
            )
        }

        if (nowPlaying != null && !isLandscape) {
            val isLight = androidx.compose.foundation.isSystemInDarkTheme().not()
            val flickerColor = if (isLight) MaterialTheme.colorScheme.onPrimary else Color(0xff300063)
            val buttonBg = if (buttonBlink) flickerColor.copy(alpha = 0.35f) else Color.Transparent
            IconButton(
                onClick = { findCurrentlyPlaying() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 14.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(buttonBg)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = stringResource(id = R.string.library_find_current_cd),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Info Popup
        InfoPopup(
            visible = infoDialogVisible,
            onDismiss = { infoDialogVisible = false },
            target = infoTarget,
            sets = infoSets,
            loading = infoLoading,
            error = infoError,
            downloadedIds = downloadedBeatmapSetIds,
            downloadedKeys = downloadedKeys,
            config = InfoPopupConfig(
                infoCoverEnabled = false, // Disable cover in library popup
                showConfirmButton = false, // Hide confirm button in library
            )
        )
    }
}
