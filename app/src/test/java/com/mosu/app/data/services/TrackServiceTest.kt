package com.mosu.app.data.services

import com.mosu.app.data.db.BeatmapEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackServiceTest {
    @Test
    fun `single remaining album track is converted to a song`() {
        val remainingTrack = track(uid = 7, isAlbum = true)

        assertEquals(
            remainingTrack,
            remainingAlbumTrackToConvert(listOf(remainingTrack))
        )
    }

    @Test
    fun `single remaining standalone track does not need conversion`() {
        assertNull(remainingAlbumTrackToConvert(listOf(track(uid = 7, isAlbum = false))))
    }

    @Test
    fun `album with multiple remaining tracks stays an album`() {
        assertNull(
            remainingAlbumTrackToConvert(
                listOf(
                    track(uid = 7, isAlbum = true),
                    track(uid = 8, isAlbum = true)
                )
            )
        )
    }

    @Test
    fun `deleted set has no track to convert`() {
        assertNull(remainingAlbumTrackToConvert(emptyList()))
    }

    private fun track(uid: Long, isAlbum: Boolean) = BeatmapEntity(
        uid = uid,
        beatmapSetId = 42,
        title = "Title",
        artist = "Artist",
        creator = "Creator",
        difficultyName = "Difficulty $uid",
        audioPath = "audio-$uid.mp3",
        coverPath = "cover-$uid.jpg",
        isAlbum = isAlbum
    )
}
