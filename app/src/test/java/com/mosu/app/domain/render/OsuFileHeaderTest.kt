package com.mosu.app.domain.render

import org.junit.Assert.assertEquals
import org.junit.Test

class OsuFileHeaderTest {
    @Test
    fun sliderMultiplierIsClampedToOfficialRange() {
        assertEquals(3.6f, parseSliderMultiplier("10"), 0.0001f)
        assertEquals(0.4f, parseSliderMultiplier("0.1"), 0.0001f)
    }

    @Test
    fun nonFiniteSliderMultiplierFallsBackToDefault() {
        assertEquals(1.4f, parseSliderMultiplier("NaN"), 0.0001f)
        assertEquals(1.4f, parseSliderMultiplier("Infinity"), 0.0001f)
        assertEquals(1.4f, parseSliderMultiplier("-Infinity"), 0.0001f)
    }

    private fun parseSliderMultiplier(value: String): Float =
        OsuFileHeaderReader.parse(
            """
            osu file format v14
            [General]
            AudioFilename: audio.mp3
            Mode: 0
            [Metadata]
            Title:Test Title
            Artist:Test Artist
            Creator:Test Creator
            Version:Hard
            [Difficulty]
            SliderMultiplier:$value
            """.trimIndent()
        ).sliderMultiplier
}
