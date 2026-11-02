package com.mosu.app.domain.render.audio

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.mosu.app.domain.render.RenderAudioSink
import com.mosu.app.domain.render.RenderSample
import com.mosu.app.domain.render.RenderSampleBank
import com.un4seen.bass.BASS
import java.io.Closeable
import java.io.IOException
import java.util.Locale

/**
 * BASS-backed sample mixer for renderer-owned hitsound cues.
 *
 * The sink is deliberately passive: renderers decide when a cue is due, while
 * this class only allocates and controls BASS sample channels. Native or asset
 * failures disable playback and leave the rest of the renderer functional.
 */
class BassRenderAudioSink(context: Context) : RenderAudioSink, Closeable {
    private val assets: AssetManager = context.applicationContext.assets
    private val lock = Any()

    /**
     * Serializes preload work and release. Asset/native sample loading must
     * never run while [lock] is held, otherwise frame-thread playback would
     * wait for background asset I/O.
     */
    private val preloadGate = Any()

    private val samples = mutableMapOf<RenderSample, Int>()
    private val missingSamples = mutableSetOf<RenderSample>()
    private val loops = mutableMapOf<String, ActiveLoop>()
    private val oneShotChannels = mutableSetOf<Int>()

    private var masterVolume = MAX_VOLUME
    private var bassInitialized = false
    private var bassNeedsFree = false
    private var bassUnavailable = false
    private var released = false

    /**
     * Sets the global sample volume. Values outside 0..100 are clamped.
     *
     * A stored volume is applied when BASS is initialized by the first preload.
     * Changing it while BASS is active affects one-shots already
     * in flight as well as loops.
     */
    fun setMasterVolume(volume: Int) {
        synchronized(lock) {
            if (released) return

            val clamped = volume.coerceIn(MIN_VOLUME, MAX_VOLUME)
            if (clamped == masterVolume) return

            masterVolume = clamped
            if (bassActiveLocked()) applyMasterVolumeLocked()
        }
    }

    override fun preloadSamples(samples: Iterable<RenderSample>) {
        val requests = samples
            .map { it.copy(volume = DEFAULT_SAMPLE_VOLUME) }
            .distinct()
        if (requests.isEmpty()) return

        synchronized(preloadGate) {
            if (!ensureInitializedForPreload()) return
            requests.forEach(::loadSampleForPreload)
        }
    }

    override fun playSample(sample: RenderSample) {
        synchronized(lock) {
            if (released || !bassActiveLocked()) return
            reapFinishedOneShotChannelsLocked()

            val sampleHandle = samples[sample.copy(volume = DEFAULT_SAMPLE_VOLUME)] ?: return
            val channel = createChannelLocked(sampleHandle, sample.volume) ?: return

            val started = guardBass("start one-shot channel") {
                BASS.BASS_ChannelPlay(channel, false)
            } ?: run {
                freeChannelLocked(channel)
                return
            }
            if (!started) {
                freeChannelLocked(channel)
                return
            }
            oneShotChannels += channel
        }
    }

    override fun startLoop(key: String, sample: RenderSample) {
        synchronized(lock) {
            if (released || !bassActiveLocked()) return

            val request = sample.copy(volume = DEFAULT_SAMPLE_VOLUME)
            val sampleVolume = sample.volume.coerceIn(MIN_VOLUME, MAX_VOLUME)
            val existing = loops[key]

            if (existing != null && existing.request == request) {
                if (existing.sampleVolume != sampleVolume) {
                    if (setChannelVolumeLocked(existing.channel, sampleVolume)) {
                        loops[key] = existing.copy(sampleVolume = sampleVolume)
                    } else {
                        freeChannelLocked(existing.channel)
                        loops.remove(key)
                    }
                }
                return
            }

            if (existing != null) stopLoopLocked(key, existing)

            val sampleHandle = samples[request] ?: return
            val channel = createChannelLocked(sampleHandle, sampleVolume) ?: return

            val loopFlags = guardBass("enable loop channel") {
                BASS.BASS_ChannelFlags(
                    channel,
                    BASS.BASS_SAMPLE_LOOP,
                    BASS.BASS_SAMPLE_LOOP
                )
            } ?: run {
                freeChannelLocked(channel)
                return
            }
            if ((loopFlags and BASS.BASS_SAMPLE_LOOP.toLong()) == 0L) {
                Log.w(TAG, "BASS did not enable looping on the sample channel")
                freeChannelLocked(channel)
                return
            }
            val started = guardBass("start loop channel") {
                BASS.BASS_ChannelPlay(channel, false)
            } ?: run {
                freeChannelLocked(channel)
                return
            }
            if (!started) {
                Log.w(TAG, "BASS could not start the loop channel")
                freeChannelLocked(channel)
                return
            }

            loops[key] = ActiveLoop(
                channel = channel,
                request = request,
                sampleVolume = sampleVolume
            )
        }
    }

    override fun stopLoop(key: String) {
        synchronized(lock) {
            if (released) return
            loops[key]?.let { stopLoopLocked(key, it) }
        }
    }

