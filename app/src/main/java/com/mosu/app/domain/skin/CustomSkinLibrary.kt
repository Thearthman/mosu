package com.mosu.app.domain.skin

import com.mosu.app.domain.render.RenderSkinSelection
import com.mosu.app.domain.render.SkinIni
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipException

data class CustomSkinRecord(
    val id: String,
    val name: String,
    val author: String
)

data class CustomSkin(
    val id: String,
    val name: String,
    val author: String,
    val directory: File
) {
    fun selection(): RenderSkinSelection = RenderSkinSelection(
        id = id,
        name = name,
        directory = directory
    )
}

enum class CustomSkinImportError {
    INVALID_ARCHIVE,
    ARCHIVE_TOO_LARGE,
    ENTRY_TOO_LARGE,
    UNCOMPRESSED_TOO_LARGE,
    TOO_MANY_ENTRIES,
    UNSAFE_PATH,
    DUPLICATE_PATH,
    UNSUPPORTED_LAYOUT,
    EMPTY,
    IO
}

sealed interface CustomSkinImportResult {
    data class Imported(
        val skin: CustomSkin,
        val alreadyExisted: Boolean
    ) : CustomSkinImportResult

    data class Failed(val error: CustomSkinImportError) : CustomSkinImportResult
}

/** Persistence boundary implemented by the app's DataStore-backed settings. */
interface CustomSkinCatalogStore {
    suspend fun readCustomSkins(): List<CustomSkinRecord>
    suspend fun writeCustomSkins(skins: List<CustomSkinRecord>)
    suspend fun readActiveCustomSkinId(): String?
    suspend fun writeActiveCustomSkinId(id: String?)
}

/**
 * App-private importer and catalog for `.osk` archives.
 *
 * Archives are copied and hashed first, validated against zip-bomb/path limits,
 * extracted into a sibling staging directory, and only then moved into their
 * final `<sha256>` directory. The catalog is updated only after the atomic
 * move succeeds.
 */
