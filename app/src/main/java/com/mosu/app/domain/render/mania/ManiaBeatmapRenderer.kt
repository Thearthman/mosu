package com.mosu.app.domain.render.mania

import android.content.Context
import android.graphics.Canvas
import com.mosu.app.domain.render.BeatmapRenderer
import com.mosu.app.domain.render.OsuFileHeaderReader
import com.mosu.app.domain.render.PreparedRender
import com.mosu.app.domain.render.RenderDifficulty
import com.mosu.app.domain.render.RenderFrame
import com.mosu.app.domain.render.RenderMode
import com.mosu.app.domain.render.RenderSkinSelection
import java.io.File

/**
 * osu!mania preview adapter. Parsing, timeline compilation and skin decoding
 * happen in [prepare]; [PreparedRender.draw] only walks immutable data.
 */
class ManiaBeatmapRenderer : BeatmapRenderer {
    override val mode: RenderMode = RenderMode.MANIA
    override val preferredAspectRatio: Float =
        ManiaRenderTimeline.MANIA_PLAYFIELD_WIDTH /
            ManiaRenderTimeline.MANIA_PLAYFIELD_HEIGHT

    override suspend fun prepare(
        context: Context,
        difficulty: RenderDifficulty,
        skinSelection: RenderSkinSelection
    ): PreparedRender? {
        if (difficulty.mode != mode) return null

        val timeline = ManiaRenderCache.timeline(
            file = File(difficulty.path),
            expectedBeatmapSetId = difficulty.beatmapSetId,
            expectedDifficultyName = difficulty.difficultyName,
            expectedAudioFilename = difficulty.audioFilename
        ) ?: return null
        if (
            timeline.beatmapSetId != null &&
            timeline.beatmapSetId != difficulty.beatmapSetId
        ) {
            return null
        }
        if (
            difficulty.beatmapId != null &&
            timeline.beatmapId != null &&
            timeline.beatmapId != difficulty.beatmapId
        ) {
            return null
        }
        if (
            timeline.difficultyName != difficulty.difficultyName ||
            timeline.audioFilename !=
            OsuFileHeaderReader.normalizeAudioFilename(difficulty.audioFilename)
        ) {
            return null
        }

        val skin = runCatching {
            ManiaSkinLoader.load(
                keyCount = timeline.keyCount,
                selection = skinSelection
            )
        }.getOrNull() ?: return null
        return PreparedManiaRender(
            timeline = timeline,
            renderer = ManiaRenderer(skin)
        )
    }
}

private class PreparedManiaRender(
    private val timeline: ManiaRenderTimeline,
    private val renderer: ManiaRenderer
) : PreparedRender {
    override val mode: RenderMode = RenderMode.MANIA

    override fun draw(canvas: Canvas, frame: RenderFrame) {
        renderer.draw(
            canvas = canvas,
            timeline = timeline,
            mapTimeMs = frame.mapTimeMs,
            surface = frame.surface
        )
    }

    override fun close() {
        renderer.close()
    }
}
