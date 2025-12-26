<img src="https://r2cdn.perplexity.ai/pplx-full-logo-primary-dark%402x.png" style="height:64px;margin-right:32px"/>

# write a detailed instruction for my coworker to work on this.

Here is a detailed technical brief you can pass to your coworker. It is structured as an engineering task ticket, covering the **Why**, the **How**, and the **Implementation Steps**.

***

# Engineering Task: High-Fidelity Audio Time-Stretching Implementation

**Date:** December 26, 2025
**Priority:** High (Blocking audio quality parity with *osu!*)
**Context:** `DOUBLE_TIME` Mod (1.5x Speed, 1.0x Pitch)

## 1. Problem Statement

Our current implementation uses Android Media3's default `SonicAudioProcessor` to handle playback speed adjustment. While efficient, `Sonic` uses a time-domain algorithm (WSOLA) that causes severe "phasiness" and saw-tooth distortion on polyphonic instrumentals, particularly piano tracks.

This does not meet user expectations set by *osu!lazer*, which uses high-quality frequency-domain processing (via the BASS library) to preserve transient integrity.

## 2. Objective

Replace the default internal time-stretching logic in ExoPlayer/Media3 with a custom **AudioProcessor** backed by the **Rubber Band Library**.

**Success Criteria:**

* Audio playback at 1.5x speed / 1.0x pitch has no metallic/robotic artifacts.
* Piano decay remains smooth.
* CPU usage remains within acceptable limits for mobile devices.


## 3. Architecture Overview

We cannot "configure" our way out of this. We must perform a **JNI (Java Native Interface)** integration.

1. **Native Layer (C++):** Compile **Rubber Band** (an open-source high-quality time-stretching library) for Android.
2. **JNI Layer:** Create a bridge to feed PCM audio buffers from Android to Rubber Band and retrieve the stretched samples.
3. **App Layer (Kotlin):** Implement a custom Media3 `AudioProcessor` that intercepts the audio stream before it hits the hardware output.

## 4. Implementation Instructions

### Phase A: Native Setup (NDK)

1. **Download Rubber Band:** Clone the [Rubber Band Library](https://github.com/breakfastquay/rubberband).
    * *Note:* This library is **GPL**. If our app is closed-source/proprietary, we must purchase a commercial license from *Breakfast Quay* or switch to the **Superpowered SDK** (paid).
2. **CMake Configuration:**
    * Add `CMakeLists.txt` to our `app/src/main/cpp` directory.
    * Include Rubber Band source files.
    * Enable standard C++14 or higher.

### Phase B: The JNI Bridge (`native-lib.cpp`)

We need a C++ class that wraps the Rubber Band state.

```cpp
#include <rubberband/RubberBandStretcher.h>

// Initialize
RubberBand::RubberBandStretcher* stretcher = new RubberBand::RubberBandStretcher(
    sampleRate, 
    channels, 
    RubberBand::RubberBandStretcher::OptionProcessRealTime | 
    RubberBand::RubberBandStretcher::OptionTransientsCrisp // Important for piano!
);

// Setting Speed
stretcher->setTimeRatio(1.0 / speed); // RubberBand uses ratio (duration), so 1.5x speed = 1/1.5 ratio

// Processing Loop (Pseudo-code)
// 1. Receive input float* buffer from Kotlin
stretcher->process(inputBuffer, frameCount, false);

// 2. Check available samples
int available = stretcher->available();

// 3. Retrieve into output buffer
stretcher->retrieve(outputBuffer, available);
```


### Phase C: Custom AudioProcessor (Kotlin)

Create `RubberBandAudioProcessor.kt` implementing `androidx.media3.common.audio.AudioProcessor`.

**Key Logic:**
Unlike standard buffers, **Time Stretching changes the buffer size.**

* If we feed in 1000 samples at 1.5x speed, we expect ~666 samples out.
* The `queueInput(inputBuffer: ByteBuffer)` method must pass data to JNI.
* The `getOutput(): ByteBuffer` method must pull processed data from JNI.

```kotlin
class RubberBandAudioProcessor : AudioProcessor {
    private var inputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    
    // Pass the speed setting here
    fun setSpeed(speed: Float) {
        nativeSetTimeRatio(1.0 / speed) 
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // 1. Convert ByteBuffer to FloatArray (if needed)
        // 2. Call JNI: process(floatArray)
        // 3. Call JNI: retrieve() -> get processed bytes
        // 4. Fill local outputBuffer with result
    }

    override fun getOutput(): ByteBuffer {
        val buffer = this.outputBuffer
        this.outputBuffer = AudioProcessor.EMPTY_BUFFER
        return buffer
    }
    
    // ... implement configure(), flush(), reset()
}
```


### Phase D: Injecting into ExoPlayer

We must tell ExoPlayer to use our processor instead of its default.

```kotlin
val renderersFactory = object : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context, 
        enableFloatOutput: Boolean, 
        enableAudioTrackPlaybackParams: Boolean, 
        enableOffload: Boolean
    ): AudioSink {
        // We inject our custom processor into the chain
        return DefaultAudioSink.Builder()
            .setAudioProcessors(arrayOf(RubberBandAudioProcessor()))
            .build()
    }
}

// Initialize player with this factory
val player = ExoPlayer.Builder(context, renderersFactory).build()
```


## 5. Critical Tuning Notes

* **Latency:** Rubber Band adds latency. We may need to adjust the `AudioSink` buffer size if the game feels "laggy" (though for a music player, this is fine).
* **Options:** When initializing Rubber Band in C++, specifically use `OptionTransientsCrisp` or `OptionTransientsMixed`. This is what fixes the piano attacks. The default `OptionTransientsSmooth` may sound too soft.


## 6. Resources

* **Rubber Band API:** [API Documentation](https://breakfastquay.com/rubberband/code-doc/index.html)
* **Media3 AudioProcessor Interface:** [Android Developer Docs](https://developer.android.com/reference/androidx/media3/common/audio/AudioProcessor)
* **Alternative SDK (Simpler but Paid):** [Superpowered Time Stretching](https://superpowered.com/time-stretching-pitch-shifting) (Consider this if the JNI/GPL route is too heavy).

