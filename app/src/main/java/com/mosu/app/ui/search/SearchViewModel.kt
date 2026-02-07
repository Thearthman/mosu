package com.mosu.app.ui.search

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mosu.app.data.api.model.BeatmapsetCompact
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.repository.OsuRepository
import com.mosu.app.domain.search.BeatmapSearchService
import com.mosu.app.domain.search.RecentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchViewModel(
    val repository: OsuRepository,
    val db: AppDatabase,
    context: Context
) : ViewModel() {

    val searchService = BeatmapSearchService(repository, db, context)

    // Search state
    var searchQuery by mutableStateOf("")
    var filterMode by mutableStateOf("")
    var searchResults by mutableStateOf<List<BeatmapsetCompact>>(emptyList())
    var recentGroupedResults by mutableStateOf<List<RecentItem>>(emptyList())
    var searchResultsMetadata by mutableStateOf<Map<Long, Pair<Int, Int>>>(emptyMap())
    var recentTimestamps by mutableStateOf<Map<Long, Long>>(emptyMap())
    var selectedGenreId by mutableStateOf<Int?>(null)
    var currentCursor by mutableStateOf<String?>(null)
    var isLoadingMore by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
    var expandedBeatmapSets by mutableStateOf<Set<Long>>(emptySet())
    var refreshTick by mutableStateOf(0)

    // Download state
    var downloadStates by mutableStateOf<Map<Long, DownloadProgress>>(emptyMap())
    var downloadedBeatmapSetIds by mutableStateOf<Set<Long>>(emptySet())
    var downloadedKeys by mutableStateOf<Set<String>>(emptySet())
    var mergeGroups by mutableStateOf<Map<String, Set<Long>>>(emptyMap())

    // Info popup state
    var infoDialogVisible by mutableStateOf(false)
    var infoLoading by mutableStateOf(false)
    var infoError by mutableStateOf<String?>(null)
    var infoTarget by mutableStateOf<BeatmapsetCompact?>(null)
    var infoSets by mutableStateOf<List<BeatmapsetCompact>>(emptyList())

    // User state
    var userId by mutableStateOf<String?>(null)
    var isSupporter by mutableStateOf(false)
    var isSupporterKnown by mutableStateOf(false)

    // Tracking to prevent redundant loads on navigation return
    var lastInitialLoadKey: String? = null
    var lastSyncedDefaultView: String? = null
    private var infoLoadJob: Job? = null

    init {
        viewModelScope.launch {
            db.beatmapDao().getAllBeatmaps().collect { beatmaps ->
                withContext(Dispatchers.Default) {
                    val setIds = beatmaps.map { it.beatmapSetId }.toSet()
                    val keys = beatmaps.map { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }.toSet()
                    withContext(Dispatchers.Main) {
                        downloadedBeatmapSetIds = setIds
                        downloadedKeys = keys
                    }
                }
            }
        }
    }

    fun loadInfoPopup(errorFallback: String) {
        val target = infoTarget ?: return
        if (!infoDialogVisible) return
        infoLoadJob?.cancel()
        infoLoadJob = viewModelScope.launch {
            infoLoading = true
            infoError = null
            infoSets = emptyList()
            val result = searchService.loadInfoPopup(target.title, target.artist)
            result.onSuccess { sets -> infoSets = sets }
            result.onFailure { e -> infoError = e.message ?: errorFallback }
            infoLoading = false
        }
    }

    fun resetOnLogout() {
        searchResults = emptyList()
        recentGroupedResults = emptyList()
        searchResultsMetadata = emptyMap()
        recentTimestamps = emptyMap()
        lastInitialLoadKey = null
    }
}

class SearchViewModelFactory(
    private val repository: OsuRepository,
    private val db: AppDatabase,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SearchViewModel(repository, db, context) as T
    }
}
