package com.mosu.app.domain.render.mania

import android.content.Context
import com.mosu.app.domain.render.OsuFileHeaderReader
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide immutable mania-timeline cache. The key includes every identity
 * input used by preparation so an album track cannot reuse another
 * difficulty's parsed scroll data.
 */
object ManiaRenderCache {
    private data class FileIdentity(val length: Long, val modified: Long)
    private data class TimelineEntry(
        val identity: FileIdentity,
        val timeline: ManiaRenderTimeline?
    )

    private val timelines = ConcurrentHashMap<String, TimelineEntry>()

    fun timeline(
        file: File,
        expectedBeatmapSetId: Long,
        expectedDifficultyName: String,
        expectedAudioFilename: String
    ): ManiaRenderTimeline? {
        val rawKey = "${file.canonicalPath}|$expectedBeatmapSetId|" +
            "$expectedDifficultyName|" +
            OsuFileHeaderReader.normalizeAudioFilename(expectedAudioFilename)
        val identity = identityOf(file) ?: run {
            timelines.remove(rawKey)
            return null
        }
        timelines[rawKey]?.takeIf { it.identity == identity }?.let { return it.timeline }
        val timeline = runCatching {
            val parsed = ManiaBeatmapParser.parseAndValidate(
                file = file,
                expectedBeatmapSetId = expectedBeatmapSetId,
                expectedDifficultyName = expectedDifficultyName,
                expectedAudioFilename = expectedAudioFilename
            ) ?: return@runCatching null
            ManiaTimelineCompiler.compile(parsed)
        }.getOrNull()
        timelines[rawKey] = TimelineEntry(identity, timeline)
        prune()
        return timeline
    }

    fun invalidateSet(context: Context, beatmapSetId: Long) {
        val directory = File(context.getExternalFilesDir(null), "beatmaps/$beatmapSetId")
        val prefix = runCatching { directory.canonicalPath }
            .getOrElse { directory.absolutePath }
        val pathPrefix = "$prefix${File.separator}"
        timelines.keys.removeIf { it.startsWith(pathPrefix) }
    }

    fun invalidateAll() {
        timelines.clear()
    }

    private fun identityOf(file: File): FileIdentity? {
        if (!file.isFile || !file.canRead()) return null
        return FileIdentity(file.length(), file.lastModified())
    }

    private fun prune() {
        if (timelines.size > 64) {
            timelines.keys.take(timelines.size - 48).forEach(timelines::remove)
        }
    }
}
