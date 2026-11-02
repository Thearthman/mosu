package com.mosu.app.domain.render

/**
 * Stateful one-shot/loop scheduler for a compiled osu!standard timeline.
 *
 * The renderer remains the owner of cue timing. This class only advances a
 * cursor over immutable cues and translates due cues into passive sink calls.
 */
class OsuHitsoundScheduler(
    timeline: OsuRenderTimeline
) {
    private val cues = timeline.hitsoundCues
    private val loops = timeline.hitsoundLoops
    private val activeLoopKeys = mutableSetOf<String>()
    private var previousEffectiveTimeMs: Double? = null
    private var previousClockEpoch: Long? = null
    private var wasPlaying = false
    private var hasReceivedFrame = false
    private var lastSink: RenderAudioSink? = null

    fun update(frame: RenderAudioFrame, sink: RenderAudioSink) {
        lastSink = sink
        val isFirstFrame = !hasReceivedFrame
        hasReceivedFrame = true
        val effectiveTimeMs = frame.mapTimeMs + frame.latencyMs
        val epochChanged = previousClockEpoch != frame.clockEpoch
        if (epochChanged) {
            stopAll(sink)
            previousClockEpoch = frame.clockEpoch
            previousEffectiveTimeMs = effectiveTimeMs
        }

        if (!frame.isPlaying) {
            if (wasPlaying || activeLoopKeys.isNotEmpty()) {
                stopAll(sink)
            }
            previousEffectiveTimeMs = effectiveTimeMs
            wasPlaying = false
            return
        }

        val previousTimeMs = previousEffectiveTimeMs
        if (previousTimeMs == null || effectiveTimeMs < previousTimeMs - TIME_EPSILON_MS) {
            stopAll(sink)
            previousEffectiveTimeMs = effectiveTimeMs
            updateLoops(effectiveTimeMs, sink)
            wasPlaying = true
            return
        }

        if (isFirstFrame) {
            playCues(effectiveTimeMs - TIME_EPSILON_MS, effectiveTimeMs, sink)
        }

        val lowerBoundMs = if (effectiveTimeMs - previousTimeMs > MAX_FORWARD_JUMP_MS) {
            effectiveTimeMs - MAX_FORWARD_JUMP_MS
        } else {
            previousTimeMs
        }
        if (effectiveTimeMs > lowerBoundMs) {
            playCues(lowerBoundMs, effectiveTimeMs, sink)
        }
        updateLoops(effectiveTimeMs, sink)
        previousEffectiveTimeMs = effectiveTimeMs
        wasPlaying = true
    }

    fun close() {
        lastSink?.stopAll()
        activeLoopKeys.clear()
        previousEffectiveTimeMs = null
        previousClockEpoch = null
        wasPlaying = false
        hasReceivedFrame = false
        lastSink = null
    }

    private fun playCues(
        lowerBoundMs: Double,
        upperBoundMs: Double,
        sink: RenderAudioSink
    ) {
        var index = firstCueAfter(lowerBoundMs)
        while (index < cues.size) {
            val cue = cues[index]
            if (cue.timeMs > upperBoundMs) break
            // All cues at an identical time are emitted in one update. Keeping
            // the inclusive cursor at the old timestamp prevents the next
            // update from replaying them.
            if (cue.timeMs > lowerBoundMs) {
                cue.samples.forEach(sink::playSample)
            }
            index++
        }
    }

    private fun updateLoops(
        effectiveTimeMs: Double,
        sink: RenderAudioSink
    ) {
        val desiredLoopKeys = loops.asSequence()
            .filter { effectiveTimeMs >= it.startTimeMs && effectiveTimeMs < it.endTimeMs }
            .map { it.key }
            .toSet()

        activeLoopKeys
            .filterNot(desiredLoopKeys::contains)
            .toList()
            .forEach { key ->
                sink.stopLoop(key)
                activeLoopKeys.remove(key)
            }
        loops.asSequence()
            .filter { it.key in desiredLoopKeys && it.key !in activeLoopKeys }
            .forEach { loop ->
                sink.startLoop(loop.key, loop.sample)
                activeLoopKeys += loop.key
            }
    }

    private fun firstCueAfter(timeMs: Double): Int {
        var low = 0
        var high = cues.lastIndex
        var result = cues.size
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (cues[middle].timeMs > timeMs) {
                result = middle
                high = middle - 1
            } else {
                low = middle + 1
            }
        }
        return result
    }

    private fun stopAll(sink: RenderAudioSink) {
        sink.stopAll()
        activeLoopKeys.clear()
    }

    private companion object {
        const val MAX_FORWARD_JUMP_MS = 250.0
        const val TIME_EPSILON_MS = 0.01
    }
}
