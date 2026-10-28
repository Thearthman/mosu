package com.mosu.app.domain.download

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import kotlinx.coroutines.*
import com.mosu.app.data.SettingsManager
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.yield
import java.util.concurrent.TimeUnit

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

    private val nerinyanQueryParams = "?noVideo=true&noBg=false&NoHitsound=true&NoStoryboard=false"

    // Mirrors - Using well-known public mirrors
    private val sayobotMirror = "https://dl.sayobot.cn/beatmaps/download/full/%s"
    private val catboyMirror = "https://catboy.best/d/%s"
    
    private val defaultMirrors = listOf(
        // Nerinyan - Fast on campus/Eduroam
        "https://api.nerinyan.moe/d/%s$nerinyanQueryParams",
        // Nerinyan backup
        "https://ko2.nerinyan.moe/d/%s$nerinyanQueryParams",
        // Sayobot - Direct download (very reliable)
        sayobotMirror,
        // Catboy - Standard mirror
        catboyMirror
    )

    private suspend fun getOrderedMirrors(): List<String> {
        val preferredMirrorUrl = when (settingsManager.preferredMirror.first().lowercase()) {
            "sayobot" -> sayobotMirror
            "catboy" -> catboyMirror
            else -> null
        }
        return preferredMirrorUrl?.let { preferred ->
            listOf(preferred) + defaultMirrors.filter { it != preferred }
        } ?: defaultMirrors
    }

    fun downloadBeatmap(beatmapSetId: Long, accessToken: String? = null): Flow<DownloadState> = flow {
        require(beatmapSetId > 0L) { "Invalid beatmapset id: $beatmapSetId" }
        emit(DownloadState.Downloading(0, "Initializing..."))
        
        var success = false
        var lastError: String? = null
        val targetFile = File(context.cacheDir, "$beatmapSetId.osz")

        // Try mirrors sequentially
        val orderedMirrors = getOrderedMirrors()
        for (mirrorUrl in orderedMirrors) {
            currentCoroutineContext().ensureActive()
            try {
                val url = mirrorUrl.format(beatmapSetId)
                Log.d("Downloader", "Trying mirror: $url")
                
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/x-osu-beatmap-archive")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body
                        if (body != null) {
                            saveToFile(body.byteStream(), body.contentLength(), targetFile) { progress ->
                                emit(DownloadState.Downloading(progress, url))
                            }
                            success = true
                            emit(DownloadState.Downloaded(targetFile))
                        } else {
                            lastError = "Empty body from $url"
                        }
                    } else {
                        lastError = "HTTP ${response.code} from $url"
                        Log.w("Downloader", "Mirror failed: $lastError")
                    }
                }
                if (success) break
            } catch (e: CancellationException) {
                throw e
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

        var lastEmitTime = 0L
        var lastEmitProgress = -1

        inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytesCopied: Long = 0
                var bytes = input.read(buffer)
                while (bytes >= 0) {
                    kotlinx.coroutines.yield()
                    output.write(buffer, 0, bytes)
                    bytesCopied += bytes
                    
                    val currentTime = System.currentTimeMillis()
                    if (totalBytes > 0) {
                        val progress = (bytesCopied * 100 / totalBytes).toInt()
                        // Throttle: emit at most every 200ms or if progress changed
                        if (progress != lastEmitProgress && (currentTime - lastEmitTime > 200 || progress == 100)) {
                            onProgress(progress)
                            lastEmitProgress = progress
                            lastEmitTime = currentTime
                        }
                    } else {
                        // Unknown total bytes - just show we're alive every 500ms
                        // We could use a special progress value or just keep it at -1/0 but update the UI
                        if (currentTime - lastEmitTime > 500) {
                            onProgress(-1) // Signal "indeterminate progress"
                            lastEmitTime = currentTime
                        }
                    }
                    
                    bytes = input.read(buffer)
                }
                
                // Final progress
                if (totalBytes > 0) onProgress(100)
                
                // Verify download completion if totalBytes is known
                if (totalBytes > 0 && bytesCopied < totalBytes) {
                    throw IOException("Download truncated: expected $totalBytes bytes, got $bytesCopied")
                }
            }
        }
    }
}
