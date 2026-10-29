package com.mosu.app.domain.render

import android.content.Context
import android.graphics.Canvas

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
        difficulty: RenderDifficulty
    ): PreparedRender?
}

interface PreparedRender {
    val mode: RenderMode
    fun draw(canvas: Canvas, frame: RenderFrame)
    fun close() = Unit
}
