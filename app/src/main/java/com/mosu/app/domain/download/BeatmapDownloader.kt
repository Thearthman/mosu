package com.mosu.app.domain.download

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.TimeUnit

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int, val source: String) : DownloadState()
    data class Downloaded(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class BeatmapDownloader(private val context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Mirrors - Using well-known public mirrors
    private val mirrors = listOf(
        // Nerinyan - Fast on campus/Eduroam
        "https://api.nerinyan.moe/d/%s",
        // Sayobot - Direct download (very reliable)
        "https://dl.sayobot.cn/beatmaps/download/full/%s",
        // Mino (Catboy) - Standard mirror
        "https://catboy.best/d/%s"
    )

    fun downloadBeatmap(beatmapSetId: Long, accessToken: String? = null): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0, "Initializing..."))
        
        var success = false
        var lastError: String? = null
        val targetFile = File(context.cacheDir, "$beatmapSetId.osz")

        // Try mirrors sequentially
        for (mirrorUrl in mirrors) {
            try {
                val url = mirrorUrl.format(beatmapSetId)
                Log.d("Downloader", "Trying mirror: $url")
                
                val request = Request.Builder()
                    .url(url)
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