class CustomSkinLibrary(
    private val rootDirectory: File,
    private val catalog: CustomSkinCatalogStore
) {
    private val importMutex = Mutex()

    suspend fun import(
        archiveName: String,
        openInput: () -> InputStream?
    ): CustomSkinImportResult = importMutex.withLock {
        withContext(Dispatchers.IO) {
            importLocked(archiveName, openInput)
        }
    }

    suspend fun select(skinId: String?) {
        catalog.writeActiveCustomSkinId(skinId)
    }

    suspend fun delete(skinId: String): Boolean = withContext(Dispatchers.IO) {
        val records = catalog.readCustomSkins()
        val record = records.firstOrNull { it.id == skinId } ?: return@withContext false
        val directory = directoryFor(record.id)
        val deleted = !directory.exists() || directory.deleteRecursively()
        if (deleted) {
            catalog.writeCustomSkins(records.filterNot { it.id == skinId })
            if (catalog.readActiveCustomSkinId() == skinId) {
                catalog.writeActiveCustomSkinId(null)
            }
        }
        deleted
    }

    fun directoryFor(id: String): File = File(rootDirectory, id)

    private suspend fun importLocked(
        archiveName: String,
        openInput: () -> InputStream?
    ): CustomSkinImportResult {
        rootDirectory.mkdirs()
        if (!rootDirectory.isDirectory) return CustomSkinImportResult.Failed(
            CustomSkinImportError.IO
        )

        val archive = File(
            rootDirectory,
            ".import-${UUID.randomUUID()}.osk"
        )
        val staging = File(
            rootDirectory,
            ".extract-${UUID.randomUUID()}"
        )
        try {
            val hash = copyAndHash(archive, openInput)
                ?: return CustomSkinImportResult.Failed(CustomSkinImportError.IO)

            val records = catalog.readCustomSkins()
            val existingRecord = records.firstOrNull { it.id == hash }
            val existingDirectory = directoryFor(hash)
            if (existingRecord != null && existingDirectory.isDirectory) {
                catalog.writeActiveCustomSkinId(hash)
                return CustomSkinImportResult.Imported(
                    skin = CustomSkin(
                        id = existingRecord.id,
                        name = existingRecord.name,
                        author = existingRecord.author,
                        directory = existingDirectory
                    ),
                    alreadyExisted = true
                )
            }

            CustomSkinArchive.extract(archive, staging)
            val metadata = readMetadata(staging, archiveName)
            val destination = directoryFor(hash)
            if (destination.exists()) {
                // A directory without a matching catalog record is not trusted:
                // it may be left over from an interrupted import or corrupt.
                if (!destination.deleteRecursively()) {
                    throw IOException("Unable to replace stale imported skin")
                }
            }
            if (!staging.renameTo(destination)) {
                throw IOException("Unable to publish imported skin")
            }

            val skin = CustomSkin(
                id = hash,
                name = metadata.first,
                author = metadata.second,
                directory = destination
            )
            val updatedRecords = listOf(
                CustomSkinRecord(skin.id, skin.name, skin.author)
            ) + records.filterNot { it.id == hash }
            catalog.writeCustomSkins(updatedRecords)
            catalog.writeActiveCustomSkinId(hash)
            return CustomSkinImportResult.Imported(skin, alreadyExisted = false)
        } catch (error: CustomSkinArchiveException) {
            return CustomSkinImportResult.Failed(error.error)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return CustomSkinImportResult.Failed(CustomSkinImportError.IO)
        } finally {
            archive.delete()
            staging.deleteRecursively()
        }
    }

    private fun copyAndHash(
        destination: File,
        openInput: () -> InputStream?
    ): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = try {
            openInput()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } ?: return null
        var total = 0L
        input.use { source ->
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    total += read
                    if (total > MAX_ARCHIVE_BYTES) {
                        throw CustomSkinArchiveException(CustomSkinImportError.ARCHIVE_TOO_LARGE)
                    }
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
        }
        if (total == 0L) {
            throw CustomSkinArchiveException(CustomSkinImportError.INVALID_ARCHIVE)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readMetadata(directory: File, archiveName: String): Pair<String, String> {
        val files = directory.walkTopDown()
            .filter(File::isFile)
            .sortedBy { it.relativeTo(directory).invariantSeparatorsPath.count { char -> char == '/' } }
            .associateBy { it.relativeTo(directory).invariantSeparatorsPath.lowercase() }
        val ini = (
            files["skin.ini"]
                ?: files.entries.firstOrNull { it.key.substringAfterLast('/') == "skin.ini" }?.value
            )?.let { file ->
            runCatching {
                SkinIni.parse(file.readText(Charsets.UTF_8))
            }.getOrNull()
        }
        val fileName = archiveName
            .replace('\\', '/')
            .substringAfterLast('/')
            .trim()
        val fallbackName = fileName
            .substringBeforeLast('.', fileName)
            .ifBlank { "Imported skin" }
            .take(80)
        return Pair(
            ini?.value("General", "Name")?.trim()?.takeIf { it.isNotEmpty() }
                ?: fallbackName,
            ini?.value("General", "Author")?.trim().orEmpty()
        )
    }

    private companion object {
        const val MAX_ARCHIVE_BYTES = 256L * 1024 * 1024
    }
}

internal class CustomSkinArchiveException(
    val error: CustomSkinImportError
) : IOException(error.name)

internal object CustomSkinArchive {
    private const val MAX_ENTRY_BYTES = 128L * 1024 * 1024
    private const val MAX_UNCOMPRESSED_BYTES = 512L * 1024 * 1024
    private const val MAX_ENTRIES = 10_000
    private val supportedExtensions = setOf("png", "wav", "mp3", "ogg")

    fun extract(archive: File, destination: File) {
        destination.mkdirs()
        if (!destination.isDirectory) {
            throw CustomSkinArchiveException(CustomSkinImportError.IO)
        }

        val zipFile = try {
            ZipFile(archive)
        } catch (_: ZipException) {
            throw CustomSkinArchiveException(CustomSkinImportError.INVALID_ARCHIVE)
        }
        zipFile.use { zip ->
            val plans = mutableListOf<EntryPlan>()
            var entryCount = 0
            var declaredTotal = 0L
            val seenPaths = mutableSetOf<String>()

            zip.entries().asSequence().forEach { entry ->
                entryCount++
                if (entryCount > MAX_ENTRIES) {
                    throw CustomSkinArchiveException(CustomSkinImportError.TOO_MANY_ENTRIES)
                }
                val normalized = normalizeEntryPath(entry.name)
                    ?: throw CustomSkinArchiveException(CustomSkinImportError.UNSAFE_PATH)
                if (entry.isDirectory) return@forEach
                if (!isSupported(normalized)) return@forEach

                if (entry.size > MAX_ENTRY_BYTES) {
                    throw CustomSkinArchiveException(CustomSkinImportError.ENTRY_TOO_LARGE)
                }
                if (entry.size > 0) declaredTotal += entry.size
                if (declaredTotal > MAX_UNCOMPRESSED_BYTES) {
                    throw CustomSkinArchiveException(
                        CustomSkinImportError.UNCOMPRESSED_TOO_LARGE
                    )
                }
                val pathKey = normalized.lowercase()
                if (!seenPaths.add(pathKey)) {
                    throw CustomSkinArchiveException(CustomSkinImportError.DUPLICATE_PATH)
                }
                plans += EntryPlan(entry, normalized)
            }

            if (plans.isEmpty()) {
                throw CustomSkinArchiveException(CustomSkinImportError.EMPTY)
            }

            val stripPrefix = wrapperDirectoryToStrip(plans.map { it.normalizedPath })
            var extractedTotal = 0L
            for (plan in plans) {
                val relativePath = if (stripPrefix != null) {
                    plan.normalizedPath.substringAfter('/')
                } else {
                    plan.normalizedPath
                }.lowercase(Locale.ROOT)
                if (relativePath.isBlank()) continue
                val output = File(destination, relativePath).canonicalFile
                val destinationRoot = destination.canonicalFile
                if (
                    output != destinationRoot &&
                    !output.path.startsWith(destinationRoot.path + File.separator)
                ) {
                    throw CustomSkinArchiveException(CustomSkinImportError.UNSAFE_PATH)
                }
                output.parentFile?.mkdirs()
                zip.getInputStream(plan.entry).use { input ->
                    output.outputStream().buffered().use { stream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var entryBytes = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            entryBytes += read
                            extractedTotal += read
                            if (entryBytes > MAX_ENTRY_BYTES) {
                                throw CustomSkinArchiveException(
                                    CustomSkinImportError.ENTRY_TOO_LARGE
                                )
                            }
                            if (extractedTotal > MAX_UNCOMPRESSED_BYTES) {
                                throw CustomSkinArchiveException(
                                    CustomSkinImportError.UNCOMPRESSED_TOO_LARGE
                                )
                            }
                            stream.write(buffer, 0, read)
                        }
                    }
                }
            }
        }
    }

    private fun normalizeEntryPath(raw: String): String? {
        if (raw.indexOf('\u0000') >= 0) return null
        val path = raw.replace('\\', '/')
        if (
            path.startsWith('/') ||
            Regex("^[A-Za-z]:").containsMatchIn(path)
        ) {
            return null
        }
        val parts = path.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." }) return null
        return parts.joinToString("/")
    }

    private fun isSupported(path: String): Boolean {
        val name = path.substringAfterLast('/').lowercase()
        if (name == "skin.ini") return true
        val extension = name.substringAfterLast('.', "")
        return extension in supportedExtensions
    }

    /**
     * Legacy skins are normally flat, while downloaded archives sometimes wrap
     * every entry in one directory. Mixed and multi-folder archives are also
     * accepted: [SkinFileIndex] and the hitsound resolver fall back to file
     * basenames, so rejecting a valid archive over packaging layout is worse
     * than preserving its safe relative paths.
     */
    private fun wrapperDirectoryToStrip(paths: List<String>): String? {
        val topLevels = paths.map { it.substringBefore('/') }.distinct()
        val hasRootFile = paths.any { !it.contains('/') }
        return when {
            hasRootFile -> null
            topLevels.size == 1 -> topLevels.single()
            else -> null
        }
    }

    private data class EntryPlan(
        val entry: java.util.zip.ZipEntry,
        val normalizedPath: String
    )
}
