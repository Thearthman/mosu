package com.mosu.app.domain.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class CoverDownloadService(private val context: Context) {

    suspend fun downloadFallbackCoverImage(beatmapSetId: Long, coverUrl: String): String? {
        if (coverUrl.isBlank()) return null

        return withContext(Dispatchers.IO) {
            try {
                val outputDir = File(context.getExternalFilesDir(null), "beatmaps/$beatmapSetId")
                if (!outputDir.exists()) outputDir.mkdirs()

                val target = File(outputDir, "cover_api.jpg")
                URL(coverUrl).openStream().use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
                target.absolutePath
            } catch (e: Exception) {
                Log.w("CoverDownloadService", "Failed to download fallback cover", e)
                null
            }
        }
    }
}
