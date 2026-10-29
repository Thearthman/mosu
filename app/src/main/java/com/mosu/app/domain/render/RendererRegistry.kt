package com.mosu.app.domain.render

import android.content.Context
import com.mosu.app.domain.render.osu.OsuStandardBeatmapRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RendererRegistry(renderers: List<BeatmapRenderer>) {
    private val renderersByMode: Map<RenderMode, BeatmapRenderer>

    init {
        require(renderers.map { it.mode }.distinct().size == renderers.size) {
            "Each renderer mode may only be registered once"
        }
        require(renderers.all { it.preferredAspectRatio.isFinite() && it.preferredAspectRatio > 0f }) {
            "Renderer preferred aspect ratios must be finite and greater than zero"
        }
        renderersByMode = renderers.associateBy { it.mode }
    }

    val supportedModes: Set<RenderMode> = renderersByMode.keys.toSet()

    fun rendererFor(mode: RenderMode): BeatmapRenderer? = renderersByMode[mode]

    /**
     * Renderer preparation failures degrade to the cover fallback, exactly like
     * a malformed or unsupported map file.
     */
    suspend fun prepare(
        context: Context,
        difficulty: RenderDifficulty
    ): PreparedRender? = withContext(Dispatchers.IO) {
        val renderer = rendererFor(difficulty.mode) ?: return@withContext null
        val prepared = try {
            renderer.prepare(context, difficulty)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return@withContext null
        validatePrepared(difficulty, prepared)
    }

    internal fun validatePrepared(
        difficulty: RenderDifficulty,
        prepared: PreparedRender
    ): PreparedRender? {
        if (prepared.mode == difficulty.mode) return prepared
        prepared.close()
        return null
    }

    companion object {
        fun default(): RendererRegistry = RendererRegistry(
            listOf(OsuStandardBeatmapRenderer())
        )
    }
}
