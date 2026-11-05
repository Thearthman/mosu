package com.mosu.app.domain.render.audio

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.mosu.app.domain.render.RenderAudioSink
import com.mosu.app.domain.render.RenderSample
import com.mosu.app.domain.render.RenderSkinSelection
import com.mosu.app.domain.render.SkinFileIndex
import com.un4seen.bass.BASS
import java.io.Closeable
import java.io.IOException

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
    private var skinSelection = RenderSkinSelection.Bundled
    private var customSkinFiles: SkinFileIndex? = null

    private var masterVolume = MAX_VOLUME
    private var bassInitialized = false
    private var bassLeaseHeld = false
    private var bassUnavailable = false
    private var released = false
    private val tracedPlayedSamples = mutableSetOf<RenderSample>()

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

    /**
     * Switches sample lookup to [selection]. Cached native samples are tied to
     * their source skin, so they are freed before the next preload pass.
     */
    fun setSkinSelection(selection: RenderSkinSelection) {
        synchronized(preloadGate) {
            val files = selection.directory
                ?.takeIf { !selection.isBundled && it.isDirectory }
                ?.let(SkinFileIndex::build)
            synchronized(lock) {
                if (released || skinSelection == selection) return
                skinSelection = selection
                customSkinFiles = files
                Log.d(
                    SKIN_TRACE_TAG,
                    "setSkinSelection id=${selection.id}/${selection.name} " +
                        "files=${files?.size ?: 0}"
                )
                stopAllLocked()
                if (bassActiveLocked()) {
                    samples.values.toList().forEach(::freeSampleLocked)
                }
                samples.clear()
                missingSamples.clear()
            }
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
            if (tracedPlayedSamples.add(sample) && tracedPlayedSamples.size <= TRACE_PLAY_LIMIT) {
                Log.d(
                    SKIN_TRACE_TAG,
                    "play sample=${sample.sampleName} bank=${sample.bank} " +
                        "skin=${skinSelection.id}/${skinSelection.name}"
                )
            }

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
                if (bassLeaseHeld) {
                    bassLeaseHeld = false
                    val result = BassDeviceLease.release()
                    result.errorCode?.let { errorCode ->
                        Log.w(TAG, "BASS device release failed (error $errorCode)")
                    }
                    result.failure?.let { error ->
                        Log.e(TAG, "BASS device release failed", error)
                    }
                }

                samples.clear()
                missingSamples.clear()
                loops.clear()
                oneShotChannels.clear()
                bassInitialized = false
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

        val acquisition = BassDeviceLease.acquire()
        acquisition.failure?.let { error ->
            synchronized(lock) {
                disableBassLocked("initialize BASS", error)
            }
            return false
        }
        if (!acquisition.acquired) {
            Log.w(
                TAG,
                "BASS initialization failed (error ${acquisition.errorCode}); hitsounds disabled"
            )
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
            bassLeaseHeld = true
            applyMasterVolumeLocked()
        }
        Log.d(TAG, "BASS initialized for hitsounds")

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

        val customFiles = customSkinFiles
        if (customFiles != null) {
            for (filename in HitSampleResolver.userSkinCandidates(sample)) {
                val file = customFiles.find(filename) ?: continue
                if (!file.isFile) continue
                if (tryLoadSample(sample, file.absolutePath) { path ->
                        BASS.BASS_SampleLoad(
                            path,
                            0,
                            0,
                            MAX_SAMPLE_CHANNELS,
                            BASS.BASS_SAMPLE_OVER_POS
                        )
                    }
                ) {
                    return
                }
            }
        }

        for (assetPath in HitSampleResolver.bundledAssetCandidates(sample)) {
            if (!assetExists(assetPath)) continue
            if (tryLoadSample(sample, assetPath) { path ->
                    BASS.BASS_SampleLoad(
                        BASS.Asset(assets, path),
                        0,
                        0,
                        MAX_SAMPLE_CHANNELS,
                        BASS.BASS_SAMPLE_OVER_POS
                    )
                }
            ) {
                return
            }
        }

        synchronized(lock) {
            if (!released && bassActiveLocked()) missingSamples += sample
        }
        Log.w(TAG, "No playable hitsound asset for ${sample.sampleName} (${sample.bank})")
    }

    private fun tryLoadSample(
        sample: RenderSample,
        source: String,
        load: (String) -> Int
    ): Boolean {
        val handle = guardBass("load sample $source") { load(source) } ?: return false
        if (handle == 0) return false

        val accepted = synchronized(lock) {
            if (released || !bassActiveLocked()) {
                false
            } else {
                samples.putIfAbsent(sample, handle) == null
            }
        }
        if (accepted) {
            if (tracedPlayedSamples.size < TRACE_PLAY_LIMIT) {
                Log.d(
                    SKIN_TRACE_TAG,
                    "loaded ${sample.sampleName}/${sample.bank} from $source"
                )
            }
            return true
        }

        synchronized(lock) {
            freeSampleLocked(handle)
        }
        return false
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
        const val SKIN_TRACE_TAG = "MosuSkinTrace"
        const val SAMPLE_RATE = 44_100
        const val MAX_SAMPLE_CHANNELS = 32
        const val MIN_VOLUME = 0
        const val MAX_VOLUME = 100
        const val DEFAULT_SAMPLE_VOLUME = 100
        const val TRACE_PLAY_LIMIT = 24
    }
}

