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

sealed class UnifiedDownloadState {
    data class Progress(val progress: Int, val status: String) : UnifiedDownloadState()
    data class Success(val beatmapSetId: Long) : UnifiedDownloadState()
    data class Error(val message: String) : UnifiedDownloadState()
}

class BeatmapDownloadService(
    private val context: Context,
    private val downloader: BeatmapDownloader,
    private val extractor: ZipExtractor,
    private val coverDownloadService: CoverDownloadService,
    private val db: AppDatabase
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
        downloader.downloadBeatmap(beatmapSetId, accessToken).collect { state ->
            when (state) {
                is DownloadState.Downloading -> {
                    emit(UnifiedDownloadState.Progress(state.progress, state.source))
                }
                is DownloadState.Downloaded -> {
                    emit(UnifiedDownloadState.Progress(100, "Extracting..."))
                    try {
                        val extractedTracks = extractor.extractBeatmap(state.file, beatmapSetId)
                        val isAlbumSet = extractedTracks.size > 1
                        
                        // Handle fallback cover if needed
                        val fallbackCoverPath = if (coversListUrl != null && extractedTracks.any { it.coverFile == null || !it.coverFile!!.exists() }) {
                            coverDownloadService.downloadFallbackCoverImage(beatmapSetId, coversListUrl)
                        } else null

                        extractedTracks.forEach { track ->
                            val entity = BeatmapEntity(
                                beatmapSetId = beatmapSetId,
                                title = track.title,
                                artist = track.artist,
                                creator = creator,
                                difficultyName = track.difficultyName,
                                audioPath = track.audioFile.absolutePath,
                                coverPath = track.coverFile?.absolutePath ?: fallbackCoverPath ?: "",
                                genreId = genreId,
                                isAlbum = isAlbumSet
                            )
                            TrackService.addTrack(entity, db, context)
                        }
                        TrackService.updateTrackDownloadStatus(beatmapSetId, true, db)
                        emit(UnifiedDownloadState.Success(beatmapSetId))
                    } catch (e: Exception) {
                        emit(UnifiedDownloadState.Error("Extraction failed: ${e.message}"))
                    }
                }
                is DownloadState.Completed -> {
                    emit(UnifiedDownloadState.Progress(100, "Registering..."))
                    try {
                        val isAlbumSet = state.tracks.size > 1
                        state.tracks.forEach { track ->
                            val entity = BeatmapEntity(
                                beatmapSetId = beatmapSetId,
                                title = track.title,
                                artist = track.artist,
                                creator = creator,
                                difficultyName = track.difficultyName,
                                audioPath = track.audioFile.absolutePath,
                                coverPath = track.coverFile?.absolutePath ?: "",
                                genreId = genreId,
                                isAlbum = isAlbumSet
                            )
                            TrackService.addTrack(entity, db, context)
                        }
                        TrackService.updateTrackDownloadStatus(beatmapSetId, true, db)
                        emit(UnifiedDownloadState.Success(beatmapSetId))
                    } catch (e: Exception) {
                        emit(UnifiedDownloadState.Error("Registration failed: ${e.message}"))
                    }
                }
                is DownloadState.Error -> {
                    emit(UnifiedDownloadState.Error(state.message))
                }
                else -> {}
            }
        }
    }.flowOn(Dispatchers.IO)
}
