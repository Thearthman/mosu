package com.mosu.app.domain.model

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatmapExtensionsTest {
    private val anchors = listOf(
        0.0f to Color(0xFF4FC0FF),
        1.5f to Color(0xFF4FFFD5),
        2.0f to Color(0xFF7CFF4F),
        2.5f to Color(0xFFF6F05C),
        3.25f to Color(0xFFFF8068),
        4.5f to Color(0xFFFF3C71),
        6.0f to Color(0xFF6563DE),
        7.0f to Color(0xFF18158E),
        8.0f to Color(0xFF000000)
    )

    @Test
    fun starRatingColorReturnsAnchorColorsAtAnchorRatings() {
        anchors.forEach { (stars, color) ->
            assertEquals(color, getStarRatingColor(stars))
        }
    }

    @Test
    fun starRatingColorInterpolatesBetweenAnchors() {
        val color = getStarRatingColor(0.75f)

        assertNotEquals(anchors[0].second, color)
        assertNotEquals(anchors[1].second, color)
        assertTrue(color.green > anchors[0].second.green)
        assertTrue(color.green < anchors[1].second.green)
        assertTrue(color.blue < anchors[0].second.blue)
        assertTrue(color.blue > anchors[1].second.blue)
    }

    @Test
    fun starRatingColorClampsAtTheEndsOfTheScale() {
        assertEquals(anchors.first().second, getStarRatingColor(-1f))
        assertEquals(anchors.last().second, getStarRatingColor(10f))
        assertEquals(anchors.last().second, getStarRatingColor(Float.NaN))
    }
}
