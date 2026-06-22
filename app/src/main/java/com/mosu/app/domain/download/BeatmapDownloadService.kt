package com.mosu.app.domain.download

import android.content.Context
import android.util.Log
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.data.media.MediaStoreFileService
import com.mosu.app.data.media.MosuBackupService
import com.mosu.app.data.services.TrackService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

import com.mosu.app.R
import com.mosu.app.data.api.model.BeatmapsetCompact
import com.mosu.app.data.repository.OsuRepository
import com.mosu.app.domain.model.ExtractedTrack
import com.mosu.app.domain.model.sortedByTotalBeatmapPlayCountDescending
import kotlinx.coroutines.flow.collect

sealed class UnifiedDownloadState {
    data class Progress(val progress: Int, val status: String) : UnifiedDownloadState()
    data class Success(val beatmapSetId: Long) : UnifiedDownloadState()
    data class Error(val message: String) : UnifiedDownloadState()
    data class Notification(val message: String) : UnifiedDownloadState()
}

class BeatmapDownloadService(
    private val context: Context,
    private val downloader: BeatmapDownloader,
    private val extractor: ZipExtractor,
    private val coverDownloadService: CoverDownloadService,
    private val db: AppDatabase,
    private val repository: OsuRepository
) {
    fun downloadBeatmap(
        beatmapSetId: Long,
        accessToken: String? = null,
        title: String,
        artist: String,
        creator: String,
        genreId: Int? = null,
        coversListUrl: String? = null
    ): Flow<UnifiedDownloadState> = flow {
        var downloadSuccessful = false
        var lastErrorMessage = ""
        var selectedCandidate = DownloadCandidate(
            id = beatmapSetId,
            title = title,
            artist = artist,
            creator = creator,
            genreId = genreId,
            coversListUrl = coversListUrl
        )

        // Helper function to perform a single download attempt
        suspend fun tryDownload(candidate: DownloadCandidate): Boolean {
            var attemptSuccess = false
            try {
                downloader.downloadBeatmap(candidate.id, accessToken).collect { state ->
                    when (state) {
                        is DownloadState.Downloading -> {
                            emit(UnifiedDownloadState.Progress(state.progress, state.source))
                        }
                        is DownloadState.Downloaded -> {
                            emit(UnifiedDownloadState.Progress(100, "Extracting..."))
                            val extractedTracks = extractor.extractBeatmap(state.file, candidate.id)
                            val isAlbumSet = extractedTracks.size > 1

                            val fallbackCoverPath = if (candidate.coversListUrl != null && extractedTracks.any { it.coverFile == null || !it.coverFile.exists() }) {
                                coverDownloadService.downloadFallbackCoverImage(candidate.id, candidate.coversListUrl)
                            } else null

                            registerExtractedTracks(
                                targetId = candidate.id,
                                tracks = extractedTracks,
                                title = candidate.title,
                                artist = candidate.artist,
                                creator = candidate.creator,
                                genreId = candidate.genreId,
                                isAlbumSet = isAlbumSet,
                                fallbackCoverPath = fallbackCoverPath
                            )
                            emit(UnifiedDownloadState.Success(candidate.id))
                            attemptSuccess = true
                        }
                        is DownloadState.Completed -> {
                            emit(UnifiedDownloadState.Progress(100, "Registering..."))
                            if (state.tracks.isEmpty()) throw Exception("No audio tracks found")

                            val isAlbumSet = state.tracks.size > 1
                            registerExtractedTracks(
                                targetId = candidate.id,
                                tracks = state.tracks,
                                title = candidate.title,
                                artist = candidate.artist,
                                creator = candidate.creator,
                                genreId = candidate.genreId,
                                isAlbumSet = isAlbumSet,
                                fallbackCoverPath = null
                            )
                            emit(UnifiedDownloadState.Success(candidate.id))
                            attemptSuccess = true
                        }
                        is DownloadState.Error -> {
                            lastErrorMessage = if (state.message == "No audio tracks found") {
                                context.getString(R.string.download_error_no_audio, title)
                            } else {
                                state.message
                            }
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                Log.e("BeatmapDownloadService", "Error during download attempt ${candidate.id}", e)
                val isNoAudio = e.message?.contains("No audio tracks") == true
                lastErrorMessage = if (isNoAudio) {
                    context.getString(R.string.download_error_no_audio, title)
                } else {
                    e.message ?: "Download attempt failed"
                }
                Log.d("BeatmapDownloadService", "lastErrorMessage updated: $lastErrorMessage")
            }
            return attemptSuccess
        }

        try {
            val preferredBeatmapset = findPreferredDownloadBeatmapset(title, artist)
            if (preferredBeatmapset != null) {
                selectedCandidate = preferredBeatmapset.toDownloadCandidate()
                if (selectedCandidate.id != beatmapSetId) {
                    Log.d(
                        "BeatmapDownloadService",
                        "Resolved download target $beatmapSetId to most-played matching set ${selectedCandidate.id} for $title - $artist"
                    )
                    emit(UnifiedDownloadState.Progress(0, "Selected beatmapset ${selectedCandidate.id}..."))
                }
            }

            // 1. Try the most-played exact-title/artist match when metadata is available.
            downloadSuccessful = tryDownload(selectedCandidate)

        // 2. If failed, try alternatives
        if (!downloadSuccessful && title.isNotBlank() && artist.isNotBlank()) {
            Log.d("BeatmapDownloadService", "Initial download failed for ${selectedCandidate.id}, checking alternatives for $title - $artist")
            
            val message = if (lastErrorMessage.isNotBlank() && !lastErrorMessage.startsWith("HTTP") && !lastErrorMessage.startsWith("Extraction") && !lastErrorMessage.startsWith("Registration") && !lastErrorMessage.startsWith("Download attempt")) {
                lastErrorMessage
            } else {
                context.getString(R.string.download_trying_alternatives, title)
            }
            emit(UnifiedDownloadState.Notification(message))

            val alternatives = try {
                repository.searchBeatmapsetsByTitleArtist(title, artist)
                    .filter { it.id != selectedCandidate.id }
                    .sortedByTotalBeatmapPlayCountDescending()
            } catch (e: Exception) {
                Log.e("BeatmapDownloadService", "Error searching for alternatives", e)
                emptyList()
            }

            if (alternatives.isNotEmpty()) {
                for (alt in alternatives) {
                        Log.d("BeatmapDownloadService", "Trying alternative: ${alt.id}")
                        emit(UnifiedDownloadState.Progress(0, "Trying alternative ${alt.id}..."))
                        if (tryDownload(alt.toDownloadCandidate())) {
                            downloadSuccessful = true
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BeatmapDownloadService", "Fatal download service error", e)
            lastErrorMessage = e.message ?: "Fatal error"
        }

        if (!downloadSuccessful) {
            emit(UnifiedDownloadState.Error(lastErrorMessage.ifEmpty { "Download failed" }))
        }
    }

    private suspend fun findPreferredDownloadBeatmapset(
        title: String,
        artist: String
    ): BeatmapsetCompact? {
        if (!hasSearchableMetadata(title, artist)) return null
        return runCatching { repository.findMostPlayedBeatmapsetByTitleArtist(title, artist) }
            .onFailure { Log.e("BeatmapDownloadService", "Error resolving preferred download beatmapset", it) }
            .getOrNull()
            ?.takeIf { it.id > 0L }
    }

    private fun hasSearchableMetadata(title: String, artist: String): Boolean {
        return title.isNotBlank() &&
            artist.isNotBlank() &&
            !title.matches(Regex("""Beatmap \d+"""))
    }

    private fun BeatmapsetCompact.toDownloadCandidate(): DownloadCandidate {
        return DownloadCandidate(
            id = id,
            title = title,
            artist = artist,
            creator = creator,
            genreId = genreId,
            coversListUrl = covers.listUrl
        )
    }

    private suspend fun registerExtractedTracks(
        targetId: Long,
        tracks: List<ExtractedTrack>,
        title: String,
        artist: String,
        creator: String,
        genreId: Int?,
        isAlbumSet: Boolean,
        fallbackCoverPath: String?
    ) {
        val mediaStore = MediaStoreFileService(context)

        tracks.forEach { track ->
            // For album sets, use individual track titles from .osu files.
            // For single sets, use the global search title/artist for state consistency.
            val finalTitle = if (isAlbumSet) track.title else title
            val finalArtist = if (isAlbumSet) track.artist else artist
            val audioUri = mediaStore.copyAudioToMediaStore(
                source = track.audioFile,
                beatmapSetId = targetId,
                title = finalTitle,
                artist = finalArtist,
                difficultyName = track.difficultyName
            )
            val coverSource = track.coverFile?.takeIf { it.exists() }
                ?: fallbackCoverPath?.let { java.io.File(it) }?.takeIf { it.exists() }
            val coverUri = coverSource?.let {
                mediaStore.copyImageToMediaStore(
                    source = it,
                    beatmapSetId = targetId,
                    title = finalTitle,
                    difficultyName = track.difficultyName
                )
            }.orEmpty()

            val entity = BeatmapEntity(
                beatmapSetId = targetId,
                title = finalTitle,
                artist = finalArtist,
                creator = creator,
                difficultyName = track.difficultyName,
                audioPath = audioUri,
                coverPath = coverUri,
                genreId = genreId,
                isAlbum = isAlbumSet
            )
            TrackService.addTrack(entity, db, context)
        }

        TrackService.updateTrackDownloadStatus(targetId, true, db)
        MosuBackupService.exportState(context, db)
    }
}

private data class DownloadCandidate(
    val id: Long,
    val title: String,
    val artist: String,
    val creator: String,
    val genreId: Int?,
    val coversListUrl: String?
)
