package com.mosu.app.ui.library

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mosu.app.R
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.data.services.TrackService
import com.mosu.app.player.MusicController
import com.mosu.app.ui.components.AlbumGroup
import com.mosu.app.ui.components.AlbumGroupActions
import com.mosu.app.ui.components.AlbumGroupData
import com.mosu.app.ui.components.GenreFilter
import com.mosu.app.ui.components.PlaylistOption
import com.mosu.app.ui.components.PlaylistSelectorDialog
import com.mosu.app.ui.components.SongItemData
import com.mosu.app.ui.components.SwipeActions
import com.mosu.app.ui.components.SwipeToDismissSongItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    db: AppDatabase,
    musicController: MusicController
) {
    val context = LocalContext.current
    val downloadedMaps by db.beatmapDao().getAllBeatmaps().collectAsState(initial = emptyList())
    val playlists by db.playlistDao().getPlaylists().collectAsState(initial = emptyList())
    val playlistTracks by db.playlistDao().getAllPlaylistTracks().collectAsState(initial = emptyList())
    val playlistMembership = playlistTracks
        .groupBy { it.playlistId }
        .mapValues { entry -> entry.value.map { it.beatmapSetId }.toSet() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var highlightSetId by remember { mutableStateOf<Long?>(null) }
    var buttonBlink by remember { mutableStateOf(false) }
    var expandedBeatmapSets by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var highlightTrackId by remember { mutableStateOf<Long?>(null) }
    val nowPlaying by musicController.nowPlaying.collectAsState()
    val playingTitle = nowPlaying?.title?.toString()?.trim()?.lowercase()

    // Genre Filter State
    var selectedGenreId by remember { mutableStateOf<Int?>(null) }

    // Search State
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Monitor keyboard visibility - collapse when keyboard is hidden and no search text
    val isKeyboardVisible = WindowInsets.isImeVisible

    LaunchedEffect(isKeyboardVisible, searchQuery) {
        if (!isKeyboardVisible && searchQuery.isEmpty() && isSearchExpanded) {
            isSearchExpanded = false
            focusManager.clearFocus()
        }
    }

    // Filter maps by selected genre and search query
    val genreFilteredMaps = if (selectedGenreId != null) {
        downloadedMaps.filter { it.genreId == selectedGenreId }
    } else {
        downloadedMaps
    }

    val filteredMaps = if (searchQuery.isNotBlank()) {
        genreFilteredMaps.filter { map ->
            map.title.contains(searchQuery, ignoreCase = true) ||
            map.artist.contains(searchQuery, ignoreCase = true) ||
            map.difficultyName.contains(searchQuery, ignoreCase = true) ||
            map.creator.contains(searchQuery, ignoreCase = true)
        }
    } else {
        genreFilteredMaps
    }
    
    // Group maps by Set ID
    val groupedMaps = filteredMaps.groupBy { it.beatmapSetId }

    var showPlaylistDialog by remember { mutableStateOf(false) }
    var dialogTrack by remember { mutableStateOf<BeatmapEntity?>(null) }
    var dialogSelection by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val dialogSelectionCache = remember { mutableStateMapOf<Long, Set<Long>>() }

    fun openPlaylistDialog(track: BeatmapEntity) {
        dialogTrack = track
        dialogSelection = dialogSelectionCache[track.uid]
            ?: playlists
                .filter { playlistMembership[it.id]?.contains(track.beatmapSetId) == true }
                .map { it.id }
                .toSet()
        showPlaylistDialog = true
    }

    // Keep dialog checkboxes in sync with latest membership while dialog is open
    LaunchedEffect(playlistTracks, dialogTrack, playlists) {
        val track = dialogTrack ?: return@LaunchedEffect
        val latest = playlists
            .filter { playlistMembership[it.id]?.contains(track.beatmapSetId) == true }
            .map { it.id }
            .toSet()
        dialogSelection = latest
        dialogSelectionCache[track.uid] = latest
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Animated width for collapsible search bar
        val searchBarWidth by animateDpAsState(
            targetValue = if (isSearchExpanded || searchQuery.isNotEmpty()) 200.dp else 50.dp,
            animationSpec = tween(durationMillis = 300),
            label = "searchBarWidth"
        )

        // Header with title and search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = stringResource(id = R.string.library_title),
                style = MaterialTheme.typography.displayMedium, // Apple Music style large title
                modifier = Modifier.weight(1f)
            )

            // Custom search field with dynamic text positioning
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
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
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
                            if (searchQuery.isEmpty() && searchBarWidth >= 125.dp) {
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
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
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

        // Genre Filter
        Text(text = stringResource(id = R.string.library_filter_genre), style = MaterialTheme.typography.labelMedium)
        GenreFilter(
            selectedGenreId = selectedGenreId,
            onGenreSelected = { selectedGenreId = it },
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )

        LazyColumn(state = listState) {
        items(
            items = groupedMaps.keys.toList(),
            key = { setId -> setId } // Add unique key for proper state management
        ) { setId ->
            val tracks = groupedMaps[setId] ?: emptyList()
            if (tracks.isEmpty()) return@items

            if (tracks.size > 1) {
                // Album Group
                val albumData = AlbumGroupData(
                    title = tracks[0].title,
                    artist = tracks[0].artist,
                    coverPath = tracks[0].coverPath,
                    trackCount = tracks.size,
                    songs = tracks.map { track ->
                        SongItemData(
                            title = track.difficultyName,
                            artist = track.creator,
                            coverPath = track.coverPath,
                            difficultyName = track.difficultyName,
                            id = track.uid
                        )
                    },
                    id = setId
                )

                val albumActions = AlbumGroupActions(
                    onAlbumPlay = {
                        // Play the first track of the album
                        musicController.playSong(tracks.first(), filteredMaps)
                    },
                    onTrackPlay = { songData ->
                        // Find the actual BeatmapEntity and play it
                        val track = tracks.find { it.uid == songData.id }
                        if (track != null) {
                            musicController.playSong(track, filteredMaps)
                        }
                    },
                    onDelete = {
                        scope.launch {
                            tracks.forEach { track ->
                                TrackService.deleteTrack(track, db, context)
                            }
                        }
                    },
                    onAddToPlaylist = {
                        openPlaylistDialog(tracks.first())
                    },
                    onTrackDelete = { songData ->
                        scope.launch {
                            val track = tracks.find { it.uid == songData.id }
                            if (track != null) {
                                TrackService.deleteTrack(track, db, context)
                            }
                        }
                    },
                    onTrackAddToPlaylist = { songData ->
                        val track = tracks.find { it.uid == songData.id }
                        if (track != null) {
                            openPlaylistDialog(track)
                        }
                    }
                )

                AlbumGroup(
                    album = albumData,
                    actions = albumActions,
                    highlight = highlightSetId == setId && nowPlaying != null,
                    forceExpanded = expandedBeatmapSets.contains(setId),
                    onExpansionChanged = { expanded ->
                        expandedBeatmapSets = if (expanded) {
                            expandedBeatmapSets + setId
                        } else {
                            expandedBeatmapSets - setId
                        }
                    },
                    highlightTrackId = highlightTrackId
                )
            } else {
                // Single Track
                val track = tracks[0]
                val songData = SongItemData(
                    title = track.title,
                    artist = track.artist,
                    coverPath = track.coverPath,
                    difficultyName = track.difficultyName,
                    id = track.uid
                )

                val swipeActions = SwipeActions(
                    onDelete = {
                        scope.launch {
                            TrackService.deleteTrack(track, db, context)
                        }
                    },
                    onAddToPlaylist = { openPlaylistDialog(track) }
                )

                SwipeToDismissSongItem(
                    song = songData,
                    onClick = { musicController.playSong(track, filteredMaps) },
                    swipeActions = swipeActions,
                    highlight = highlightSetId == setId && nowPlaying != null
                )
            }
            Divider(modifier = Modifier.padding(start = 64.dp)) // Apple style separator
        }
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
                        TrackService.addTrackToPlaylist(playlistId, beatmapSetId, track.title, track.artist, db)
                    }
                },
                onRemoveFromPlaylist = { playlistId, beatmapSetId ->
                    scope.launch {
                        TrackService.removeTrackFromPlaylist(playlistId, beatmapSetId, db)
                    }
                },
                beatmapSetId = track.beatmapSetId,
                onDismiss = { showPlaylistDialog = false }
            )
        }

        if (nowPlaying != null) {
            val isLight = androidx.compose.foundation.isSystemInDarkTheme().not()
            val flickerColor = if (isLight) MaterialTheme.colorScheme.onPrimary else Color(0xff300063)
            val buttonBg = if (buttonBlink) flickerColor.copy(alpha = 0.35f) else Color.Transparent
            IconButton(
                onClick = {
                    val keyList = groupedMaps.keys.toList()
                    val match = filteredMaps.firstOrNull {
                        // Try matching by title first (for standalone songs)
                        it.title.trim().lowercase() == playingTitle ||
                        // Then try matching by difficulty name (for songs in beatmapsets)
                        it.difficultyName.trim().lowercase() == playingTitle
                    }
                    if (match != null) {
                        val targetIndex = keyList.indexOf(match.beatmapSetId)
                        if (targetIndex >= 0) {
                            scope.launch {
                                // Check if it's a song in a beatmapset pack
                                val isSongInPack = groupedMaps[match.beatmapSetId]?.size ?: 0 > 1 &&
                                                  match.difficultyName.trim().lowercase() == playingTitle

                                if (isSongInPack) {
                                    // For songs in beatmapset packs: expand the pack and highlight only the specific track
                                    expandedBeatmapSets = expandedBeatmapSets + match.beatmapSetId
                                    highlightTrackId = match.uid

                                    // Calculate song position within the beatmapset for scroll offset
                                    val beatmapset = groupedMaps[match.beatmapSetId] ?: emptyList()
                                    val sortedSongs = beatmapset.sortedBy { it.difficultyName } // Sort by difficulty name like AlbumGroup
                                    val songIndex = sortedSongs.indexOfFirst { it.uid == match.uid }

                                    // Estimate scroll offset: header height + (song index * estimated item height)
                                    // Header is ~80dp, each song item is ~60dp
                                    val estimatedOffsetDp = 80 + songIndex * 65
                                    val estimatedOffsetPx = estimatedOffsetDp * 3 // Rough density multiplier

                                    // Scroll to the beatmapset with offset to bring target song into view
                                    if (songIndex > 2) { // Only use offset if song is not in the first few positions
                                        // Positive offset to position beatmapset lower, revealing content above
                                        listState.animateScrollToItem(targetIndex, estimatedOffsetPx.toInt())
                                    } else {
                                        listState.animateScrollToItem(targetIndex)
                                    }

                                    // Normal highlight sequence for the specific track (same as standalone songs)
                                    repeat(3) {
                                        highlightTrackId = match.uid  // Set highlight
                                        delay(150)
                                        highlightTrackId = null       // Clear highlight
                                        delay(150)
                                    }

                                    // Final clear (already cleared in loop, but for safety)
                                    highlightTrackId = null
                                } else {
                                    // For standalone songs, just highlight the item
                                    listState.animateScrollToItem(targetIndex)
                                    repeat(3) {
                                        highlightSetId = match.beatmapSetId
                                        delay(150)
                                        highlightSetId = null
                                        delay(150)
                                    }
                                }
                            }
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
                },
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

    }
}


