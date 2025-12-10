package com.mosu.app.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mosu.app.data.api.model.Covers
import com.mosu.app.data.api.model.BeatmapsetCompact
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.RecentPlayEntity
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.data.repository.OsuRepository
import com.mosu.app.domain.download.BeatmapDownloader
import com.mosu.app.domain.download.DownloadState
import com.mosu.app.domain.download.ZipExtractor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

data class DownloadProgress(
    val progress: Int, // 0-100
    val status: String // "Downloading", "Extracting", "Done", "Error"
)

@Composable
fun SearchScreen(
    authCode: String?,
    repository: OsuRepository,
    db: AppDatabase,
    accessToken: String?,
    clientId: String,
    clientSecret: String,
    settingsManager: com.mosu.app.data.SettingsManager,
    musicController: com.mosu.app.player.MusicController,
    onTokenReceived: (String) -> Unit,
    scrollToTop: Boolean = false,
    onScrolledToTop: () -> Unit = {}
) {
    var statusText by remember { mutableStateOf("") }
    
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
    
    // Download States Map (BeatmapSetId -> DownloadProgress)
    var downloadStates by remember { mutableStateOf<Map<Long, DownloadProgress>>(emptyMap()) }
    
    // Downloaded BeatmapSet IDs (from database)
    var downloadedBeatmapSetIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    
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
        10 to "Electronic", 3 to "Anime", 4 to "Rock", 5 to "Pop",
        2 to "Game", 9 to "Hip Hop", 11 to "Metal", 12 to "Classical",
        13 to "Folk", 14 to "Jazz", 7 to "Novelty", 6 to "Other"
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
        val seen = mutableSetOf<String>()
        val merged = mutableListOf<BeatmapsetCompact>()
        list.forEach { beatmap ->
            val key = beatmap.title.trim().lowercase()
            if (seen.add(key)) {
                merged.add(beatmap)
            }
        }
        return merged
    }

    fun mergeByTitle(existing: List<BeatmapsetCompact>, incoming: List<BeatmapsetCompact>): List<BeatmapsetCompact> {
        if (incoming.isEmpty()) return existing
        val seen = existing.map { it.title.trim().lowercase() }.toMutableSet()
        val merged = existing.toMutableList()
        incoming.forEach { beatmap ->
            val key = beatmap.title.trim().lowercase()
            if (seen.add(key)) {
                merged.add(beatmap)
            }
        }
        return merged
    }

    fun filterMetadataFor(list: List<BeatmapsetCompact>, metadata: Map<Long, Pair<Int, Int>>): Map<Long, Pair<Int, Int>> {
        if (metadata.isEmpty()) return emptyMap()
        val allowedIds = list.map { it.id }.toSet()
        return metadata.filterKeys { it in allowedIds }
    }

    suspend fun loadRecent() {
        val recent = db.recentPlayDao().getRecentPlays()
        searchResults = dedupeByTitle(applyLocalFilters(recent.map { it.toBeatmapset() }))
        currentCursor = null
        searchResultsMetadata = emptyMap()
        statusText = ""
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
        )

        if (accessToken == null) {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Not Logged In", style = MaterialTheme.typography.titleMedium)
                    Text("Please go to the Profile tab to configure credentials and login.", style = MaterialTheme.typography.bodyMedium)
                    if (statusText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        } else {
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
            
            // Collapsible header with search bar and genre filter
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
                        "Search by title or artist...",
                        style = MaterialTheme.typography.bodySmall
                    ) 
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
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
                                        val result = repository.getPlayedBeatmaps(accessToken!!, selectedGenreId, null, null, filterMode, playedFilterMode, userId, isSupporter)
                                        val deduped = dedupeByTitle(result.beatmaps)
                                        searchResults = deduped
                                        currentCursor = result.cursor
                                        searchResultsMetadata = filterMetadataFor(deduped, result.metadata)
                                        }
                                    } catch (e: Exception) {
                                        statusText = "Error: ${e.message}"
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                        
                        // Filter Mode Dropdown
                        var filterMenuExpanded by remember { mutableStateOf(false) }
                        val isSupporterAllowed = isSupporter
                        val options = if (isSupporterAllowed) {
                            listOf("played", "recent", "favorite", "most_played", "all")
                        } else {
                            listOf("recent", "favorite", "most_played", "all")
                        }
                        val optionLabels = mapOf(
                            "played" to "Played",
                            "recent" to "Recent",
                            "favorite" to "Favorite",
                            "most_played" to "Most Play",
                            "all" to "All"
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
                            "recent" to androidx.compose.ui.graphics.Color(0xFFFFFFFF),
                            "favorite" to androidx.compose.ui.graphics.Color(0xFF000000),
                            "most_played" to androidx.compose.ui.graphics.Color(0xFFFFFFFF),
                            "all" to androidx.compose.ui.graphics.Color(0xFFFFFFFF)
                        )
                        val currentColor = optionColors[filterMode] ?: MaterialTheme.colorScheme.primary
                        val currentContentColor = contentColors[filterMode] ?: MaterialTheme.colorScheme.primary

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
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = optionLabels[filterMode] ?: "Select",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                maxLines = 1
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Filter")
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
                                            settingsManager.saveDefaultSearchView(mode)
                                            currentCursor = null
                                            if (mode == "recent") {
                                                loadRecent()
                                            } else {
                                            val result = repository.getPlayedBeatmaps(accessToken!!, selectedGenreId, null, searchQuery.trim().ifEmpty { null }, mode, playedFilterMode, userId, isSupporter, searchAnyEnabled)
                                                val deduped = dedupeByTitle(result.beatmaps)
                                                searchResults = deduped
                                                currentCursor = result.cursor
                                                searchResultsMetadata = filterMetadataFor(deduped, result.metadata)
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
                                    val result = repository.getPlayedBeatmaps(accessToken!!, selectedGenreId, null, searchQuery.trim(), filterMode, playedFilterMode, userId, isSupporter, searchAnyEnabled)
                                val deduped = dedupeByTitle(result.beatmaps)
                                searchResults = deduped
                                currentCursor = result.cursor
                                        searchResultsMetadata = filterMetadataFor(deduped, result.metadata)
                                }
                            } catch (e: Exception) {
                                statusText = "Search Error: ${e.message}"
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
                                Text(text = "Filter by Genre", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                                LazyRow(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                                items(genres) { (id, name) ->
                                    Button(
                                        onClick = {
                                            selectedGenreId = if (selectedGenreId == id) null else id
                                            currentCursor = null // Reset cursor when changing genre
                                            scope.launch {
                                                try {
                                                    if (filterMode == "recent") {
                                                        loadRecent()
                                                    } else {
                                                        val result = repository.getPlayedBeatmaps(accessToken, selectedGenreId, null, searchQuery.trim().ifEmpty { null }, filterMode, playedFilterMode, userId, isSupporter, searchAnyEnabled)
                                                        val deduped = dedupeByTitle(result.beatmaps)
                                                    searchResults = deduped
                                                    currentCursor = result.cursor
                                                    searchResultsMetadata = filterMetadataFor(deduped, result.metadata)
                                                    }
                                                } catch(e: Exception) {
                                                    statusText = "Error: ${e.message}"
                                                }
                                            }
                                        },
                                        modifier = Modifier.padding(end = 8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
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
                                text = "Genre filter not available in Most Played view",
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
                            .clickable {
                                if (isDownloaded) {
                                    // Play the song if downloaded
                                    scope.launch {
                                        val tracks = db.beatmapDao().getTracksForSet(map.id)
                                        if (tracks.isNotEmpty()) {
                                            // Let's fetch all downloaded songs to use as playlist context
                                            val allDownloaded = db.beatmapDao().getAllBeatmaps().first()
                                            musicController.playSong(tracks[0], allDownloaded)
                                        }
                                    }
                                }
                            }
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
                            Text(text = map.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(text = map.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                            
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
                                    text = "plays",
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
                                        downloadStates = downloadStates + (map.id to DownloadProgress(0, "Starting..."))
                                        downloader.downloadBeatmap(map.id, accessToken).collect { state ->
                                            when (state) {
                                                is DownloadState.Downloading -> {
                                                    downloadStates = downloadStates + (map.id to DownloadProgress(state.progress, "Downloading"))
                                                }
                                                is DownloadState.Downloaded -> {
                                                    downloadStates = downloadStates + (map.id to DownloadProgress(100, "Extracting..."))
                                                    try {
                                                    val extractedTracks = extractor.extractBeatmap(state.file, map.id)
                                                    extractedTracks.forEach { track ->
                                                        val entity = BeatmapEntity(
                                                            beatmapSetId = map.id,
                                                            title = track.title,
                                                            artist = track.artist,
                                                            creator = map.creator,
                                                            difficultyName = track.difficultyName,
                                                            audioPath = track.audioFile.absolutePath,
                                                            coverPath = track.coverFile?.absolutePath ?: "",
                                                            genreId = map.genreId
                                                        )
                                                        db.beatmapDao().insertBeatmap(entity)
                                                    }
                                                        downloadStates = downloadStates + (map.id to DownloadProgress(100, "Done ✓"))
                                                        // Remove from download states after 2 seconds
                                                        kotlinx.coroutines.delay(2000)
                                                        downloadStates = downloadStates - map.id
                                                    } catch (e: Exception) {
                                                        downloadStates = downloadStates + (map.id to DownloadProgress(0, "Error: ${e.message}"))
                                                    }
                                                }
                                                is DownloadState.Error -> {
                                                    downloadStates = downloadStates + (map.id to DownloadProgress(0, "Failed"))
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
                                contentDescription = if (isDownloaded) "Downloaded" else "Download",
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
                                    statusText = "Loading more..."
                                    try {
                                        val result = repository.getPlayedBeatmaps(accessToken!!, selectedGenreId, currentCursor, searchQuery.trim().ifEmpty { null }, filterMode, playedFilterMode, userId, isSupporter, searchAnyEnabled)
                                        if (result.beatmaps.isNotEmpty()) {
                                            val merged = mergeByTitle(searchResults, result.beatmaps)
                                            val mergedIds = merged.map { it.id }.toSet()
                                            searchResults = merged
                                            currentCursor = result.cursor
                                            searchResultsMetadata = (searchResultsMetadata + result.metadata).filterKeys { it in mergedIds }
                                            statusText = "Loaded ${result.beatmaps.size} more results"
                                        } else {
                                            statusText = "No more results available"
                                            currentCursor = null
                                        }
                                    } catch (e: Exception) {
                                        statusText = "Load More Error: ${e.message}"
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
                
                // Status/Error Message Display
                if (statusText.isNotEmpty()) {
                    item {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (statusText.contains("Error") || statusText.contains("Failed")) 
                                MaterialTheme.colorScheme.error 
                            else 
                                MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        )
                    }
                }
            }
            
        // Fetch user info when access token changes (login/logout)
        LaunchedEffect(accessToken) {
            if (accessToken != null) {
                try {
                    val user = repository.getMe(accessToken)
                    userId = user.id.toString()
                    isSupporter = user.isSupporter ?: false
                    isSupporterKnown = true
                } catch (e: Exception) {
                    statusText = "Failed to fetch user info: ${e.message}"
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
            
        // Initial Load - Fetch results when logged in
        LaunchedEffect(accessToken, filterMode, userId) {
            if (accessToken != null && userId != null) {
                val uid = userId ?: return@LaunchedEffect
                try {
                    if (filterMode == "recent") {
                        loadRecent()
                    } else {
                        val result = repository.getPlayedBeatmaps(accessToken, null, null, null, filterMode, playedFilterMode, uid, isSupporter, searchAnyEnabled)
                    val deduped = dedupeByTitle(result.beatmaps)
                    searchResults = deduped
                    currentCursor = result.cursor
                    searchResultsMetadata = filterMetadataFor(deduped, result.metadata)
                    }
                } catch (e: Exception) {
                    statusText = "Failed to load: ${e.message}"
                }
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
        genreId = null
    )
}
