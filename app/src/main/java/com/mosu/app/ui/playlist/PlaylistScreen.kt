package com.mosu.app.ui.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mosu.app.R
import com.mosu.app.data.api.model.BeatmapDetail
import com.mosu.app.data.api.model.BeatmapsetCompact
import com.mosu.app.data.api.model.Covers
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.data.db.PlaylistEntity
import com.mosu.app.data.db.PlaylistTrackWithBeatmap
import com.mosu.app.data.repository.OsuRepository
import com.mosu.app.data.services.TrackService
import com.mosu.app.domain.download.BeatmapDownloadService
import com.mosu.app.domain.download.UnifiedDownloadState
import com.mosu.app.domain.search.BeatmapSearchService
import com.mosu.app.player.MusicController
import com.mosu.app.ui.components.BeatmapSetActions
import com.mosu.app.ui.components.BeatmapSetData
import com.mosu.app.ui.components.BeatmapSetList
import com.mosu.app.ui.components.BeatmapSetListConfig
import com.mosu.app.ui.components.BeatmapTrackData
import com.mosu.app.ui.components.InfoPopup
import com.mosu.app.ui.components.InfoPopupConfig
import com.mosu.app.ui.components.PlaylistOption
import com.mosu.app.ui.components.PlaylistSelectorDialog
import com.mosu.app.ui.components.SelectableBeatmapData
import com.mosu.app.ui.components.SelectableBeatmapList
import com.mosu.app.ui.components.beatmapSetList
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    db: AppDatabase,
    musicController: MusicController,
    repository: OsuRepository,
    downloadService: BeatmapDownloadService,
    accessToken: String? = null,
    snackbarHostState: SnackbarHostState? = null
) {
    val playlists by db.playlistDao().getPlaylists().collectAsState(initial = emptyList())
    val playlistCounts by db.playlistDao().getPlaylistCounts().collectAsState(initial = emptyList())
    val downloadedTracks by db.beatmapDao().getAllBeatmaps().collectAsState(initial = emptyList())
    val playlistTrackRefs by db.playlistDao().getAllPlaylistTracks().collectAsState(initial = emptyList())
    val beatmapBySetId = remember(downloadedTracks) { downloadedTracks.associateBy { it.beatmapSetId } }
    val downloadedBeatmapSetIds = remember(downloadedTracks) { downloadedTracks.map { it.beatmapSetId }.toSet() }
    val downloadedKeys = remember(downloadedTracks) {
        downloadedTracks.map { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }.toSet()
    }
    val playlistMembership = playlistTrackRefs
        .groupBy { it.playlistId }
        .mapValues { entry -> entry.value.map { "${it.beatmapSetId}|${it.difficultyName}" }.toSet() }

    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
    
    // Pending Deletions State (for Deferred Removal Redo)
    var pendingDeletions by remember { mutableStateOf<Set<String>>(emptySet()) } // Set of "setId|difficultyName"

    val playlistTracksWithStatus: List<PlaylistTrackWithBeatmap> by if (selectedPlaylistId != null) {
        db.playlistDao().getTracksWithStatusForPlaylist(selectedPlaylistId!!).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }

    // Extract downloaded tracks for backward compatibility
    val playlistTracks: List<BeatmapEntity> = playlistTracksWithStatus.mapNotNull { it.toBeatmapEntity() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    var showAddDialog by remember { mutableStateOf(false) }
    var addSelection by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // Playlist dialog for adding tracks/playlists to other playlists
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var dialogTrack by remember { mutableStateOf<BeatmapEntity?>(null) }
    var dialogSelection by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val dialogSelectionCache = remember { mutableStateMapOf<Long, Set<Long>>() }

    // Info Popup State
    var infoDialogVisible by remember { mutableStateOf(false) }
    var infoLoading by remember { mutableStateOf(false) }
    var infoError by remember { mutableStateOf<String?>(null) }
    var infoTarget by remember { mutableStateOf<BeatmapsetCompact?>(null) }
    var infoSets by remember { mutableStateOf<List<BeatmapsetCompact>>(emptyList()) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val searchService = remember { BeatmapSearchService(repository, db, context) }
    var expandedBeatmapSets by remember { mutableStateOf<Set<Long>>(emptySet()) }

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

    // Handle back gesture when inside a playlist - takes priority over main navigation
    BackHandler(enabled = selectedPlaylistId != null) {
        selectedPlaylistId = null
    }

    fun playAlbum(tracks: List<BeatmapEntity>) {
        if (tracks.isEmpty()) return
        musicController.playSong(tracks.first(), tracks)
    }

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
    LaunchedEffect(playlistTrackRefs, dialogTrack, playlists) {
        val track = dialogTrack ?: return@LaunchedEffect
        val latest = playlists
            .filter { playlistMembership[it.id]?.contains("${track.beatmapSetId}|${track.difficultyName}") == true }
            .map { it.id }
            .toSet()
        dialogSelection = latest
        dialogSelectionCache[track.uid] = latest
    }

    // Transform to BeatmapSetData for the list
    val groupedTracks = remember(playlistTracksWithStatus, pendingDeletions) {
        playlistTracksWithStatus
            .filter { "${it.beatmapSetId}|${it.difficultyName ?: it.storedDifficultyName}" !in pendingDeletions }
            .groupBy { it.beatmapSetId }
    }

    val beatmapSets = remember(groupedTracks) {
        groupedTracks.map { (setId, tracks) ->
            val first = tracks.first()
            BeatmapSetData(
                id = setId,
                title = first.beatmapTitle ?: first.storedTitle,
                artist = first.beatmapArtist ?: first.storedArtist,
                creator = first.creator,
                coverPath = first.coverPath,
                isExpandable = first.isAlbum == true,
                genreId = first.genreId,
                tracks = tracks.map { track ->
                    BeatmapTrackData(
                        id = track.uid ?: track.beatmapSetId, 
                        difficultyName = track.difficultyName ?: track.storedDifficultyName,
                        title = track.beatmapTitle ?: track.storedTitle,
                        artist = track.beatmapArtist ?: track.storedArtist,
                        creator = track.creator ?: track.storedArtist,
                        isDownloaded = track.isDownloaded == true,
                        beatmapSetId = track.beatmapSetId,
                        genreId = track.genreId
                    )
                }
            )
        }
    }

    val actions = remember(playlistTracksWithStatus, selectedPlaylistId, snackbarHostState) {
        val selectedPlaylist = playlists.find { it.id == selectedPlaylistId }
        BeatmapSetActions(
            onClick = { set ->
                if (!set.isExpandable && set.tracks.isNotEmpty()) {
                    val track = playlistTracksWithStatus.find { 
                        it.beatmapSetId == set.id && 
                        (it.uid == set.tracks.first().id || it.difficultyName == set.tracks.first().difficultyName) 
                    }
                    track?.toBeatmapEntity()?.let { 
                        musicController.playSong(it, playlistTracks) 
                    }
                }
            },
            onTrackPlay = { trackData ->
                val track = playlistTracksWithStatus.find { 
                    it.uid == trackData.id || 
                    (it.beatmapSetId == (beatmapSets.find { s -> s.tracks.contains(trackData) }?.id ?: 0) && it.difficultyName == trackData.difficultyName)
                }
                track?.toBeatmapEntity()?.let {
                    musicController.playSong(it, playlistTracks)
                }
            },
            onTrackSwipeLeft = { trackData ->
                // UI-only remove: hide it
                if (selectedPlaylistId != null) {
                    val key = "${trackData.beatmapSetId}|${trackData.difficultyName}"
                    pendingDeletions = pendingDeletions + key
                }
            },
            onTrackSwipeLeftRevert = { trackData ->
                // Redo: just un-hide
                val key = "${trackData.beatmapSetId}|${trackData.difficultyName}"
                pendingDeletions = pendingDeletions - key
            },
            onTrackSwipeLeftConfirmed = { trackData ->
                // Actual removal after timeout
                if (selectedPlaylistId != null) {
                    scope.launch {
                        TrackService.removeTrackFromPlaylist(selectedPlaylistId!!, trackData.beatmapSetId, trackData.difficultyName, db)
                        val key = "${trackData.beatmapSetId}|${trackData.difficultyName}"
                        pendingDeletions = pendingDeletions - key
                    }
                }
            },
            onTrackSwipeLeftMessage = { trackData ->
                val title = trackData.title ?: trackData.difficultyName
                "Removed $title from ${selectedPlaylist?.name ?: "Playlist"}"
            },
            onTrackSwipeRight = { trackData ->
                // Add to other playlist
                val track = playlistTracksWithStatus.find { it.uid == trackData.id }?.toBeatmapEntity()
                if (track != null) openPlaylistDialog(track)
            },
            onSwipeLeft = { set ->
                // UI-only remove: hide the whole set
                if (selectedPlaylistId != null) {
                    val keys = set.tracks.map { "${set.id}|${it.difficultyName}" }
                    pendingDeletions = pendingDeletions + keys
                }
            },
            onSwipeLeftRevert = { set ->
                // Redo: un-hide the set
                val keys = set.tracks.map { "${set.id}|${it.difficultyName}" }
                pendingDeletions = pendingDeletions - keys
            },
            onSwipeLeftConfirmed = { set ->
                // Actual removal after timeout
                if (selectedPlaylistId != null) {
                    scope.launch {
                        val tracksToRemove = playlistTracksWithStatus.filter { it.beatmapSetId == set.id }
                        tracksToRemove.forEach { track ->
                            TrackService.removeTrackFromPlaylist(selectedPlaylistId!!, track.beatmapSetId, track.storedDifficultyName, db)
                        }
                        val keys = set.tracks.map { "${set.id}|${it.difficultyName}" }
                        pendingDeletions = pendingDeletions - keys
                    }
                }
            },
            onSwipeLeftMessage = { set ->
                "Removed ${set.title} from ${selectedPlaylist?.name ?: "Playlist"}"
            },
            onSwipeRight = { set ->
                // Add to OTHER playlist
                val track = playlistTracksWithStatus.find { it.beatmapSetId == set.id }?.toBeatmapEntity()
                if (track != null) openPlaylistDialog(track)
            },
            onLongClick = { set ->
                val track = playlistTracksWithStatus.find { it.beatmapSetId == set.id }?.toBeatmapEntity()
                if (track != null) onSongLongPress(track)
            },
            swipeLeftIcon = Icons.Default.Remove,
            swipeRightIcon = Icons.Default.Add,
            snackbarHostState = snackbarHostState,
            coroutineScope = scope
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (selectedPlaylistId != null) {
                    // Remove padding when viewing playlist to allow content to touch miniplayer
                    Modifier
                } else {
                    Modifier.padding(16.dp)
                }
            )
    ) {
        if (selectedPlaylistId == null) {
            // Grid view of albums
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.playlist_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(id = R.string.playlist_cd_create_playlist))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = stringResource(id = R.string.playlist_empty_state))
                }
            } else {
                val countsMap = playlistCounts.associate { it.playlistId to it.count }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        val coverPaths = playlistTrackRefs
                            .asSequence()
                            .filter { it.playlistId == playlist.id }
                            .sortedBy { it.addedAt }
                            .mapNotNull { ref -> beatmapBySetId[ref.beatmapSetId]?.coverPath }
                            .filter { it.isNotBlank() }
                            .take(4)
                            .toList()
                        PlaylistCard(
                            playlist = playlist,
                            trackCount = countsMap[playlist.id] ?: 0,
                            coverPaths = coverPaths,
                            onClick = { selectedPlaylistId = playlist.id },
                            onDelete = {
                                scope.launch {
                                    // Delete all tracks from this playlist first
                                    db.playlistDao().removeAllTracksFromPlaylist(playlist.id)
                                    // Then delete the playlist itself
                                    db.playlistDao().deletePlaylist(playlist.id)
                                }
                            }
                        )
                    }
                }
            }
        } else {
            val selectedPlaylist = playlists.find { it.id == selectedPlaylistId }
            val existingIds = playlistTracks.map { it.uid }.toSet()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedPlaylistId = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.playlist_cd_back))
                }
                Column {
                    Text(
                        text = selectedPlaylist?.name ?: stringResource(id = R.string.playlist_default_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val downloadedCount = playlistTracksWithStatus.count { it.toBeatmapEntity() != null }
                    val totalCount = playlistTracksWithStatus.size
                    Text(
                        text = if (downloadedCount < totalCount) {
                            "$downloadedCount/${totalCount} ${stringResource(id = R.string.playlist_song_count_suffix)}"
                        } else {
                            "$totalCount ${stringResource(id = R.string.playlist_song_count_suffix)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    addSelection = emptySet()
                    showAddDialog = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(id = R.string.playlist_cd_add_songs))
                }
                IconButton(onClick = { playAlbum(playlistTracks) }, enabled = playlistTracks.isNotEmpty()) {
                    Icon(Icons.Default.PlayArrow, contentDescription = stringResource(id = R.string.playlist_cd_play_playlist))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (playlistTracksWithStatus.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = stringResource(id = R.string.playlist_no_songs))
                }
            } else {
                BeatmapSetList(
                    sets = beatmapSets,
                    actions = actions,
                    modifier = Modifier.fillMaxSize(),
                    config = BeatmapSetListConfig(
                        showDividers = true,
                        showScrollbar = true,
                        expandedIds = expandedBeatmapSets,
                        onExpansionChanged = { id, isExpanded ->
                            expandedBeatmapSets = if (isExpanded) {
                                expandedBeatmapSets + id
                            } else {
                                expandedBeatmapSets - id
                            }
                        }
                    )
                )
            }

            if (showAddDialog) {
                AlertDialog(
                    onDismissRequest = { showAddDialog = false },
                    title = { Text(stringResource(id = R.string.playlist_add_songs_dialog_title)) },
                    text = {
                        val selectableBeatmaps = downloadedTracks.map { track ->
                            SelectableBeatmapData(
                                title = track.title,
                                artist = track.artist,
                                coverPath = track.coverPath,
                                difficultyName = track.difficultyName,
                                id = track.uid
                            )
                        }

                        SelectableBeatmapList(
                            beatmaps = selectableBeatmaps,
                            isSelected = { data -> addSelection.contains(data.id) },
                            onSelectionChanged = { data, selected ->
                                addSelection = if (selected) {
                                    addSelection + data.id
                                } else {
                                    addSelection - data.id
                                }
                            },
                            isDisabled = { data -> existingIds.contains(data.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val playlistId = selectedPlaylistId ?: return@TextButton
                                scope.launch {
                                    addSelection.forEach { uid ->
                                        val track = downloadedTracks.find { it.uid == uid }
                                        if (track != null) {
                                            TrackService.addTrackToPlaylist(playlistId, track.beatmapSetId, track.title, track.artist, track.difficultyName, db)
                                        }
                                    }
                                }
                                showAddDialog = false
                            }
                        ) {
                            Text(stringResource(id = R.string.playlist_add_button))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddDialog = false }) { Text(stringResource(id = R.string.playlist_cancel_button)) }
                    }
                )
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(id = R.string.playlist_create_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    singleLine = true,
                    label = { Text(stringResource(id = R.string.playlist_name_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newPlaylistName.trim()
                        if (name.isNotEmpty()) {
                            scope.launch {
                                db.playlistDao().insertPlaylist(PlaylistEntity(name = name))
                            }
                            newPlaylistName = ""
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text(stringResource(id = R.string.playlist_create_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(id = R.string.playlist_cancel_button))
                }
            }
        )
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
                        TrackService.addTrackToPlaylist(playlistId, beatmapSetId, track.title, track.artist, track.difficultyName, db)
                    }
                },
                onRemoveFromPlaylist = { playlistId, beatmapSetId ->
                    scope.launch {
                        TrackService.removeTrackFromPlaylist(playlistId, beatmapSetId, track.difficultyName, db)
                    }
                },
            beatmapSetId = track.beatmapSetId,
            onDismiss = { showPlaylistDialog = false }
        )
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
            infoCoverEnabled = false, // Disable cover in playlist popup
            showConfirmButton = false, // Hide confirm button in playlist
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
                        if (state is UnifiedDownloadState.Error) {
                            android.widget.Toast.makeText(context, state.message, android.widget.Toast.LENGTH_SHORT).show()
                        } else if (state is UnifiedDownloadState.Success) {
                            android.widget.Toast.makeText(context, context.getString(R.string.search_download_done), android.widget.Toast.LENGTH_SHORT).show()
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
                        if (state is UnifiedDownloadState.Error) {
                            android.widget.Toast.makeText(context, state.message, android.widget.Toast.LENGTH_SHORT).show()
                        } else if (state is UnifiedDownloadState.Success) {
                            android.widget.Toast.makeText(context, context.getString(R.string.search_download_done), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    )
}

@Composable
private fun PlaylistCard(
    playlist: PlaylistEntity,
    trackCount: Int,
    coverPaths: List<String>,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteIcon by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (showDeleteIcon) {
                            // If delete icon is showing, hide it instead of opening playlist
                            showDeleteIcon = false
                        } else {
                            onClick()
                        }
                    },
                    onLongPress = {
                        showDeleteIcon = true
                    }
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            CollageBackground(
                coverPaths = coverPaths,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .padding(12.dp)
            ) {
                // Delete icon overlay (top-right corner)
                if (showDeleteIcon) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(id = R.string.playlist_cd_delete_playlist),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(24.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                            .padding(4.dp)
                            .clickable(
                                onClick = {
                                    onDelete()
                                    showDeleteIcon = false  // Hide icon after deletion
                                }
                            )
                    )
                }
            Column(
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Icon(
                    Icons.Default.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.65f),
                            offset = Offset(1f, 1f),
                            blurRadius = 4f
                        )
                    ),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$trackCount songs",
                    style = MaterialTheme.typography.bodySmall.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.6f),
                            offset = Offset(0.8f, 0.8f),
                            blurRadius = 3f
                        )
                    ),
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        }
    }
}


@Composable
private fun CollageBackground(
    coverPaths: List<String>,
    modifier: Modifier = Modifier
) {
    val paths = coverPaths.take(4)
    if (paths.isEmpty()) {
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        return
    }

    val chunks = listOf(
        paths.take(2),
        paths.drop(2)
    )

    Column(modifier = modifier) {
        chunks.forEach { row ->
            Row(modifier = Modifier.weight(1f)) {
                row.forEach { path ->
                    AsyncImage(
                        model = File(path),
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .aspectRatio(1f),
                        contentScale = ContentScale.Crop
                    )
                }
                repeat(2 - row.size) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        }
    }
}
