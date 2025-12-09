package com.mosu.app.domain.download

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ZipExtractor(private val context: Context) {

    /**
     * Extracts audio (.mp3/.ogg) and cover (.jpg/.png) from .osz
     * Returns the directory where files are stored.
     */
    suspend fun extractBeatmap(oszFile: File, beatmapSetId: Long): File = withContext(Dispatchers.IO) {
        val outputDir = File(context.getExternalFilesDir(null), "beatmaps/$beatmapSetId")
        if (!outputDir.exists()) outputDir.mkdirs()

        try {
            ZipFile(oszFile).use { zip ->
                val entries = zip.entries()
                
                var audioFound = false
                var coverFound = false

                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name.lowercase()

                    // Logic to find Audio
                    // Simple heuristic: largest mp3/ogg file or ends with .mp3
                    if (!audioFound && (name.endsWith(".mp3") || name.endsWith(".ogg"))) {
                        // Extract as "audio.mp3" (or keep extension) to normalize
                        val ext = if (name.endsWith(".ogg")) "ogg" else "mp3"
                        val dest = File(outputDir, "audio.$ext")
                        
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(dest).use { output -> input.copyTo(output) }
                        }
                        audioFound = true
                        Log.d("ZipExtractor", "Extracted audio: ${entry.name}")
                    }

                    // Logic to find Cover
                    // Often named "bg.jpg", "background.jpg", or just large jpgs
                    // For now, look for common background names or any jpg
                    // TODO: Improve by parsing .osu file for "Events" section background
                    if (!coverFound && (name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg"))) {
                         // A bit naive, but usually the background is the largest image or explicitly named
                         // For MVP, if it contains "bg" or "cover" or "background" prioritize it
                         val isLikelyCover = name.contains("bg") || name.contains("cover") || name.contains("background")
                         
                         if (isLikelyCover || !coverFound) { // Take first image as fallback, replace if better match found?
                            val ext = name.substringAfterLast('.')
                            val dest = File(outputDir, "cover.$ext")
                            
                            zip.getInputStream(entry).use { input ->
                                FileOutputStream(dest).use { output -> input.copyTo(output) }
                            }
                            coverFound = true // Simplification: just take the first candidate for now
                            Log.d("ZipExtractor", "Extracted cover: ${entry.name}")
                         }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ZipExtractor", "Failed to unzip", e)
            throw e
        } finally {
            // Cleanup the .osz file to save space
            if (oszFile.exists()) {
                oszFile.delete()
            }
        }
        
        return@withContext outputDir
    }
}

