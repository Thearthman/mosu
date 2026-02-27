package com.mosu.app.domain.download

import android.content.Context
import android.util.Log
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.data.services.TrackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

import android.widget.Toast
import com.mosu.app.R
import com.mosu.app.data.repository.OsuRepository
import kotlinx.coroutines.withContext
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

        // Helper function to perform a single download attempt
        suspend fun tryDownload(targetId: Long, targetCoversUrl: String?): Boolean {
            var attemptSuccess = false
            try {
                downloader.downloadBeatmap(targetId, accessToken).collect { state ->
                    when (state) {
                        is DownloadState.Downloading -> {
                            emit(UnifiedDownloadState.Progress(state.progress, state.source))
                        }
                        is DownloadState.Downloaded -> {
                            emit(UnifiedDownloadState.Progress(100, "Extracting..."))
                            val extractedTracks = extractor.extractBeatmap(state.file, targetId)
                            val isAlbumSet = extractedTracks.size > 1
                            
                            val fallbackCoverPath = if (targetCoversUrl != null && extractedTracks.any { it.coverFile == null || !it.coverFile.exists() }) {
                                coverDownloadService.downloadFallbackCoverImage(targetId, targetCoversUrl)
                            } else null

                            extractedTracks.forEach { track ->
                                // For album sets, use individual track titles from .osu files.
                                // For single sets, use the global search title/artist for state consistency.
                                val finalTitle = if (isAlbumSet) track.title else title
                                val finalArtist = if (isAlbumSet) track.artist else artist
                                
                                val entity = BeatmapEntity(
                                    beatmapSetId = targetId,
                                    title = finalTitle,
                                    artist = finalArtist,
                                    creator = creator,
                                    difficultyName = track.difficultyName,
                                    audioPath = track.audioFile.absolutePath,
                                    coverPath = track.coverFile?.absolutePath ?: fallbackCoverPath ?: "",
                                    genreId = genreId,
                                    isAlbum = isAlbumSet
                                )
                                TrackService.addTrack(entity, db, context)
                            }
                            TrackService.updateTrackDownloadStatus(targetId, true, db)
                            emit(UnifiedDownloadState.Success(targetId))
                            attemptSuccess = true
                        }
                        is DownloadState.Completed -> {
                            emit(UnifiedDownloadState.Progress(100, "Registering..."))
                            if (state.tracks.isEmpty()) throw Exception("No audio tracks found")
                            
                            val isAlbumSet = state.tracks.size > 1
                            state.tracks.forEach { track ->
                                val finalTitle = if (isAlbumSet) track.title else title
                                val finalArtist = if (isAlbumSet) track.artist else artist
                                
                                val entity = BeatmapEntity(
                                    beatmapSetId = targetId,
                                    title = finalTitle,
                                    artist = finalArtist,
                                    creator = creator,
                                    difficultyName = track.difficultyName,
                                    audioPath = track.audioFile.absolutePath,
                                    coverPath = track.coverFile?.absolutePath ?: "",
                                    genreId = genreId,
                                    isAlbum = isAlbumSet
                                )
                                TrackService.addTrack(entity, db, context)
                            }
                            TrackService.updateTrackDownloadStatus(targetId, true, db)
                            emit(UnifiedDownloadState.Success(targetId))
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
                Log.e("BeatmapDownloadService", "Error during download attempt $targetId", e)
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
            // 1. Try initial download
            downloadSuccessful = tryDownload(beatmapSetId, coversListUrl)

        // 2. If failed, try alternatives
        if (!downloadSuccessful && title.isNotBlank() && artist.isNotBlank()) {
            Log.d("BeatmapDownloadService", "Initial download failed for $beatmapSetId, checking alternatives for $title - $artist")
            
            val message = if (lastErrorMessage.isNotBlank() && !lastErrorMessage.startsWith("HTTP") && !lastErrorMessage.startsWith("Extraction") && !lastErrorMessage.startsWith("Registration") && !lastErrorMessage.startsWith("Download attempt")) {
                lastErrorMessage
            } else {
                context.getString(R.string.download_trying_alternatives, title)
            }
            emit(UnifiedDownloadState.Notification(message))

            val alternatives = try {
                repository.searchBeatmapsetsByTitleArtist(title, artist)
                    .filter { it.id != beatmapSetId }
                    .sortedBy { it.id }
            } catch (e: Exception) {
                Log.e("BeatmapDownloadService", "Error searching for alternatives", e)
                emptyList()
            }

            if (alternatives.isNotEmpty()) {
                for (alt in alternatives) {
                        Log.d("BeatmapDownloadService", "Trying alternative: ${alt.id}")
                        emit(UnifiedDownloadState.Progress(0, "Trying alternative ${alt.id}..."))
                        if (tryDownload(alt.id, alt.covers.listUrl)) {
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
}
