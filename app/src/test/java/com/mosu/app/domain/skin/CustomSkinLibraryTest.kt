package com.mosu.app.domain.skin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CustomSkinLibraryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun malformedArchiveIsReportedAsInvalid() = runBlocking {
        val library = library()

        val result = library.import("broken.osk") {
            ByteArrayInputStream("not a zip".toByteArray())
        }

        assertEquals(
            CustomSkinImportResult.Failed(CustomSkinImportError.INVALID_ARCHIVE),
            result
        )
    }

    @Test
    fun emptyArchiveIsReportedAsEmpty() = runBlocking {
        val result = library().import("empty.osk") {
            ByteArrayInputStream(zip())
        }

        assertEquals(CustomSkinImportResult.Failed(CustomSkinImportError.EMPTY), result)
    }

    @Test
    fun archiveWithOnlyUnsupportedFilesIsReportedAsEmpty() = runBlocking {
        val archive = zip("readme.txt" to "hello".toByteArray())

        val result = library().import("unsupported.osk") {
            ByteArrayInputStream(archive)
        }

        assertEquals(CustomSkinImportResult.Failed(CustomSkinImportError.EMPTY), result)
    }

    @Test
    fun traversalPathIsRejected() = runBlocking {
        val archive = zip(
            "skin.ini" to "[General]\nName: Test".toByteArray(),
            "../outside.png" to byteArrayOf(1)
        )

        val result = library().import("traversal.osk") {
            ByteArrayInputStream(archive)
        }

        assertEquals(CustomSkinImportResult.Failed(CustomSkinImportError.UNSAFE_PATH), result)
    }

    @Test
    fun absolutePathIsRejected() = runBlocking {
        val archive = zip(
            "skin.ini" to "[General]\nName: Test".toByteArray(),
            "/outside.png" to byteArrayOf(1)
        )

        val result = library().import("absolute.osk") {
            ByteArrayInputStream(archive)
        }

        assertEquals(CustomSkinImportResult.Failed(CustomSkinImportError.UNSAFE_PATH), result)
    }

    @Test
    fun duplicatePathsDifferingOnlyByCaseAreRejected() = runBlocking {
        val archive = zip(
            "skin.ini" to "[General]\nName: Test".toByteArray(),
            "HitCircle.png" to byteArrayOf(1),
            "hitcircle.png" to byteArrayOf(2)
        )

        val result = library().import("duplicate.osk") {
            ByteArrayInputStream(archive)
        }

        assertEquals(
            CustomSkinImportResult.Failed(CustomSkinImportError.DUPLICATE_PATH),
            result
        )
    }

    @Test
    fun rootLayoutPreservesFilesAndReadsMetadata() = runBlocking {
        val archive = zip(
            "skin.ini" to "[General]\nName: Root Skin\nAuthor: Alice".toByteArray(),
            "hitcircle.png" to byteArrayOf(1, 2, 3)
        )
        val library = library()

        val result = importBytes(library, "root.osk", archive)

        assertTrue(result is CustomSkinImportResult.Imported)
        val imported = result as CustomSkinImportResult.Imported
        assertEquals("Root Skin", imported.skin.name)
        assertEquals("Alice", imported.skin.author)
        assertTrue(File(imported.skin.directory, "skin.ini").isFile)
        assertTrue(File(imported.skin.directory, "hitcircle.png").isFile)
    }

    @Test
    fun singleWrapperDirectoryIsStripped() = runBlocking {
        val archive = zip(
            "Wrapped/skin.ini" to "[General]\nName: Wrapped Skin".toByteArray(),
            "Wrapped/HitCircle.PNG" to byteArrayOf(1)
        )
        val library = library()

        val result = importBytes(library, "wrapped.osk", archive)

        val imported = result as CustomSkinImportResult.Imported
        assertTrue(File(imported.skin.directory, "skin.ini").isFile)
        assertTrue(File(imported.skin.directory, "hitcircle.png").isFile)
    }

    @Test
    fun mixedAndNestedLayoutsArePreservedSafely() = runBlocking {
        val archive = zip(
            "skin.ini" to "[General]\nName: Nested Skin".toByteArray(),
            "Graphics/HITCIRCLE.PNG" to byteArrayOf(1),
            "Sounds/soft-hitnormal.wav" to byteArrayOf(2)
        )
        val library = library()

        val result = importBytes(library, "nested.osk", archive)

        val imported = result as CustomSkinImportResult.Imported
        assertTrue(File(imported.skin.directory, "graphics/hitcircle.png").isFile)
        assertTrue(File(imported.skin.directory, "sounds/soft-hitnormal.wav").isFile)
    }

    @Test
    fun reimportingSameArchiveSelectsExistingSkinWithoutDuplicatingIt() = runBlocking {
        val archive = zip(
            "skin.ini" to "[General]\nName: Deduplicated".toByteArray(),
            "hitcircle.png" to byteArrayOf(1)
        )
        val store = FakeCustomSkinCatalogStore()
        val library = library(store)

        val first = importBytes(library, "first.osk", archive) as CustomSkinImportResult.Imported
        val second = importBytes(library, "renamed.osk", archive) as CustomSkinImportResult.Imported

        assertFalse(first.alreadyExisted)
        assertTrue(second.alreadyExisted)
        assertEquals(first.skin.id, second.skin.id)
        assertEquals(sha256(archive), first.skin.id)
        assertEquals(1, store.skins.size)
        assertEquals(first.skin.id, store.activeId)
    }

    @Test
    fun untrackedDestinationDirectoryIsReplacedByTheValidatedArchive() = runBlocking {
        val archive = zip(
            "skin.ini" to "[General]\nName: Clean Import".toByteArray(),
            "hitcircle.png" to byteArrayOf(1)
        )
        val root = temporaryFolder.newFolder("stale-skins")
        val staleDirectory = File(root, sha256(archive)).apply { mkdirs() }
        File(staleDirectory, "stale.txt").writeText("old data")
        val store = FakeCustomSkinCatalogStore()
        val library = CustomSkinLibrary(root, store)

        val imported = importBytes(
            library,
            "clean.osk",
            archive
        ) as CustomSkinImportResult.Imported

        assertFalse(File(imported.skin.directory, "stale.txt").exists())
        assertTrue(File(imported.skin.directory, "hitcircle.png").isFile)
        assertEquals(1, store.skins.size)
    }

    @Test
    fun deletingSkinRemovesFilesCatalogEntryAndActiveSelection() = runBlocking {
        val archive = zip(
            "skin.ini" to "[General]\nName: Deletable".toByteArray(),
            "hitcircle.png" to byteArrayOf(1)
        )
        val store = FakeCustomSkinCatalogStore()
        val library = library(store)
        val imported = importBytes(
            library,
            "deletable.osk",
            archive
        ) as CustomSkinImportResult.Imported

        val deleted = library.delete(imported.skin.id)

        assertTrue(deleted)
        assertFalse(imported.skin.directory.exists())
        assertTrue(store.skins.isEmpty())
        assertNull(store.activeId)
    }

    @Test
    fun archiveNameWithoutAnExtensionProvidesFallbackMetadata() = runBlocking {
        val archive = zip("hitcircle.png" to byteArrayOf(1))

        val imported = importBytes(
            library(),
            "/downloads/Community Skin",
            archive
        ) as CustomSkinImportResult.Imported

        assertEquals("Community Skin", imported.skin.name)
    }

    @Test
    fun cancellationFromInputIsNotConvertedToIoFailure() {
        val library = library()
        val error = runCatching {
            runBlocking {
                library.import("cancelled.osk") {
                    object : InputStream() {
                        override fun read(): Int =
                            throw CancellationException("cancelled")
                    }
                }
            }
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is CancellationException)
    }

    private fun library(
        store: FakeCustomSkinCatalogStore = FakeCustomSkinCatalogStore()
    ): CustomSkinLibrary = CustomSkinLibrary(
        rootDirectory = temporaryFolder.newFolder("skins"),
        catalog = store
    )

    private suspend fun importBytes(
        library: CustomSkinLibrary,
        name: String,
        archive: ByteArray
    ): CustomSkinImportResult = library.import(name) {
        ByteArrayInputStream(archive)
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private class FakeCustomSkinCatalogStore : CustomSkinCatalogStore {
        var skins: List<CustomSkinRecord> = emptyList()
        var activeId: String? = null

        override suspend fun readCustomSkins(): List<CustomSkinRecord> = skins

        override suspend fun writeCustomSkins(skins: List<CustomSkinRecord>) {
            this.skins = skins
        }

        override suspend fun readActiveCustomSkinId(): String? = activeId

        override suspend fun writeActiveCustomSkinId(id: String?) {
            activeId = id
        }
    }
}
