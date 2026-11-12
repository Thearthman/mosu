package com.mosu.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Test

class HapticsTest {
    private val testedApiLevels = listOf(26, 29, 30, 33, 34)

    @Test
    fun buttonAndLongPressUseStableConstantsOnEverySupportedApi() {
        testedApiLevels.forEach { sdkInt ->
            assertEquals(
                HapticFeedbackConstants.VIRTUAL_KEY,
                hapticConstantFor(HapticKind.BUTTON, sdkInt)
            )
            assertEquals(
                HapticFeedbackConstants.LONG_PRESS,
                hapticConstantFor(HapticKind.LONG_PRESS, sdkInt)
            )
        }
    }

    @Test
    fun confirmRejectAndGestureConstantsSwitchAtApi30() {
        testedApiLevels.forEach { sdkInt ->
            val expectsSemanticConstants = sdkInt >= Build.VERSION_CODES.R

            assertEquals(
                if (expectsSemanticConstants) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                },
                hapticConstantFor(HapticKind.CONFIRM, sdkInt)
            )
            assertEquals(
                if (expectsSemanticConstants) {
                    HapticFeedbackConstants.REJECT
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                },
                hapticConstantFor(HapticKind.REJECT, sdkInt)
            )
            assertEquals(
                if (expectsSemanticConstants) {
                    HapticFeedbackConstants.GESTURE_START
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                },
                hapticConstantFor(HapticKind.GESTURE_START, sdkInt)
            )
            assertEquals(
                if (expectsSemanticConstants) {
                    HapticFeedbackConstants.GESTURE_END
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                },
                hapticConstantFor(HapticKind.GESTURE_END, sdkInt)
            )
        }
    }

    @Test
    fun toggleAndGestureThresholdConstantsSwitchAtApi34() {
        testedApiLevels.forEach { sdkInt ->
            val expectsSemanticConstants = sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

            assertEquals(
                if (expectsSemanticConstants) {
                    HapticFeedbackConstants.TOGGLE_ON
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                },
                hapticConstantFor(HapticKind.TOGGLE_ON, sdkInt)
            )
            assertEquals(
                if (expectsSemanticConstants) {
                    HapticFeedbackConstants.TOGGLE_OFF
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                },
                hapticConstantFor(HapticKind.TOGGLE_OFF, sdkInt)
            )
            assertEquals(
                if (expectsSemanticConstants) {
                    HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                },
                hapticConstantFor(HapticKind.GESTURE_THRESHOLD_ACTIVATE, sdkInt)
            )
            assertEquals(
                if (expectsSemanticConstants) {
                    HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                },
                hapticConstantFor(HapticKind.GESTURE_THRESHOLD_DEACTIVATE, sdkInt)
            )
        }
    }

    @Test
    fun segmentTickUsesFrequentTickAtApi34AndClockTickBelow() {
        testedApiLevels.forEach { sdkInt ->
            assertEquals(
                if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                } else {
                    HapticFeedbackConstants.CLOCK_TICK
                },
                hapticConstantFor(HapticKind.SEGMENT_TICK, sdkInt)
            )
        }
    }
}
