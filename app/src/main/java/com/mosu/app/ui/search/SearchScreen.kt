package com.mosu.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mosu.app.data.api.model.BeatmapsetCompact
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.data.repository.OsuRepository
import com.mosu.app.domain.download.BeatmapDownloader
import com.mosu.app.domain.download.DownloadState
import com.mosu.app.domain.download.ZipExtractor
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
    onTokenReceived: (String) -> Unit
) {
    var statusText by remember { mutableStateOf("") }
    
    // Search Query
    var searchQuery by remember { mutableStateOf("") }
    
    // Search Results
    var searchResults by remember { mutableStateOf<List<BeatmapsetCompact>>(emptyList()) }
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

    val genres = listOf(
        10 to "Electronic", 3 to "Anime", 4 to "Rock", 5 to "Pop",
        2 to "Game", 9 to "Hip Hop", 11 to "Metal", 12 to "Classical",
        13 to "Folk", 14 to "Jazz", 7 to "Novelty", 6 to "Other"
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (accessToken == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Not Logged In", style = MaterialTheme.typography.titleMedium)
                    Text("Please go to the Profile tab to configure credentials and login.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(text = statusText, modifier = Modifier.padding(top = 8.dp))
        } else {
            // Search Bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                placeholder = { Text("Search by title or artist...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            // Refresh results without search query
                            scope.launch {
                                try {
                                    val (results, cursor) = repository.getPlayedBeatmaps(accessToken!!, selectedGenreId, null, null)
                                    searchResults = results
                                    currentCursor = cursor
                                } catch (e: Exception) {
                                    statusText = "Error: ${e.message}"
                                }
                            }
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
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
                                val (results, cursor) = repository.getPlayedBeatmaps(accessToken!!, selectedGenreId, null, searchQuery.trim())
                                searchResults = results
                                currentCursor = cursor
                            } catch (e: Exception) {
                                statusText = "Search Error: ${e.message}"
                            }
                        }
                    }
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            )
            
            // Genre Filter
            Text(text = "Filter by Genre", style = MaterialTheme.typography.labelMedium)
            LazyRow(modifier = Modifier.padding(vertical = 8.dp)) {
                items(genres) { (id, name) ->
                    Button(
                        onClick = {
                            selectedGenreId = if (selectedGenreId == id) null else id
                            currentCursor = null // Reset cursor when changing genre
                            scope.launch {
                                try {
                                    val (results, cursor) = repository.getPlayedBeatmaps(accessToken, selectedGenreId, null, searchQuery.trim().ifEmpty { null })
                                    searchResults = results
                                    currentCursor = cursor
                                } catch(e: Exception) {
                                    statusText = "Error: ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedGenreId == id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (selectedGenreId == id) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text(name)
                    }
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Search Results List
            LazyColumn {
                items(searchResults) { map ->
                    val downloadProgress = downloadStates[map.id]
                    val isDownloaded = downloadedBeatmapSetIds.contains(map.id)
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                                                                coverPath = track.coverFile?.absolutePath ?: ""
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
                    Divider(modifier = Modifier.padding(start = 64.dp))
                }
                
                // Pagination / Load More
                if (searchResults.isNotEmpty() && currentCursor != null) {
                    item {
                        Button(
                            onClick = {
                                scope.launch {
                                    isLoadingMore = true
                                    statusText = "Loading more..."
                                    try {
                                        val (moreResults, nextCursor) = repository.getPlayedBeatmaps(accessToken!!, selectedGenreId, currentCursor, searchQuery.trim().ifEmpty { null })
                                        if (moreResults.isNotEmpty()) {
                                            searchResults = searchResults + moreResults
                                            currentCursor = nextCursor
                                            statusText = "Loaded ${moreResults.size} more results"
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
            }
        }

        // Auth Logic - Handle OAuth callback
        LaunchedEffect(authCode) {
            if (authCode != null && accessToken == null && clientId.isNotEmpty() && clientSecret.isNotEmpty()) {
                try {
                    val tokenResponse = repository.exchangeCodeForToken(authCode, clientId, clientSecret)
                    onTokenReceived(tokenResponse.accessToken)
                } catch (e: Exception) {
                    statusText = "Login Error: ${e.message}"
                }
            }
        }
        
        // Initial Load - Fetch results when logged in
        LaunchedEffect(accessToken) {
            if (accessToken != null && searchResults.isEmpty()) {
                try {
                    val (results, cursor) = repository.getPlayedBeatmaps(accessToken, null, null, null)
                    searchResults = results
                    currentCursor = cursor
                } catch (e: Exception) {
                    statusText = "Failed to load: ${e.message}"
                }
            }
        }
    }
}

