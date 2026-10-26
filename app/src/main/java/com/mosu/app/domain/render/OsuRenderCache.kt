package com.mosu.app.domain.render

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Process-wide immutable timeline/header cache keyed by file identity. */
object OsuRenderCache {
    private data class FileIdentity(val length: Long, val modified: Long)
    private data class HeaderEntry(val identity: FileIdentity, val header: OsuMapHeader?)
    private data class TimelineEntry(val identity: FileIdentity, val timeline: OsuRenderTimeline?)

    private val headers = ConcurrentHashMap<String, HeaderEntry>()
    private val timelines = ConcurrentHashMap<String, TimelineEntry>()

    fun header(file: File): OsuMapHeader? {
        val key = file.canonicalPath
        val identity = identityOf(file) ?: run {
            headers.remove(key)
            return null
        }
        headers[key]?.takeIf { it.identity == identity }?.let { return it.header }
        val header = runCatching { OsuBeatmapParser.parseHeader(file) }.getOrNull()
        headers[key] = HeaderEntry(identity, header)
        prune()
        return header
    }

    fun timeline(file: File, expectedBeatmapSetId: Long): OsuRenderTimeline? {
        val key = "${file.canonicalPath}|$expectedBeatmapSetId"
        val identity = identityOf(file) ?: run {
            timelines.remove(key)
            return null
        }
        timelines[key]?.takeIf { it.identity == identity }?.let { return it.timeline }
        val timeline = runCatching {
            val parsed = OsuBeatmapParser.parse(file)
            require(parsed.hitObjects.isNotEmpty()) { "Map has no hit objects" }
            OsuTimelineCompiler.compile(parsed, expectedBeatmapSetId)
        }.getOrNull()
        timelines[key] = TimelineEntry(identity, timeline)
        prune()
        return timeline
    }

    fun invalidateSet(context: Context, beatmapSetId: Long) {
        val directory = File(context.getExternalFilesDir(null), "beatmaps/$beatmapSetId")
        val prefix = runCatching { directory.canonicalPath }.getOrElse { directory.absolutePath }
        val pathPrefix = "$prefix${File.separator}"
        headers.keys.removeIf { it.startsWith(pathPrefix) }
        timelines.keys.removeIf { it.startsWith(pathPrefix) }
    }

    fun invalidateAll() {
        headers.clear()
        timelines.clear()
    }

    private fun identityOf(file: File): FileIdentity? {
        if (!file.isFile || !file.canRead()) return null
        return FileIdentity(file.length(), file.lastModified())
    }

    private fun prune() {
        if (headers.size > 256) {
            headers.keys.take(headers.size - 192).forEach(headers::remove)
        }
        if (timelines.size > 64) {
            timelines.keys.take(timelines.size - 48).forEach(timelines::remove)
        }
    }
}