/**
 * BASS owns one device per process, regardless of how many render sessions
 * exist. The activity and service can briefly overlap during task handoff, so
 * initialization is reference-counted: every sink leases the shared device,
 * and only the last sink releases it.
 */
private object BassDeviceLease {
    private val lock = Any()
    private var owners = 0
    private var initialized = false

    fun acquire(): BassDeviceAcquisition = synchronized(lock) {
        if (initialized) {
            owners++
            return@synchronized BassDeviceAcquisition(
                acquired = true,
                errorCode = BASS.BASS_OK,
                reusedExistingDevice = true
            )
        }

        try {
            val initializedNow = BASS.BASS_Init(BASS_DEVICE_DEFAULT, SAMPLE_RATE, 0)
            val errorCode = if (initializedNow) BASS.BASS_OK else BASS.BASS_ErrorGetCode()
            if (initializedNow || errorCode == BASS.BASS_ERROR_ALREADY) {
                initialized = true
                owners++
                BassDeviceAcquisition(
                    acquired = true,
                    errorCode = errorCode,
                    reusedExistingDevice = !initializedNow
                )
            } else {
                BassDeviceAcquisition(acquired = false, errorCode = errorCode)
            }
        } catch (error: LinkageError) {
            BassDeviceAcquisition(acquired = false, errorCode = BASS.BASS_ERROR_INIT, failure = error)
        } catch (error: RuntimeException) {
            BassDeviceAcquisition(acquired = false, errorCode = BASS.BASS_ERROR_INIT, failure = error)
        }
    }

    fun release(): BassDeviceRelease = synchronized(lock) {
        if (owners == 0) return@synchronized BassDeviceRelease()

        owners--
        if (owners > 0 || !initialized) return@synchronized BassDeviceRelease()

        initialized = false
        try {
            if (BASS.BASS_Free()) {
                BassDeviceRelease()
            } else {
                BassDeviceRelease(errorCode = BASS.BASS_ErrorGetCode())
            }
        } catch (error: LinkageError) {
            BassDeviceRelease(failure = error)
        } catch (error: RuntimeException) {
            BassDeviceRelease(failure = error)
        }
    }

    private const val SAMPLE_RATE = 44_100
    private const val BASS_DEVICE_DEFAULT = -1
}

private data class BassDeviceAcquisition(
    val acquired: Boolean,
    val errorCode: Int,
    val reusedExistingDevice: Boolean = false,
    val failure: Throwable? = null
)

private data class BassDeviceRelease(
    val errorCode: Int? = null,
    val failure: Throwable? = null
)
