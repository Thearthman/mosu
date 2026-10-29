package com.mosu.app.domain.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RendererRegistryTest {
    @Test
    fun defaultRegistryRegistersOnlyTheStandardRenderer() {
        assertEquals(setOf(RenderMode.OSU), RendererRegistry.default().supportedModes)
    }

    @Test
    fun rejectsDuplicateModeRegistration() {
        assertThrows(IllegalArgumentException::class.java) {
            RendererRegistry(
                listOf(
                    fakeRenderer(RenderMode.OSU),
                    fakeRenderer(RenderMode.OSU)
                )
            )
        }
    }

    @Test
    fun exposesOnlyRegisteredModes() {
        val registry = RendererRegistry(
            listOf(fakeRenderer(RenderMode.OSU), fakeRenderer(RenderMode.TAIKO))
        )

        assertEquals(setOf(RenderMode.OSU, RenderMode.TAIKO), registry.supportedModes)
        assertNull(registry.rendererFor(RenderMode.CATCH))
    }

    @Test
    fun rejectsInvalidPreferredAspectRatios() {
        assertThrows(IllegalArgumentException::class.java) {
            RendererRegistry(listOf(fakeRenderer(RenderMode.OSU, aspectRatio = 0f)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RendererRegistry(listOf(fakeRenderer(RenderMode.OSU, aspectRatio = Float.NaN)))
        }
    }

    @Test
    fun rejectsAndClosesPreparedRenderersWithTheWrongMode() {
        val prepared = FakePrepared(RenderMode.TAIKO)
        val difficulty = RenderDifficulty(
            key = RenderDifficultyKey(RenderMode.OSU, "Hard"),
            path = "hard.osu",
            beatmapSetId = 42,
            audioFilename = "audio.mp3"
        )
        val registry = RendererRegistry(listOf(fakeRenderer(RenderMode.OSU)))

        assertNull(registry.validatePrepared(difficulty, prepared))
        assertEquals(1, prepared.closeCount)
    }

    @Test
    fun acceptsPreparedRenderersWithTheMatchingMode() {
        val prepared = FakePrepared(RenderMode.OSU)
        val difficulty = RenderDifficulty(
            key = RenderDifficultyKey(RenderMode.OSU, "Hard"),
            path = "hard.osu",
            beatmapSetId = 42,
            audioFilename = "audio.mp3"
        )
        val registry = RendererRegistry(listOf(fakeRenderer(RenderMode.OSU)))

        assertEquals(prepared, registry.validatePrepared(difficulty, prepared))
        assertEquals(0, prepared.closeCount)
    }

    private fun fakeRenderer(
        mode: RenderMode,
        aspectRatio: Float = 1f
    ) = object : BeatmapRenderer {
        override val mode = mode
        override val preferredAspectRatio = aspectRatio

        override suspend fun prepare(
            context: android.content.Context,
            difficulty: RenderDifficulty
        ): PreparedRender = FakePrepared(mode)
    }

    private class FakePrepared(override val mode: RenderMode) : PreparedRender {
        var closeCount = 0

        override fun draw(canvas: android.graphics.Canvas, frame: RenderFrame) = Unit

        override fun close() {
            closeCount++
        }
    }
}
