package com.mosu.app.domain.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistFileFormatTest {
    @Test
    fun encodeThenDecodeRoundTripsNameAndIds() {
        val content = PlaylistFileFormat.encode("My Playlist", listOf(123L, 456L, 789L))
        val payload = PlaylistFileFormat.decode(content)

        assertEquals("My Playlist", payload?.name)
        assertEquals(listOf(123L, 456L, 789L), payload?.setIds)
    }

    @Test
    fun encodeProducesJson() {
        val content = PlaylistFileFormat.encode("Set", listOf(42L))

        assertEquals("""{"version":1,"name":"Set","beatmapSetIds":[42]}""", content)
    }

    @Test
    fun encodeOmitsNameWhenBlank() {
        val content = PlaylistFileFormat.encode("   ", listOf(42L))
        val payload = PlaylistFileFormat.decode(content)

        assertEquals(listOf(42L), payload?.setIds)
        assertNull(payload?.name)
    }

    @Test
    fun decodeBareJsonArray() {
        val payload = PlaylistFileFormat.decode("[111, 222, 333]")

        assertEquals(listOf(111L, 222L, 333L), payload?.setIds)
        assertNull(payload?.name)
    }

    @Test
    fun decodeDeduplicatesPreservingOrder() {
        val payload = PlaylistFileFormat.decode("""{"beatmapSetIds":[9,5,9,5,1]}""")

        assertEquals(listOf(9L, 5L, 1L), payload?.setIds)
    }

    @Test
    fun decodeAllowsStringEncodedIds() {
        val payload = PlaylistFileFormat.decode("""{"beatmapSetIds":["10","20"]}""")

        assertEquals(listOf(10L, 20L), payload?.setIds)
    }

    @Test
    fun decodeFallsBackToPlainTextList() {
        val payload = PlaylistFileFormat.decode("111\n222\n\n# comment\n333\n")

        assertEquals(listOf(111L, 222L, 333L), payload?.setIds)
        assertNull(payload?.name)
    }

    @Test
    fun decodeFallsBackToCommaSeparatedText() {
        val payload = PlaylistFileFormat.decode("1, 2, 3")

        assertEquals(listOf(1L, 2L, 3L), payload?.setIds)
    }

    @Test
    fun decodeIgnoresNonPositiveIds() {
        val payload = PlaylistFileFormat.decode("""{"beatmapSetIds":[0,-5,77]}""")

        // "0" is dropped; "-5" is not a valid positive id.
        assertEquals(listOf(77L), payload?.setIds)
    }

    @Test
    fun decodeReturnsNullForBlankContent() {
        assertNull(PlaylistFileFormat.decode(""))
        assertNull(PlaylistFileFormat.decode("   \n  \n"))
    }

    @Test
    fun decodeStripsBomAndReadsName() {
        val payload = PlaylistFileFormat.decode("\uFEFF{\"name\":\"Breakcore\",\"beatmapSetIds\":[10,20]}")

        assertEquals("Breakcore", payload?.name)
        assertEquals(listOf(10L, 20L), payload?.setIds)
    }

    @Test
    fun suggestedFileNameSanitizesUnsafeCharacters() {
        assertEquals(
            "a_b_c_d.json",
            PlaylistTransferService.suggestedFileName("a/b:c*d")
        )
        assertEquals(
            "playlist.json",
            PlaylistTransferService.suggestedFileName("   ")
        )
    }
}
