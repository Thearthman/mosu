package com.mosu.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Semantic haptic feedback for meaningful app interactions.
 *
 * Uses [View.performHapticFeedback] without an "ignore global setting" flag so Android
 * continues to honour the user's system touch-feedback preference.
 */
internal class Haptics(private val view: View) {
    fun button() = perform(HapticKind.BUTTON)

    fun longPress() = perform(HapticKind.LONG_PRESS)

    fun confirm() = perform(HapticKind.CONFIRM)

    fun reject() = perform(HapticKind.REJECT)

    fun toggle(enabled: Boolean) = perform(
        if (enabled) HapticKind.TOGGLE_ON else HapticKind.TOGGLE_OFF
    )

    fun segmentTick() = perform(HapticKind.SEGMENT_TICK)

    fun gestureStart() = perform(HapticKind.GESTURE_START)

    fun gestureEnd() = perform(HapticKind.GESTURE_END)

    fun gestureThreshold(active: Boolean) = perform(
        if (active) {
            HapticKind.GESTURE_THRESHOLD_ACTIVATE
        } else {
            HapticKind.GESTURE_THRESHOLD_DEACTIVATE
        }
    )

    private fun perform(kind: HapticKind) {
        view.performHapticFeedback(hapticConstantFor(kind, Build.VERSION.SDK_INT))
    }
}

@Composable
internal fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

internal enum class HapticKind {
    BUTTON,
    LONG_PRESS,
    CONFIRM,
    REJECT,
    TOGGLE_ON,
    TOGGLE_OFF,
    SEGMENT_TICK,
    GESTURE_START,
    GESTURE_END,
    GESTURE_THRESHOLD_ACTIVATE,
    GESTURE_THRESHOLD_DEACTIVATE
}

/**
 * Keeps SDK-version selection pure so it can be tested without Android runtime services.
 */
internal fun hapticConstantFor(kind: HapticKind, sdkInt: Int): Int {
    return when (kind) {
        HapticKind.BUTTON -> HapticFeedbackConstants.VIRTUAL_KEY
        HapticKind.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
        HapticKind.CONFIRM -> {
            if (sdkInt >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        }
        HapticKind.REJECT -> {
            if (sdkInt >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        }
        HapticKind.TOGGLE_ON -> {
            if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.TOGGLE_ON
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        }
        HapticKind.TOGGLE_OFF -> {
            if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.TOGGLE_OFF
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        }
        HapticKind.SEGMENT_TICK -> {
            if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
        }
        HapticKind.GESTURE_START -> {
            if (sdkInt >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.GESTURE_START
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        }
        HapticKind.GESTURE_END -> {
            if (sdkInt >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.GESTURE_END
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        }
        HapticKind.GESTURE_THRESHOLD_ACTIVATE -> {
            if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        }
        HapticKind.GESTURE_THRESHOLD_DEACTIVATE -> {
            if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        }
    }
}
