package com.mosu.app.domain.download

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import kotlinx.coroutines.flow.emitAll
import com.mosu.app.data.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.TimeUnit

import com.mosu.app.data.api.model.BeatmapDetail
import com.mosu.app.data.api.model.BeatmapsetDetail
import com.mosu.app.data.api.model.SayobotDetailResponse
import com.google.gson.Gson

import com.mosu.app.domain.model.ExtractedTrack

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int, val source: String) : DownloadState()
    data class Downloaded(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
    data class Completed(val tracks: List<ExtractedTrack>, val beatmapSetId: Long) : DownloadState()
}

class BeatmapDownloader(
    private val context: Context,
    private val settingsManager: SettingsManager
) {
    // ... (rest of the file)
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val gson = Gson()

    private val nerinyanQueryParams = "?noVideo=true&noBg=false&NoHitsound=true&NoStoryboard=false"

    // Mirrors - Using well-known public mirrors
    private val sayobotMirror = "https://dl.sayobot.cn/beatmaps/download/full/%s"
    private val sayobotAudio = "https://dl.sayobot.cn/beatmaps/files/%s/%s"
    private val sayobotImage = "https://dl.sayobot.cn/beatmaps/files/%s/%s"
    
    private val defaultMirrors = listOf(
        // Nerinyan - Fast on campus/Eduroam
        "https://api.nerinyan.moe/d/%s$nerinyanQueryParams",
        // Nerinyan backup
        "https://ko2.nerinyan.moe/d/%s$nerinyanQueryParams",
        // Sayobot - Direct download (very reliable)
        sayobotMirror,
        // Mino (Catboy) - Standard mirror
        "https://catboy.best/d/%s"
    )

    private suspend fun getOrderedMirrors(): List<String> {
        val preferredMirror = settingsManager.preferredMirror.first()
        return if (preferredMirror == "sayobot") {
            listOf(sayobotMirror) + defaultMirrors.filter { it != sayobotMirror }
        } else {
            defaultMirrors
        }
    }

    suspend fun downloadDirectlyFromSayobot(beatmapSetId: Long): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0, "Fetching file list..."))
        
        try {
            // 1. Fetch Sayobot detail to get file names
            val infoUrl = "https://api.sayobot.cn/v2/beatmapinfo?K=$beatmapSetId&T=0"
            val infoRequest = Request.Builder().url(infoUrl).build()
            val infoResponse = client.newCall(infoRequest).execute()
            
            if (!infoResponse.isSuccessful) throw IOException("Failed to fetch beatmap info")
            
            val jsonStr = infoResponse.body?.string() ?: throw IOException("Empty info body")
            val detailResponse = gson.fromJson(jsonStr, SayobotDetailResponse::class.java)
            val detailData = detailResponse.data
            
            // Collect unique files to download
            val filesToDownload = mutableSetOf<String>()
            detailData.bidData.forEach { bid ->
                bid.audio?.let { if (it.isNotEmpty()) filesToDownload.add(it) }
                bid.bg?.let { if (it.isNotEmpty()) filesToDownload.add(it) }
            }

            val targetDir = File(context.getExternalFilesDir(null), "beatmaps/$beatmapSetId")
            if (!targetDir.exists()) targetDir.mkdirs()

            // Download unique files
            val totalFiles = filesToDownload.size
            var completedFilesCount = 0
            for (filename in filesToDownload) {
                val downloadUrl = sayobotAudio.format(beatmapSetId, filename)
                val targetFile = File(targetDir, filename)
                
                if (!targetFile.exists() || targetFile.length() == 0L) {
                    emit(DownloadState.Downloading((completedFilesCount * 100) / totalFiles, "Downloading $filename"))
                    val request = Request.Builder().url(downloadUrl).build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful && response.body != null) {
                        saveToFile(response.body!!.byteStream(), response.body!!.contentLength(), targetFile) { _ -> }
                    }
                }
                completedFilesCount++
            }
            
            // 2. Register one track for each UNIQUE audio file (matching ZipExtractor logic)
            val extractedTracks = mutableListOf<ExtractedTrack>()
            val uniqueAudioFiles = mutableSetOf<String>()
            
            detailData.bidData.forEach { bid ->
                val audioFilename = bid.audio
                if (!audioFilename.isNullOrEmpty() && !uniqueAudioFiles.contains(audioFilename)) {
                    uniqueAudioFiles.add(audioFilename)
                    
                    val audioFile = File(targetDir, audioFilename)
                    val coverFile = if (!bid.bg.isNullOrEmpty()) File(targetDir, bid.bg) else null
                    
                    if (audioFile.exists()) {
                        extractedTracks.add(ExtractedTrack(
                            audioFile = audioFile,
                            coverFile = if (coverFile?.exists() == true) coverFile else null,
                            title = detailData.title,
                            artist = detailData.artist,
                            difficultyName = bid.version
                        ))
                    }
                }
            }
            
            emit(DownloadState.Completed(extractedTracks, beatmapSetId))

        } catch (e: Exception) {
            Log.e("Downloader", "Direct download failed", e)
            emit(DownloadState.Error("Direct download failed: ${e.message}"))
        }
    }

    fun downloadBeatmap(beatmapSetId: Long, accessToken: String? = null): Flow<DownloadState> = flow {
        val preferredMirror = settingsManager.preferredMirror.first()
        
        // Use direct file download for Sayobot
        if (preferredMirror == "sayobot") {
             emitAll(downloadDirectlyFromSayobot(beatmapSetId))
             return@flow
        }

        emit(DownloadState.Downloading(0, "Initializing..."))
        
        var success = false
        var lastError: String? = null
        val targetFile = File(context.cacheDir, "$beatmapSetId.osz")

        // Try mirrors sequentially
        val orderedMirrors = getOrderedMirrors()
        for (mirrorUrl in orderedMirrors) {
            try {
                val url = mirrorUrl.format(beatmapSetId)
                Log.d("Downloader", "Trying mirror: $url")
                
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/x-osu-beatmap-archive")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val body = response.body
                    if (body != null) {
                        saveToFile(body.byteStream(), body.contentLength(), targetFile) { progress ->
                            emit(DownloadState.Downloading(progress, url))
                        }
                        success = true
                        emit(DownloadState.Downloaded(targetFile))
                        break // Success!
                    } else {
                        lastError = "Empty body from $url"
                    }
                } else {
                    lastError = "HTTP ${response.code} from $url"
                    Log.w("Downloader", "Mirror failed: $lastError")
                }
            } catch (e: Exception) {
                lastError = "${e.javaClass.simpleName}: ${e.message}"
                Log.e("Downloader", "Mirror exception: $mirrorUrl", e)
            }
        }

        if (!success) {
            emit(DownloadState.Error("All mirrors failed. Last error: $lastError"))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun saveToFile(
        inputStream: InputStream?, 
        totalBytes: Long, 
        targetFile: File,
        onProgress: suspend (Int) -> Unit
    ) {
        if (inputStream == null) throw IOException("Empty body")

        inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytesCopied: Long = 0
                var bytes = input.read(buffer)
                while (bytes >= 0) {
                    output.write(buffer, 0, bytes)
                    bytesCopied += bytes
                    
                    if (totalBytes > 0) {
                        val progress = (bytesCopied * 100 / totalBytes).toInt()
                        onProgress(progress)
                    }
                    
                    bytes = input.read(buffer)
                }
            }
        }
    }
}