    /** Stops every active loop without interrupting one-shot samples. */
    fun stopLoops() {
        synchronized(lock) {
            if (released) return
            stopLoopsLocked()
        }
    }

    /** Stops every active sample channel, including one-shots and loops. */
    override fun stopAll() {
        synchronized(lock) {
            if (released) return
            stopAllLocked()
        }
    }

    /** Stops playback and frees all BASS samples, channels, and the device. */
    fun release() {
        synchronized(preloadGate) {
            synchronized(lock) {
                if (released) return

                stopAllLocked()
                if (bassActiveLocked()) {
                    samples.values.toList().forEach(::freeSampleLocked)
                }
                if (bassActiveLocked() || bassNeedsFree) {
                    guardBass("free BASS") {
                        BASS.BASS_Free()
                    }
                }

                samples.clear()
                missingSamples.clear()
                loops.clear()
                oneShotChannels.clear()
                bassInitialized = false
                bassNeedsFree = false
                released = true
            }
        }
    }

    override fun close() = release()

    /**
     * Initializes BASS while holding only [preloadGate]. The state lock is used
     * solely to read/publish lifecycle flags around the native call.
     */
    private fun ensureInitializedForPreload(): Boolean {
        synchronized(lock) {
            if (bassActiveLocked()) return true
            if (released || bassUnavailable) return false
        }

        val initialized = guardBass("initialize BASS") {
            BASS.BASS_Init(BASS_DEVICE_DEFAULT, SAMPLE_RATE, 0)
        } ?: return false
        if (!initialized) {
            val errorCode = BASS.BASS_ErrorGetCode()
            Log.w(TAG, "BASS initialization failed (error $errorCode); hitsounds disabled")
            synchronized(lock) {
                if (!released) bassUnavailable = true
            }
            return false
        }

        synchronized(lock) {
            // release cannot run concurrently because preloadGate is held, and
            // no sample handles can have failed before this initialization is
            // published.
            bassInitialized = true
            bassNeedsFree = false
            applyMasterVolumeLocked()
        }

        return synchronized(lock) { bassActiveLocked() }
    }

    private fun applyMasterVolumeLocked(): Boolean {
        if (!bassActiveLocked()) return false
        val applied = guardBass("set master volume") {
            BASS.BASS_SetConfig(BASS.BASS_CONFIG_GVOL_SAMPLE, masterVolume * 100)
        } ?: return false
        if (!applied) {
            Log.w(TAG, "BASS could not apply the master sample volume")
        }
        return applied && bassActiveLocked()
    }

    /**
     * Loads one requested sample into the cache. This runs under [preloadGate]
     * but outside [lock] so playback never waits on asset resolution or native
     * sample decoding.
     */
    private fun loadSampleForPreload(sample: RenderSample) {
        synchronized(lock) {
            if (released || !bassActiveLocked()) return
            if (sample in samples || sample in missingSamples) return
        }

        for (assetPath in sampleAssetCandidates(sample)) {
            if (!assetExists(assetPath)) continue

            val handle = guardBass("load sample $assetPath") {
                BASS.BASS_SampleLoad(
                    BASS.Asset(assets, assetPath),
                    0,
                    0,
                    MAX_SAMPLE_CHANNELS,
                    BASS.BASS_SAMPLE_OVER_POS
                )
            } ?: return
            if (handle == 0) continue

            val accepted = synchronized(lock) {
                if (released || !bassActiveLocked()) {
                    false
                } else {
                    samples.putIfAbsent(sample, handle) == null
                }
            }
            if (accepted) return

            synchronized(lock) {
                freeSampleLocked(handle)
            }
            return
        }

        synchronized(lock) {
            if (!released && bassActiveLocked()) missingSamples += sample
        }
        Log.w(TAG, "No playable hitsound asset for ${sample.sampleName} (${sample.bank})")
    }

    private fun createChannelLocked(sampleHandle: Int, sampleVolume: Int): Int? {
        if (!bassActiveLocked()) return null
        if (sampleVolume <= MIN_VOLUME) return null

        val channel = guardBass("create sample channel") {
            BASS.BASS_SampleGetChannel(sampleHandle, BASS.BASS_SAMCHAN_NEW)
        } ?: return null
        if (channel == 0) {
            Log.w(TAG, "BASS could not allocate a sample channel")
            return null
        }

        if (!setChannelVolumeLocked(channel, sampleVolume)) {
            freeChannelLocked(channel)
            return null
        }
        return channel
    }

    private fun setChannelVolumeLocked(channel: Int, volume: Int): Boolean {
        if (!bassActiveLocked()) return false
        val channelVolume = volume.coerceIn(MIN_VOLUME, MAX_VOLUME) / MAX_VOLUME.toFloat()
        val applied = guardBass("set channel volume") {
            BASS.BASS_ChannelSetAttribute(channel, BASS.BASS_ATTRIB_VOL, channelVolume)
        } ?: return false
        return applied && bassActiveLocked()
    }

    private fun stopLoopLocked(key: String, loop: ActiveLoop) {
        freeChannelLocked(loop.channel)
        loops.remove(key)
    }

