package com.mosu.app.domain.render.osu

import android.content.Context
import android.graphics.Canvas
import com.mosu.app.domain.render.BeatmapRenderer
import com.mosu.app.domain.render.OsuPlayfieldSize
import com.mosu.app.domain.render.OsuRenderCache
import com.mosu.app.domain.render.OsuRenderTimeline
import com.mosu.app.domain.render.OsuRenderer
import com.mosu.app.domain.render.OsuHitsoundScheduler
import com.mosu.app.domain.render.OsuSkinLoader
import com.mosu.app.domain.render.PreparedRender
import com.mosu.app.domain.render.RenderAudioFrame
import com.mosu.app.domain.render.RenderAudioSink
import com.mosu.app.domain.render.RenderDifficulty
import com.mosu.app.domain.render.RenderFrame
import com.mosu.app.domain.render.RenderMode
import com.mosu.app.domain.render.RenderSkinSelection
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
        difficulty: RenderDifficulty,
        skinSelection: RenderSkinSelection
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

        val skin = runCatching {
            OsuSkinLoader.load(context, skinSelection)
        }.getOrNull()
            ?: return null
        return PreparedOsuStandardRender(
            timeline = timeline,
            renderer = OsuRenderer(skin),
            layeredHitSounds = skin.layeredHitSounds
        )
    }
}

private class PreparedOsuStandardRender(
    private val timeline: OsuRenderTimeline,
    private val renderer: OsuRenderer,
    layeredHitSounds: Boolean
) : PreparedRender {
    override val mode: RenderMode = RenderMode.OSU
    private val hitsoundScheduler = OsuHitsoundScheduler(timeline, layeredHitSounds)

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

    override fun preloadAudio(sink: RenderAudioSink) {
        sink.preloadSamples(
            timeline.hitsoundCues
                .flatMap { it.samples }
                .plus(timeline.hitsoundLoops.map { it.sample })
        )
    }

    override fun updateAudio(frame: RenderAudioFrame, sink: RenderAudioSink) {
        hitsoundScheduler.update(frame, sink)
    }

    override fun close() {
        hitsoundScheduler.close()
        renderer.close()
    }
}
