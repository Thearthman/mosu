package com.mosu.app.data.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class MediaStoreFileService(private val context: Context) {
    private val resolver = context.contentResolver

    suspend fun copyAudioToMediaStore(
        source: File,
        beatmapSetId: Long,
        title: String,
        artist: String,
        difficultyName: String
    ): String = withContext(Dispatchers.IO) {
        if (!source.exists()) throw IOException("Audio file does not exist: ${source.absolutePath}")

        val extension = source.extension.ifBlank { "mp3" }.lowercase()
        val displayName = sanitizeFileName("${beatmapSetId}_${difficultyName}_${title}.$extension")
        val relativePath = "${Environment.DIRECTORY_MUSIC}/Mosu/"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeForExtension(extension, "audio/mpeg"))
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.ALBUM, "Mosu")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        copyToCollection(audioCollectionUri(), values, source, relativePath).toString()
    }

    suspend fun copyImageToMediaStore(
        source: File,
        beatmapSetId: Long,
        title: String,
        difficultyName: String
    ): String = withContext(Dispatchers.IO) {
        if (!source.exists()) throw IOException("Cover file does not exist: ${source.absolutePath}")

        val extension = source.extension.ifBlank { "jpg" }.lowercase()
        val displayName = sanitizeFileName("${beatmapSetId}_${difficultyName}_${title}_cover.$extension")
        val relativePath = "${Environment.DIRECTORY_PICTURES}/Mosu/Covers/"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeForExtension(extension, "image/jpeg"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        copyToCollection(imageCollectionUri(), values, source, relativePath).toString()
    }

    suspend fun deletePath(path: String) = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext
        if (path.startsWith("content://")) {
            runCatching { resolver.delete(Uri.parse(path), null, null) }
        } else {
            runCatching { File(path).delete() }
        }
    }

    suspend fun writeManifest(json: String) = withContext(Dispatchers.IO) {
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/Mosu/"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, MANIFEST_FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = downloadsCollectionUri()
        val existing = findExisting(collection, MANIFEST_FILE_NAME, relativePath)
        val uri = existing ?: resolver.insert(collection, values)
            ?: throw IOException("Unable to create MediaStore manifest")

        try {
            writeBytes(uri, json.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            if (existing != null) {
                val fallbackUri = resolver.insert(collection, values)
                    ?: throw IOException("Unable to create replacement MediaStore manifest", e)
                try {
                    writeBytes(fallbackUri, json.toByteArray(Charsets.UTF_8))
                } catch (fallbackError: Exception) {
                    runCatching { resolver.delete(fallbackUri, null, null) }
                    throw fallbackError
                }
            } else {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
        }
    }

    suspend fun readManifest(): String? = withContext(Dispatchers.IO) {
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/Mosu/"
        val uri = findLatestManifest(downloadsCollectionUri(), relativePath) ?: return@withContext null
        runCatching {
            resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        }.getOrNull()
    }

    fun canRead(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return if (path.startsWith("content://")) {
            runCatching {
                resolver.openFileDescriptor(Uri.parse(path), "r")?.use { true } ?: false
            }.getOrDefault(false)
        } else {
            File(path).exists()
        }
    }

    private fun copyToCollection(
        collection: Uri,
        values: ContentValues,
        source: File,
        relativePath: String
    ): Uri {
        val displayName = values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME)
        val existing = findExisting(collection, displayName, relativePath)
        val uri = existing ?: resolver.insert(collection, values)
            ?: throw IOException("Unable to create MediaStore item")

        try {
            writeFile(uri, source)
            return uri
        } catch (e: Exception) {
            if (existing != null) {
                val fallbackUri = resolver.insert(collection, values)
                    ?: throw IOException("Unable to create replacement MediaStore item", e)
                try {
                    writeFile(fallbackUri, source)
                    return fallbackUri
                } catch (fallbackError: Exception) {
                    runCatching { resolver.delete(fallbackUri, null, null) }
                    throw fallbackError
                }
            } else {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
        }
    }

    private fun writeFile(uri: Uri, source: File) {
        resolver.openOutputStream(uri, "wt")?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: throw IOException("Unable to open MediaStore output stream")
        markComplete(uri)
    }

    private fun writeBytes(uri: Uri, bytes: ByteArray) {
        resolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(bytes)
        } ?: throw IOException("Unable to open MediaStore output stream")
        markComplete(uri)
    }

    private fun markComplete(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val completed = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, completed, null, null)
        }
    }

    private fun findExisting(collection: Uri, displayName: String, relativePath: String): Uri? {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection: String
        val args: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
            args = arrayOf(displayName, relativePath)
        } else {
            selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            args = arrayOf(displayName)
        }

        return runCatching {
            resolver.query(
                collection,
                projection,
                selection,
                args,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    ContentUris.withAppendedId(collection, id)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun findLatestManifest(collection: Uri, relativePath: String): Uri? {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection: String
        val args: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
            args = arrayOf(MANIFEST_FILE_NAME, relativePath)
        } else {
            selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            args = arrayOf(MANIFEST_FILE_NAME)
        }

        val exact = queryFirst(collection, projection, selection, args)
        if (exact != null) return exact

        val fallbackSelection: String
        val fallbackArgs: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            fallbackSelection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
            fallbackArgs = arrayOf("mosu_manifest%.json", relativePath)
        } else {
            fallbackSelection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
            fallbackArgs = arrayOf("mosu_manifest%.json")
        }
        return queryFirst(collection, projection, fallbackSelection, fallbackArgs)
    }

    private fun queryFirst(
        collection: Uri,
        projection: Array<String>,
        selection: String,
        args: Array<String>
    ): Uri? {
        return runCatching {
            resolver.query(
                collection,
                projection,
                selection,
                args,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    ContentUris.withAppendedId(collection, id)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun audioCollectionUri(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
    }

    private fun imageCollectionUri(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
    }

    private fun downloadsCollectionUri(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }
    }

    private fun mimeTypeForExtension(extension: String, fallback: String): String {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: fallback
    }

    private fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(160)
            .ifBlank { "mosu_file" }
    }

    companion object {
        const val MANIFEST_FILE_NAME = "mosu_manifest.json"
    }
}