    private fun stopLoopsLocked() {
        loops.values.toList().forEach { loop -> freeChannelLocked(loop.channel) }
        loops.clear()
    }

    private fun stopAllLocked() {
        stopLoopsLocked()
        reapFinishedOneShotChannelsLocked()
        oneShotChannels.toList().forEach(::freeChannelLocked)
        oneShotChannels.clear()
        if (!bassActiveLocked()) return

        for (sampleHandle in samples.values.toList()) {
            if (!bassActiveLocked()) break
            guardBass("stop sample channels") {
                BASS.BASS_SampleStop(sampleHandle)
            }
        }
    }

    private fun reapFinishedOneShotChannelsLocked() {
        oneShotChannels.toList().forEach { channel ->
            val active = guardBass("inspect one-shot channel") {
                BASS.BASS_ChannelIsActive(channel)
            } ?: return@forEach
            if (active == BASS.BASS_ACTIVE_STOPPED) {
                freeChannelLocked(channel)
                oneShotChannels.remove(channel)
            }
        }
    }

    private fun assetExists(assetPath: String): Boolean {
        return try {
            assets.open(assetPath).use { }
            true
        } catch (_: IOException) {
            false
        }
    }

    private fun freeChannelLocked(channel: Int) {
        if (!bassActiveLocked()) return
        guardBass("stop abandoned channel") {
            BASS.BASS_ChannelStop(channel)
        }
        if (!bassActiveLocked()) return
        guardBass("free abandoned channel") {
            BASS.BASS_ChannelFree(channel)
        }
    }

    private fun freeSampleLocked(sampleHandle: Int) {
        if (!bassActiveLocked()) return
        guardBass("free sample") {
            BASS.BASS_SampleFree(sampleHandle)
        }
    }

    private fun bassActiveLocked(): Boolean {
        return bassInitialized && !bassUnavailable && !released
    }

    private inline fun <T> guardBass(operation: String, block: () -> T): T? {
        return try {
            block()
        } catch (error: LinkageError) {
            synchronized(lock) {
                disableBassLocked(operation, error)
            }
            null
        } catch (error: RuntimeException) {
            synchronized(lock) {
                disableBassLocked(operation, error)
            }
            null
        }
    }

    private fun disableBassLocked(operation: String, error: Throwable) {
        if (!bassUnavailable) {
            Log.e(TAG, "BASS failed to $operation; hitsounds disabled", error)
        }
        val needsDeviceCleanup = bassInitialized
        bassNeedsFree = bassNeedsFree || needsDeviceCleanup
        bassUnavailable = true
        bassInitialized = false
        loops.clear()
        oneShotChannels.clear()
        samples.clear()
        missingSamples.clear()
    }

    private data class ActiveLoop(
        val channel: Int,
        val request: RenderSample,
        val sampleVolume: Int
    )

    private companion object {
        const val TAG = "BassRenderAudioSink"
        const val ASSET_ROOT = "hitsounds"
        const val SAMPLE_RATE = 44_100
        const val MAX_SAMPLE_CHANNELS = 32
        const val MIN_VOLUME = 0
        const val MAX_VOLUME = 100
        const val DEFAULT_SAMPLE_VOLUME = 100
        const val BASS_DEVICE_DEFAULT = -1

        val SAMPLE_EXTENSIONS = listOf("wav", "mp3", "ogg")

        fun sampleAssetCandidates(sample: RenderSample): List<String> {
            val names = linkedSetOf<String>()
            val sampleName = sample.sampleName.toAssetBaseName()
            val sampleStem = sampleName.substringBeforeLast('.', sampleName)

            sample.customFilename
                ?.toAssetBaseName()
                ?.takeIf { it.isNotEmpty() }
                ?.let { names.addAll(it.withSampleExtensions()) }

            if (sampleName.isNotEmpty()) {
                val bankPrefix = sample.bank.assetPrefix
                val index = sample.customSampleIndex.coerceAtLeast(0)
                if (index > 0) {
                    names.addAll("$bankPrefix-$sampleStem$index".withSampleExtensions())
                }
                names.addAll("$bankPrefix-$sampleStem".withSampleExtensions())
                names.addAll(sampleStem.withSampleExtensions())
            }

            return names
                .flatMap { name ->
                    val lowerName = name.lowercase(Locale.ROOT)
                    if (lowerName == name) listOf(name) else listOf(name, lowerName)
                }
                .distinct()
                .map { "$ASSET_ROOT/$it" }
        }

        fun String.toAssetBaseName(): String {
            return trim()
                .replace('\\', '/')
                .substringAfterLast('/')
        }

        fun String.withSampleExtensions(): List<String> {
            val extension = substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (extension in SAMPLE_EXTENSIONS) return listOf(this)
            return SAMPLE_EXTENSIONS.map { "$this.$it" }
        }

        val RenderSampleBank.assetPrefix: String
            get() = when (this) {
                RenderSampleBank.NORMAL -> "normal"
                RenderSampleBank.SOFT -> "soft"
                RenderSampleBank.DRUM -> "drum"
            }
    }
}
