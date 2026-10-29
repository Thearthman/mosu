package com.mosu.app.domain.render

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class OsuMapHeader(
    val beatmapSetId: Long?,
    val beatmapId: Long? = null,
    val mode: Int,
    val audioFilename: String,
    val difficultyName: String,
    val approachRate: Float,
    val overallDifficulty: Float,
    val circleSize: Float,
    val sliderMultiplier: Float,
    val stackLeniency: Float
)

class OsuMapParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Ruleset-neutral reader for the header sections shared by every `.osu` mode.
 */
object OsuFileHeaderReader {
    fun parse(file: File): OsuMapHeader {
        val text = try {
            file.bufferedReader(Charsets.UTF_8).use { reader ->
                val builder = StringBuilder()
                var section = ""
                while (true) {
                    val rawLine = reader.readLine() ?: break
                    val line = rawLine.trim()
                    if (line.startsWith("[") && line.endsWith("]")) {
                        section = line.substring(1, line.length - 1)
                        if (section == "HitObjects") break
                    }
                    if (section != "HitObjects") {
                        builder.append(line).append('\n')
                    }
                }
                builder.toString()
            }
        } catch (e: Exception) {
            throw OsuMapParseException("Unable to read ${file.name}", e)
        }
        return parse(text)
    }

    fun parse(text: String): OsuMapHeader {
        var section = ""
        val general = mutableMapOf<String, String>()
        val metadata = mutableMapOf<String, String>()
        val difficulty = mutableMapOf<String, String>()
        text.removePrefix("\uFEFF").lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("//")) return@forEach
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length - 1)
                return@forEach
            }
            when (section) {
                "General" -> parseKeyValue(line)?.let { (key, value) -> general[key] = value }
                "Metadata" -> parseKeyValue(line)?.let { (key, value) -> metadata[key] = value }
                "Difficulty" -> parseKeyValue(line)?.let { (key, value) -> difficulty[key] = value }
            }
        }

        val audioFilename = general["AudioFilename"]?.trim().orEmpty()
        val difficultyName = metadata["Version"]?.trim().orEmpty()
        if (audioFilename.isBlank()) throw OsuMapParseException("AudioFilename is missing")
        if (difficultyName.isBlank()) throw OsuMapParseException("Version is missing")

        val mode = general["Mode"]?.trim()?.toIntOrNull() ?: 0
        val overallDifficulty = difficulty.floatValue("OverallDifficulty") ?: 5f
        val approachRate = difficulty.floatValue("ApproachRate") ?: overallDifficulty
        return OsuMapHeader(
            beatmapSetId = metadata["BeatmapSetID"]?.trim()?.toLongOrNull()?.takeIf { it > 0L },
            beatmapId = metadata["BeatmapID"]?.trim()?.toLongOrNull()?.takeIf { it > 0L },
            mode = mode,
            audioFilename = normalizeAudioFilename(audioFilename),
            difficultyName = difficultyName,
            approachRate = approachRate.coerceIn(0f, 10f),
            overallDifficulty = overallDifficulty.coerceIn(0f, 10f),
            circleSize = (difficulty.floatValue("CircleSize") ?: 4f).coerceIn(0f, 10f),
            sliderMultiplier = (difficulty.floatValue("SliderMultiplier") ?: 1.4f)
                .takeIf { it > 0f } ?: 1.4f,
            stackLeniency = (difficulty.floatValue("StackLeniency") ?: 0.7f)
                .coerceIn(0f, 1f)
        )
    }

    /**
     * Old `.osu` formats predate BeatmapSetID and therefore omit it. The
     * containing `beatmaps/{setId}` directory supplies that identity, while an
     * explicitly different embedded id remains invalid.
     */
    fun belongsToBeatmapSet(
        embeddedBeatmapSetId: Long?,
        expectedBeatmapSetId: Long
    ): Boolean = embeddedBeatmapSetId == null || embeddedBeatmapSetId == expectedBeatmapSetId

    fun normalizeAudioFilename(value: String): String =
        value.trim()
            .replace('\\', '/')
            .trimStart('/')
            .lowercase()

    private fun parseKeyValue(line: String): Pair<String, String>? {
        val separator = line.indexOf(':')
        if (separator <= 0) return null
        return line.substring(0, separator).trim() to line.substring(separator + 1).trim()
    }

    private fun Map<String, String>.floatValue(key: String): Float? =
        this[key]?.trim()?.toFloatOrNull()
}

/** Process-wide immutable header cache keyed by file identity. */
object OsuFileHeaderCache {
    private data class FileIdentity(val length: Long, val modified: Long)
    private data class HeaderEntry(val identity: FileIdentity, val header: OsuMapHeader?)

    private val headers = ConcurrentHashMap<String, HeaderEntry>()

    fun header(file: File): OsuMapHeader? {
        val key = file.canonicalPath
        val identity = identityOf(file) ?: run {
            headers.remove(key)
            return null
        }
        headers[key]?.takeIf { it.identity == identity }?.let { return it.header }
        val header = runCatching { OsuFileHeaderReader.parse(file) }.getOrNull()
        headers[key] = HeaderEntry(identity, header)
        prune()
        return header
    }

    fun invalidateSet(context: Context, beatmapSetId: Long) {
        val directory = File(context.getExternalFilesDir(null), "beatmaps/$beatmapSetId")
        val prefix = runCatching { directory.canonicalPath }.getOrElse { directory.absolutePath }
        val pathPrefix = "$prefix${File.separator}"
        headers.keys.removeIf { it.startsWith(pathPrefix) }
    }

    fun invalidateAll() {
        headers.clear()
    }

    private fun identityOf(file: File): FileIdentity? {
        if (!file.isFile || !file.canRead()) return null
        return FileIdentity(file.length(), file.lastModified())
    }

    private fun prune() {
        if (headers.size > 256) {
            headers.keys.take(headers.size - 192).forEach(headers::remove)
        }
    }
}
