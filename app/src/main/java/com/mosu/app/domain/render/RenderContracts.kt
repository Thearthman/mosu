package com.mosu.app.domain.render

import android.content.Context
import android.graphics.Canvas
import java.io.File

enum class RenderMode(val osuMode: Int, val displayName: String) {
    OSU(0, "osu!"),
    TAIKO(1, "taiko"),
    CATCH(2, "catch"),
    MANIA(3, "mania");

    companion object {
        fun fromOsuMode(mode: Int): RenderMode? = entries.firstOrNull { it.osuMode == mode }
    }
}

data class RenderDifficultyKey(
    val mode: RenderMode,
    val difficultyName: String
) {
    fun encode(): String = "$ENCODING_PREFIX${mode.osuMode}:$difficultyName"

    companion object {
        private const val ENCODING_PREFIX = "v1:"

        fun decode(value: String): RenderDifficultyKey? {
            if (!value.startsWith(ENCODING_PREFIX)) return null
            val payload = value.removePrefix(ENCODING_PREFIX)
            val separator = payload.indexOf(':')
            if (separator <= 0) return null
            val mode = payload.substring(0, separator).toIntOrNull()
                ?.let(RenderMode::fromOsuMode)
                ?: return null
            val difficultyName = payload.substring(separator + 1)
            if (difficultyName.isBlank()) return null
            return RenderDifficultyKey(mode, difficultyName)
        }
    }
}

data class RenderDifficulty(
    val key: RenderDifficultyKey,
    val path: String,
    val beatmapSetId: Long,
    val audioFilename: String,
    val beatmapId: Long? = null
) {
    val mode: RenderMode get() = key.mode
    val difficultyName: String get() = key.difficultyName
}

/**
 * The visual/hitsound skin selected for one renderer instance.
 *
 * Bundled resources are always the fallback. [directory] points at an
 * app-private, already validated imported skin root.
 */
data class RenderSkinSelection(
    val id: String? = null,
    val name: String? = null,
    val directory: File? = null
) {
    val isBundled: Boolean get() = directory == null

    companion object {
        val Bundled = RenderSkinSelection()
    }
}

data class RenderCatalog(
    val beatmapSetId: Long,
    val audioFilename: String,
    val difficulties: List<RenderDifficulty>,
    val defaultDifficultyKey: RenderDifficultyKey
) {
    /**
     * Stable preference key for the playing song. The set id separates songs,
     * while the normalized audio filename distinguishes multiple-audio albums.
     */
    val selectionKey: String
        get() = "$beatmapSetId|$audioFilename"

    fun difficulty(key: RenderDifficultyKey): RenderDifficulty? =
        difficulties.firstOrNull { it.key == key }

    /**
     * Resolves the versioned mode/name format first. Values persisted by older
     * builds contain only a difficulty name, so they remain valid as long as
     * that name identifies exactly one renderable difficulty.
     */
    fun resolveSavedDifficulty(savedValue: String?): RenderDifficultyKey {
        val value = savedValue?.takeIf { it.isNotBlank() }
        if (value != null) {
            RenderDifficultyKey.decode(value)
                ?.let(::difficulty)
                ?.let { return it.key }

            val legacyMatches = difficulties.filter { it.difficultyName == value }
            if (legacyMatches.size == 1) return legacyMatches.single().key
        }
        return defaultDifficultyKey
    }
}

data class RenderSurface(
    val widthPx: Float,
    val heightPx: Float,
    val edgeInsetPx: Float = 0f,
    val borderWidthPx: Float = 0f
)

data class RenderFrame(
    val mapTimeMs: Double,
    val surface: RenderSurface,
    val replayInput: ReplayInput? = null
)

enum class RenderSampleBank {
    NORMAL,
    SOFT,
    DRUM
}

/**
 * A ruleset-neutral request for one hit sample.
 *
 * [customFilename] and [customSampleIndex] are retained even while custom
 * beatmap samples are unsupported, so extracting and playing them later does
 * not require changing renderer contracts.
 */
data class RenderSample(
    val sampleName: String,
    val bank: RenderSampleBank,
    val customSampleIndex: Int = 0,
    val customFilename: String? = null,
    val volume: Int = 100
)

/**
 * Playback state passed to a prepared renderer's audio scheduler.
 *
 * Cues are early by [latencyMs]: renderers should compare
 * `mapTimeMs + latencyMs` against their timeline's cue times. [clockEpoch]
 * changes on discontinuities such as a seek, media transition or restart.
 */
data class RenderAudioFrame(
    val mapTimeMs: Double,
    val clockEpoch: Long,
    val isPlaying: Boolean,
    val latencyMs: Int = 0
)

/**
 * Passive audio output. Renderers decide when a cue is due; the sink only
 * starts, updates and stops native channels.
 *
 * [preloadSamples] may perform asset or native I/O and must be called before
 * the first audible cue. [playSample] and [startLoop] are called from the
 * renderer frame loop and must only use already-preloaded resources.
 */
interface RenderAudioSink {
    fun preloadSamples(samples: Iterable<RenderSample>) = Unit
    fun playSample(sample: RenderSample)
    fun startLoop(key: String, sample: RenderSample)
    fun stopLoop(key: String)
    fun stopAll()
}

data class ReplayPosition(val x: Float, val y: Float)

data class ReplayFrame(
    val timeMs: Long,
    val position: ReplayPosition,
    val buttons: Int
)

/** Extension point only; replay rendering is not part of the current renderer. */
fun interface ReplayInput {
    fun sampleAt(timeMs: Long): ReplayFrame?
}

/**
 * A renderer owns one osu! ruleset. [prepare] may perform file or asset I/O,
 * while [PreparedRender.draw] must remain safe to call from the frame loop.
 */
interface BeatmapRenderer {
    val mode: RenderMode

    /** Width divided by height for the renderer's preferred preview surface. */
    val preferredAspectRatio: Float

    suspend fun prepare(
        context: Context,
        difficulty: RenderDifficulty,
        skinSelection: RenderSkinSelection = RenderSkinSelection.Bundled
    ): PreparedRender?
}

/**
 * A renderer instance that has finished its expensive preparation.
 *
 * The player session serializes lifecycle calls: [preloadAudio] runs off the
 * frame thread, [updateAudio] runs on the frame thread and only after preload
 * completes, and [close] runs only after in-flight preload/update work has
 * stopped. Implementations may therefore keep mutable scheduler state without
 * adding their own locks, but must not perform I/O or block in [draw] or
 * [updateAudio].
 */
interface PreparedRender {
    val mode: RenderMode
    fun draw(canvas: Canvas, frame: RenderFrame)
    fun preloadAudio(sink: RenderAudioSink) = Unit
    fun updateAudio(frame: RenderAudioFrame, sink: RenderAudioSink) = Unit
    fun close() = Unit
}
