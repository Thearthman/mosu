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
import com.mosu.app.data.repository.OsuRepository
import com.mosu.app.domain.model.ExtractedTrack
import com.mosu.app.domain.rating.BeatmapStarRatingService
import kotlinx.coroutines.CancellationException
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
        // Restore-download callers only know the set id, so title may be blank.
        // Use a readable label in progress/error messages without persisting it.
        val displayTitle = title.ifBlank { "#$beatmapSetId" }
        val selectedCandidate = DownloadCandidate(
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
                                context.getString(R.string.download_error_no_audio, displayTitle)
                            } else {
                                state.message
                            }
                        }
                        else -> {}
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("BeatmapDownloadService", "Error during download attempt ${candidate.id}", e)
                val isNoAudio = e.message?.contains("No audio tracks") == true
                lastErrorMessage = if (isNoAudio) {
                    context.getString(R.string.download_error_no_audio, displayTitle)
                } else {
                    e.message ?: "Download attempt failed"
                }
                Log.d("BeatmapDownloadService", "lastErrorMessage updated: $lastErrorMessage")
            }
            return attemptSuccess
        }

        try {
            // The requested set ID is canonical. Never replace it with a
            // title/artist match or register a different set under this job.
            downloadSuccessful = tryDownload(selectedCandidate)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("BeatmapDownloadService", "Fatal download service error", e)
            lastErrorMessage = e.message ?: "Fatal error"
        }

        if (!downloadSuccessful) {
            emit(UnifiedDownloadState.Error(lastErrorMessage.ifEmpty { "Download failed" }))
        }
    }

    private suspend fun registerExtractedTracks(
        targetId: Long,
        tracks: List<ExtractedTrack>,
        creator: String,
        genreId: Int?,
        isAlbumSet: Boolean,
        fallbackCoverPath: String?
    ) {
        val mediaStore = MediaStoreFileService(context)
        // One official detail lookup supplies both set-level genre metadata and
        // per-difficulty star ratings. Restore Downloads supplies no hints, so
        // this is the only metadata request needed for those jobs.
        val officialDetail = runCatching { repository.getBeatmapsetDetail(targetId) }
            .onFailure {
                Log.w("BeatmapDownloadService", "Set detail lookup failed for $targetId", it)
            }
            .getOrNull()
        val resolvedGenreId = genreId ?: officialDetail?.genreId
        runCatching {
            BeatmapStarRatingService(db, repository)
                .cacheRatingsForDownload(targetId, officialDetail)
        }.onFailure {
            Log.w("BeatmapDownloadService", "Star rating cache failed for set $targetId", it)
        }

        tracks.forEach { track ->
            // Track-level metadata (title/artist/difficulty, incl. native-script
            // variants) is authoritative from the downloaded .osu file, and so is
            // the set creator (see below). Genre is the only set-level field the
            // .osu format lacks, so that one comes from the API.
            val finalTitle = track.title
            val finalArtist = track.artist
            // Set-level creator. Empirically the .osu "Creator" field holds the
            // beatmapset host (verified: guest difficulties like "zoli's Expert"
            // still carry the set creator), so the downloaded file is authoritative
            // here too; fall back to the caller hint only if it is absent.
            val finalCreator = track.creator?.takeIf { it.isNotBlank() } ?: creator
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
                titleUnicode = track.titleUnicode,
                artistUnicode = track.artistUnicode,
                creator = finalCreator,
                difficultyName = track.difficultyName,
                audioPath = audioUri,
                coverPath = coverUri,
                genreId = resolvedGenreId,
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
