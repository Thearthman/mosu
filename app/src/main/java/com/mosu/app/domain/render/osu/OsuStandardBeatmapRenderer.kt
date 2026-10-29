package com.mosu.app.domain.render.osu

import android.content.Context
import android.graphics.Canvas
import com.mosu.app.domain.render.BeatmapRenderer
import com.mosu.app.domain.render.OsuPlayfieldSize
import com.mosu.app.domain.render.OsuRenderCache
import com.mosu.app.domain.render.OsuRenderTimeline
import com.mosu.app.domain.render.OsuRenderer
import com.mosu.app.domain.render.OsuSkinLoader
import com.mosu.app.domain.render.PreparedRender
import com.mosu.app.domain.render.RenderDifficulty
import com.mosu.app.domain.render.RenderFrame
import com.mosu.app.domain.render.RenderMode
import java.io.File

/**
 * osu!standard adapter. Parsing, timeline compilation and skin loading all
 * happen in [prepare]; [draw] only walks the precomputed timeline.
 */
class OsuStandardBeatmapRenderer : BeatmapRenderer {
    override val mode: RenderMode = RenderMode.OSU
    override val preferredAspectRatio: Float = 7f / 6f

    override suspend fun prepare(
        context: Context,
        difficulty: RenderDifficulty
    ): PreparedRender? {
        if (difficulty.mode != mode) return null

        val timeline = OsuRenderCache.timeline(
            file = File(difficulty.path),
            expectedBeatmapSetId = difficulty.beatmapSetId
        ) ?: return null
        if (
            timeline.beatmapSetId != difficulty.beatmapSetId ||
            timeline.audioFilename != difficulty.audioFilename
        ) {
            return null
        }

        val skin = runCatching { OsuSkinLoader.load(context) }.getOrNull()
            ?: return null
        return PreparedOsuStandardRender(timeline, OsuRenderer(skin))
    }
}

private class PreparedOsuStandardRender(
    private val timeline: OsuRenderTimeline,
    private val renderer: OsuRenderer
) : PreparedRender {
    override val mode: RenderMode = RenderMode.OSU

    override fun draw(canvas: Canvas, frame: RenderFrame) {
        renderer.draw(
            canvas = canvas,
            timeline = timeline,
            mapTimeMs = frame.mapTimeMs,
            playfieldSize = OsuPlayfieldSize(
                widthPx = frame.surface.widthPx,
                heightPx = frame.surface.heightPx
            ),
            replayInput = frame.replayInput,
            edgeInsetPx = frame.surface.edgeInsetPx,
            borderWidthPx = frame.surface.borderWidthPx
        )
    }
}
