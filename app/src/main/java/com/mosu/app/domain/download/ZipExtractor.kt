package com.mosu.app.domain.download

import android.content.Context
import android.util.Log
import com.mosu.app.domain.model.ExtractedTrack
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.IOException

class ZipExtractor(private val context: Context) {

    suspend fun extractBeatmap(oszFile: File, beatmapSetId: Long): List<ExtractedTrack> = withContext(Dispatchers.IO) {
        val outputDir = File(context.getExternalFilesDir(null), "beatmaps/$beatmapSetId")
        if (!outputDir.exists()) outputDir.mkdirs()

        val extractedTracks = mutableListOf<ExtractedTrack>()
        val osuFiles = mutableListOf<String>() // Names of .osu files

        try {
            ZipFile(oszFile).use { zip ->
                val entriesList = zip.entries().toList()
                
                // Extract .osu files to parse them
                entriesList.filter { it.name.endsWith(".osu") }.forEach { entry ->
                    val dest = File(outputDir, entry.name)
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(dest).use { output -> input.copyTo(output) }
                    }
                    osuFiles.add(entry.name)
                }

                // 2. Parse each .osu file to find associations
                val uniqueAudioFiles = mutableSetOf<String>()
                
                osuFiles.forEach { osuFileName ->
                    kotlinx.coroutines.yield()
                    val osuFile = File(outputDir, osuFileName)
                    val metadata = parseOsuFile(osuFile)
                    
                    if (metadata != null) {
                        // Avoid duplicates if multiple difficulties share same audio/bg (very common)
                        // We construct a track for EACH difficulty, or group?
                        // User wants "Album" -> "Different songs under same map".
                        // Usually 1 mapset = 1 song (audio.mp3) + N difficulties.
                        // User specifically said "multiple music present... multiple difficulty with different music".
                        
                        // We will treat each Unique Audio File as a "Track".
                        if (!uniqueAudioFiles.contains(metadata.audioFilename)) {
                            uniqueAudioFiles.add(metadata.audioFilename)
                            
                            // Extract Audio
                            val audioEntry = entriesList.find { entry ->
                                val entryPath = entry.name.replace("\\", "/")
                                val audioPath = metadata.audioFilename.replace("\\", "/")
                                entryPath.equals(audioPath, ignoreCase = true)
                            }
                            var extractedAudio: File? = null
                            
                            if (audioEntry != null) {
                                // Extract to flat structure (remove subdirectories)
                                val flatFilename = audioEntry.name.replace("\\", "/").substringAfterLast("/")
                                extractedAudio = File(outputDir, flatFilename)
                                
                                if (!extractedAudio.exists()) {
                                    try {
                                        zip.getInputStream(audioEntry).use { input ->
                                            FileOutputStream(extractedAudio).use { output -> input.copyTo(output) }
                                        }
                                    } catch (e: Exception) {
                                        Log.w("ZipExtractor", "Failed to extract audio: ${audioEntry.name}", e)
                                        extractedAudio = null
                                    }
                                }
                            } else {
                                Log.w("ZipExtractor", "Audio file not found in zip: ${metadata.audioFilename}")
                            }

                            // Extract Background
                            // Logic: weight "bg"/"background", but prefer the one linked in .osu
                            var extractedCover: File? = null
                            val bgName = metadata.backgroundFilename
                            
                            if (bgName != null) {
                                // Handle subdirectories - find entry with matching filename
                                val bgEntry = entriesList.find { entry ->
                                    // Normalize paths for comparison
                                    val entryPath = entry.name.replace("\\", "/")
                                    val bgPath = bgName.replace("\\", "/")
                                    entryPath.equals(bgPath, ignoreCase = true)
                                }
                                
                                if (bgEntry != null) {
                                    // Extract to flat structure (remove subdirectories)
                                    val flatFilename = bgEntry.name.replace("\\", "/").substringAfterLast("/")
                                    extractedCover = File(outputDir, flatFilename)
                                    
                                    if (!extractedCover.exists()) {
                                        try {
                                            zip.getInputStream(bgEntry).use { input ->
                                                FileOutputStream(extractedCover).use { output -> input.copyTo(output) }
                                            }
                                        } catch (e: Exception) {
                                            Log.w("ZipExtractor", "Failed to extract cover: ${bgEntry.name}", e)
                                            extractedCover = null
                                        }
                                    }
                                } else {
                                    Log.w("ZipExtractor", "Background file not found in zip: $bgName")
                                }
                            }
                            
                            // If no BG in .osu or extraction failed, fallback to heuristic
                            if (extractedCover == null || !extractedCover.exists()) {
                                val fallbackEntry = entriesList.find { entry ->
                                    val n = entry.name.lowercase().replace("\\", "/")
                                    !entry.isDirectory &&
                                    (n.endsWith(".jpg") || n.endsWith(".png") || n.endsWith(".jpeg")) && 
                                    (n.contains("bg") || n.contains("background") || n.contains("cover"))
                                } ?: entriesList.find { entry ->
                                    val n = entry.name.lowercase()
                                    !entry.isDirectory && (n.endsWith(".jpg") || n.endsWith(".png") || n.endsWith(".jpeg"))
                                }
                                
                                if (fallbackEntry != null) {
                                    val flatFilename = fallbackEntry.name.replace("\\", "/").substringAfterLast("/")
                                    extractedCover = File(outputDir, flatFilename)
                                    if (!extractedCover.exists()) {
                                        try {
                                            zip.getInputStream(fallbackEntry).use { input ->
                                                FileOutputStream(extractedCover).use { output -> input.copyTo(output) }
                                            }
                                        } catch (e: Exception) {
                                            Log.w("ZipExtractor", "Failed to extract fallback cover", e)
                                            extractedCover = null
                                        }
                                    }
                                }
                            }

                            if (extractedAudio != null) {
                                extractedTracks.add(ExtractedTrack(
                                    audioFile = extractedAudio,
                                    coverFile = extractedCover,
                                    title = metadata.title,
                                    artist = metadata.artist,
                                    difficultyName = metadata.version
                                ))
                            }
                        }
                    }
                    // Delete .osu file after parsing (optional, maybe keep for future)
                    osuFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("ZipExtractor", "Failed to unzip", e)
            throw e
        } finally {
            if (oszFile.exists()) {
                oszFile.delete()
            }
        }
        
        if (extractedTracks.isEmpty()) {
            throw IOException("No audio tracks found in zip")
        }
        
        return@withContext extractedTracks
    }

    private data class OsuMetadata(
        val audioFilename: String,
        val backgroundFilename: String?,
        val title: String,
        val artist: String,
        val version: String
    )

    private fun parseOsuFile(file: File): OsuMetadata? {
        var audioFilename = ""
        var bgFilename: String? = null
        var title = ""
        var artist = ""
        var version = ""
        
        try {
            file.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("AudioFilename:")) {
                    audioFilename = trimmed.substringAfter(":").trim()
                } else if (trimmed.startsWith("Title:")) {
                    title = trimmed.substringAfter(":").trim()
                } else if (trimmed.startsWith("Artist:")) {
                    artist = trimmed.substringAfter(":").trim()
                } else if (trimmed.startsWith("Version:")) {
                    version = trimmed.substringAfter(":").trim()
                } else if (trimmed.startsWith("0,0,") && (trimmed.endsWith(".jpg") || trimmed.endsWith(".png") || trimmed.endsWith(".jpeg"))) {
                    // Events section background line usually looks like: 0,0,"bg.jpg",0,0
                    // But regex is safer. Or simple string split.
                    // Background events start with 0,0 (Background), then filename in quotes
                    val parts = trimmed.split(",")
                    if (parts.size >= 3) {
                        val potentialBg = parts[2].replace("\"", "")
                        if (potentialBg.contains(".")) {
                            bgFilename = potentialBg
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return null
        }
        
        if (audioFilename.isEmpty()) return null
        return OsuMetadata(audioFilename, bgFilename, title, artist, version)
    }
}
