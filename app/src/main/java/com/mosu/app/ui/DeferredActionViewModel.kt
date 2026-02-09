package com.mosu.app.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel to manage deferred actions (like deletions) that should persist across screen navigation.
 * This ensures the grace period completes even if the user leaves the screen.
 */
class DeferredActionViewModel : ViewModel() {
    // Library pending deletions (BeatmapSet IDs)
    private val _pendingLibrarySetDeletions = MutableStateFlow<Set<Long>>(emptySet())
    val pendingLibrarySetDeletions: StateFlow<Set<Long>> = _pendingLibrarySetDeletions.asStateFlow()

    // Library pending deletions (Individual Track IDs)
    private val _pendingLibraryTrackDeletions = MutableStateFlow<Set<Long>>(emptySet())
    val pendingLibraryTrackDeletions: StateFlow<Set<Long>> = _pendingLibraryTrackDeletions.asStateFlow()

    // Playlist pending removals (Key: "playlistId|beatmapSetId|difficultyName")
    private val _pendingPlaylistRemovals = MutableStateFlow<Set<String>>(emptySet())
    val pendingPlaylistRemovals: StateFlow<Set<String>> = _pendingPlaylistRemovals.asStateFlow()

    fun addPendingLibrarySet(setId: Long) {
        _pendingLibrarySetDeletions.value += setId
    }

    fun removePendingLibrarySet(setId: Long) {
        _pendingLibrarySetDeletions.value -= setId
    }

    fun addPendingLibraryTrack(trackId: Long) {
        _pendingLibraryTrackDeletions.value += trackId
    }

    fun removePendingLibraryTrack(trackId: Long) {
        _pendingLibraryTrackDeletions.value -= trackId
    }

    fun addPendingPlaylistRemoval(key: String) {
        _pendingPlaylistRemovals.value += key
    }

    fun removePendingPlaylistRemoval(key: String) {
        _pendingPlaylistRemovals.value -= key
    }

    /**
     * Executes a deferred action with a snackbar grace period.
     * This coroutine runs in the ViewModel's scope, so it persists across navigation.
     */
    fun launchDeferredAction(
        snackbarHostState: SnackbarHostState,
        message: String,
        actionLabel: String,
        onRevert: () -> Unit,
        onConfirm: suspend () -> Unit
    ) {
        viewModelScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                onRevert()
            } else {
                onConfirm()
            }
        }
    }
}
