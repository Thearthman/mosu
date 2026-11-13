package com.mosu.app.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val LANDSCAPE_EXPANSION_DRAG_GAIN = 1.2f
private const val LANDSCAPE_TRANSITION_DURATION_MILLIS = 300
private val CompactPlayerSnapSpec = spring<Float>(
    dampingRatio = 0.70f,
    stiffness = Spring.StiffnessMedium
)

/**
 * Owns the landscape player's two visual stages and every transition between them.
 *
 * Gesture state, detent catch-up, haptic arbitration, and direct two-stage animations live
 * together so a new pointer interaction or transition can replace the previous one as a
 * unit. In particular, a settle coroutine can never end a newer gesture's haptic session.
 */
internal class LandscapePlayerGestureCoordinator(
    private val scope: CoroutineScope,
    private val collapsedOffsetPx: Float,
    private val expansionDistancePx: Float,
    private val miniPlayerWidthPx: Float,
    initiallyFullyOpen: Boolean,
    private val onFullPlayerOpenChanged: (Boolean) -> Unit,
    private val onDetentHaptic: () -> Unit
) {
    val sheetOffset = Animatable(
        if (initiallyFullyOpen) 0f else collapsedOffsetPx
    )
    val expansionProgress = Animatable(
        if (initiallyFullyOpen) 1f else 0f
    )

    var isDragging by mutableStateOf(false)
        private set

    val isOpen: Boolean
        get() = sheetOffset.value < collapsedOffsetPx

    val isFullyOpen: Boolean
        get() = sheetOffset.value <= 0f

    val isCatchUpActive: Boolean
        get() = compactCatchUpActive || expansionCatchUpActive

    private val compactAnimationHapticTracker =
        PlayerCriticalPointHapticTracker()
    private val expansionAnimationHapticTracker =
        PlayerCriticalPointHapticTracker()
    private val compactDragHapticTracker =
        PlayerCriticalPointHapticTracker()
    private val expansionDragHapticTracker =
        PlayerCriticalPointHapticTracker()
    private val gestureHapticArbiter =
        LandscapePlayerGestureHapticArbiter()
    private val dragVelocityTracker = LandscapeDragVelocityTracker()

    private var compactGestureDirection = PlayerGestureDirection.EXPANDING
    private var expansionGestureDirection = PlayerGestureDirection.EXPANDING
    private var dragState: LandscapePlayerDragState? = null
    private var gestureStartStage = LandscapePlayerStage.MINI
    private var compactAnimationHapticSession = 0
    private var expansionAnimationHapticSession = 0
    private var compactDragHapticSession = 0
    private var expansionDragHapticSession = 0
    private var gestureHapticSession = 0
    private var compactCatchUpActive by mutableStateOf(false)
    private var expansionCatchUpActive by mutableStateOf(false)
    private var transitionJob: Job? = null
    private var dragSession = 0
    private var reportedFullPlayerOpen = initiallyFullyOpen

    internal suspend fun observeAnimationBoundaries() = coroutineScope {
        launch {
            snapshotFlow {
                playerProgressFromSheetOffset(
                    sheetOffset.value,
                    collapsedOffsetPx
                )
            }.collect { progress ->
                val crossing = compactAnimationHapticTracker
                    .observeCrossing(progress)
                if (crossing != null) {
                    val emitHaptic =
                        crossing.hapticArbiterSession?.let { session ->
                            gestureHapticArbiter.onAnimationBoundaryCrossed(
                                session,
                                LandscapeDetentBoundary.COMPACT
                            )
                        } ?: true
                    if (emitHaptic) {
                        onDetentHaptic()
                    }
                }
            }
        }
        launch {
            snapshotFlow { expansionProgress.value }
                .collect { progress ->
                    val crossing = expansionAnimationHapticTracker
                        .observeCrossing(progress)
                    if (crossing != null) {
                        val emitHaptic =
                            crossing.hapticArbiterSession?.let { session ->
                                gestureHapticArbiter
                                    .onAnimationBoundaryCrossed(
                                        session,
                                        LandscapeDetentBoundary.EXPANSION
                                    )
                            } ?: true
                        if (emitHaptic) {
                            onDetentHaptic()
                        }
                    }
                }
        }
    }

    internal suspend fun runCatchUp() {
        var lastFrameNanos = withFrameNanos { it }
        while (isCatchUpActive) {
            val frameNanos = withFrameNanos { it }
            val elapsedSeconds = (
                (frameNanos - lastFrameNanos) / 1_000_000_000f
                ).coerceIn(0f, 0.05f)
            lastFrameNanos = frameNanos
            val state = dragState
            if (state == null) {
                stopCatchUp()
                break
            }

            val compactProgress = playerProgressFromSheetOffset(
                sheetOffset.value,
                collapsedOffsetPx
            )
            val currentExpansionProgress = expansionProgress.value
            val nextCompactProgress = if (compactCatchUpActive) {
                advancePlayerDetentCatchUp(
                    progress = compactProgress,
                    targetProgress = landscapePlayerCatchUpTarget(
                        rawProgress = state.compactRawProgress,
                        direction = compactGestureDirection,
                        stageDistancePx = collapsedOffsetPx,
                        miniPlayerWidthPx = miniPlayerWidthPx
                    ),
                    elapsedSeconds = elapsedSeconds,
                    catchUpSpeed = PLAYER_LANDSCAPE_DETENT_CATCH_UP_SPEED
                )
            } else {
                compactProgress
            }
            val nextExpansionProgress = if (expansionCatchUpActive) {
                advancePlayerDetentCatchUp(
                    progress = currentExpansionProgress,
                    targetProgress = landscapePlayerCatchUpTarget(
                        rawProgress = state.expansionRawProgress,
                        direction = expansionGestureDirection,
                        stageDistancePx = expansionDistancePx,
                        miniPlayerWidthPx = miniPlayerWidthPx
                    ),
                    elapsedSeconds = elapsedSeconds,
                    catchUpSpeed = PLAYER_LANDSCAPE_DETENT_CATCH_UP_SPEED
                )
            } else {
                currentExpansionProgress
            }
            if (
                nextCompactProgress != compactProgress ||
                nextExpansionProgress != currentExpansionProgress
            ) {
                val nextPosition = LandscapePlayerDragPosition(
                    sheetOffsetPx = playerSheetOffsetFromProgress(
                        nextCompactProgress,
                        collapsedOffsetPx
                    ),
                    expansionProgress = nextExpansionProgress
                )
                dragState = state.copy(position = nextPosition)
                sheetOffset.snapTo(nextPosition.sheetOffsetPx)
                expansionProgress.snapTo(nextPosition.expansionProgress)
            }
        }
    }

    fun startDrag() {
        if (isDragging) return
        replaceTransition(null)
        compactAnimationHapticTracker.end(compactAnimationHapticSession)
        expansionAnimationHapticTracker.end(expansionAnimationHapticSession)
        isDragging = true
        dragSession += 1
        compactCatchUpActive = false
        expansionCatchUpActive = false

        val compactProgress = playerProgressFromSheetOffset(
            sheetOffset.value,
            collapsedOffsetPx
        )
        val currentExpansionProgress = expansionProgress.value
        dragState = LandscapePlayerDragState.at(
            position = LandscapePlayerDragPosition(
                sheetOffsetPx = sheetOffset.value,
                expansionProgress = currentExpansionProgress
            ),
            collapsedOffsetPx = collapsedOffsetPx
        )
        compactGestureDirection =
            playerGestureDirectionForStart(compactProgress)
        expansionGestureDirection =
            playerGestureDirectionForStart(currentExpansionProgress)
        gestureStartStage = when {
            currentExpansionProgress > 0f -> LandscapePlayerStage.FULL
            compactProgress > 0f -> LandscapePlayerStage.COMPACT
            else -> LandscapePlayerStage.MINI
        }
        gestureHapticSession = gestureHapticArbiter.begin()
        dragVelocityTracker.begin()
        compactDragHapticSession = compactDragHapticTracker.begin(
            progress = compactProgress,
            criticalPoint = playerGestureCriticalPoint(
                compactGestureDirection,
                PLAYER_LANDSCAPE_CRITICAL_EDGE_FRACTION
            )
        )
        expansionDragHapticSession = expansionDragHapticTracker.begin(
            progress = currentExpansionProgress,
            criticalPoint = playerGestureCriticalPoint(
                expansionGestureDirection,
                PLAYER_LANDSCAPE_CRITICAL_EDGE_FRACTION
            )
        )
    }

    fun onDragDelta(delta: Float) {
        if (!isDragging) return
        val interactionSession = dragSession
        dragVelocityTracker.recordDelta(delta)
        val velocityPxPerSecond =
            dragVelocityTracker.velocityPxPerSecond()
        val gestureSession = gestureHapticSession
        val compactSession = compactDragHapticSession
        val expansionSession = expansionDragHapticSession
        scope.launch {
            if (!isDragging || interactionSession != dragSession) {
                return@launch
            }
            val currentPosition = LandscapePlayerDragPosition(
                sheetOffsetPx = sheetOffset.value,
                expansionProgress = expansionProgress.value
            )
            val next = advanceLandscapePlayerDragState(
                state = dragState ?: LandscapePlayerDragState.at(
                    currentPosition,
                    collapsedOffsetPx
                ),
                deltaPx = delta,
                collapsedOffsetPx = collapsedOffsetPx,
                expansionDistancePx = expansionDistancePx,
                expansionGain = LANDSCAPE_EXPANSION_DRAG_GAIN,
                criticalEdgeFraction =
                    PLAYER_LANDSCAPE_CRITICAL_EDGE_FRACTION,
                compactDirection = compactGestureDirection,
                expansionDirection = expansionGestureDirection,
                compactCatchUpActive = compactCatchUpActive,
                expansionCatchUpActive = expansionCatchUpActive
            )
            dragState = next
            val compactBoundaryCrossed = compactDragHapticTracker.observe(
                next.compactRawProgress,
                compactSession
            )
            val expansionBoundaryCrossed = expansionDragHapticTracker.observe(
                next.expansionRawProgress,
                expansionSession
            )

            fun handleBoundary(boundary: LandscapeDetentBoundary) {
                if (
                    gestureHapticArbiter.onLiveBoundaryCrossed(
                        gestureSession,
                        boundary,
                        velocityPxPerSecond
                    )
                ) {
                    onDetentHaptic()
                }
            }

            if (delta < 0f) {
                if (compactBoundaryCrossed) {
                    compactCatchUpActive = true
                    handleBoundary(LandscapeDetentBoundary.COMPACT)
                }
                if (expansionBoundaryCrossed) {
                    expansionCatchUpActive = true
                    handleBoundary(LandscapeDetentBoundary.EXPANSION)
                }
            } else {
                if (expansionBoundaryCrossed) {
                    expansionCatchUpActive = true
                    handleBoundary(LandscapeDetentBoundary.EXPANSION)
                }
                if (compactBoundaryCrossed) {
                    compactCatchUpActive = true
                    handleBoundary(LandscapeDetentBoundary.COMPACT)
                }
            }
            sheetOffset.snapTo(next.position.sheetOffsetPx)
            expansionProgress.snapTo(next.position.expansionProgress)
        }
    }

    fun settle(velocity: Float) {
        if (!isDragging) return
        endDragTracking()

        val gestureSession = gestureHapticSession
        replaceTransition {
            try {
                val currentExpansionProgress = expansionProgress.value
                val currentSheetOffset = sheetOffset.value
                val rawCompactProgress = dragState
                    ?.compactRawProgress
                    ?: playerProgressFromSheetOffset(
                        currentSheetOffset,
                        collapsedOffsetPx
                    )
                val rawExpansionProgress = dragState
                    ?.expansionRawProgress
                    ?: currentExpansionProgress
                val release = resolveLandscapePlayerRelease(
                    startStage = gestureStartStage,
                    compactRawProgress = rawCompactProgress,
                    expansionRawProgress = rawExpansionProgress,
                    velocityPxPerSecond = velocity
                )
                gestureHapticArbiter.prepareForSettle(
                    gestureSession,
                    limitDirectTransitionToSingleTick =
                        release.spansBothStages &&
                            abs(velocity) >=
                            PLAYER_FULL_FLICK_VELOCITY_PX_PER_SECOND
                )
                setFullPlayerOpen(
                    release.target == LandscapePlayerStage.FULL
                )

                when {
                    release.target == LandscapePlayerStage.FULL -> {
                        animateBothStages(
                            sheetTargetOffsetPx = 0f,
                            expansionTargetProgress = 1f,
                            compactDirection = compactGestureDirection,
                            expansionDirection = expansionGestureDirection,
                            hapticArbiterSession = gestureSession
                        )
                    }
                    release.spansBothStages -> {
                        // A full flick traverses both stages in one motion instead of
                        // travelling to compact, pausing, and then continuing.
                        animateBothStages(
                            sheetTargetOffsetPx = collapsedOffsetPx,
                            expansionTargetProgress = 0f,
                            compactDirection = compactGestureDirection,
                            expansionDirection = expansionGestureDirection,
                            hapticArbiterSession = gestureSession
                        )
                    }
                    else -> {
                        if (
                            currentExpansionProgress > 0f ||
                            rawExpansionProgress > 0f
                        ) {
                            animateExpansionTo(
                                targetProgress = 0f,
                                animationSpec = CompactPlayerSnapSpec,
                                direction = expansionGestureDirection,
                                hapticArbiterSession = gestureSession
                            )
                        }
                        val targetOffset = if (
                            release.target == LandscapePlayerStage.COMPACT
                        ) {
                            0f
                        } else {
                            collapsedOffsetPx
                        }
                        animateSheetTo(
                            targetOffset = targetOffset,
                            animationSpec = if (targetOffset == 0f) {
                                CompactPlayerSnapSpec
                            } else {
                                tween(LANDSCAPE_TRANSITION_DURATION_MILLIS)
                            },
                            direction = compactGestureDirection,
                            hapticArbiterSession = gestureSession
                        )
                    }
                }
            } finally {
                gestureHapticArbiter.end(gestureSession)
            }
        }
    }

    fun expandToFull() {
        endActiveGestureBeforeTransition()
        setFullPlayerOpen(true)
        replaceTransition {
            animateBothStages(
                sheetTargetOffsetPx = 0f,
                expansionTargetProgress = 1f,
                compactDirection = PlayerGestureDirection.EXPANDING,
                expansionDirection = PlayerGestureDirection.EXPANDING
            )
        }
    }

    fun expandToCompact() {
        endActiveGestureBeforeTransition()
        setFullPlayerOpen(false)
        replaceTransition {
            animateSheetTo(
                targetOffset = 0f,
                animationSpec = CompactPlayerSnapSpec,
                direction = PlayerGestureDirection.EXPANDING
            )
        }
    }

    fun collapseOneStage() {
        endActiveGestureBeforeTransition()
        setFullPlayerOpen(false)
        replaceTransition {
            if (expansionProgress.value > 0f) {
                animateExpansionTo(
                    targetProgress = 0f,
                    animationSpec = CompactPlayerSnapSpec,
                    direction = PlayerGestureDirection.COLLAPSING
                )
            } else {
                animateSheetTo(
                    targetOffset = collapsedOffsetPx,
                    animationSpec =
                        tween(LANDSCAPE_TRANSITION_DURATION_MILLIS),
                    direction = PlayerGestureDirection.COLLAPSING
                )
            }
        }
    }

    fun collapseAfterPlaybackEnded(emitHaptic: Boolean) {
        endActiveGestureBeforeTransition()
        setFullPlayerOpen(false)
        replaceTransition {
            if (expansionProgress.value > 0f) {
                animateExpansionTo(
                    targetProgress = 0f,
                    animationSpec = CompactPlayerSnapSpec,
                    direction = PlayerGestureDirection.COLLAPSING,
                    emitHaptic = emitHaptic
                )
            }
            animateSheetTo(
                targetOffset = collapsedOffsetPx,
                animationSpec = tween(LANDSCAPE_TRANSITION_DURATION_MILLIS),
                direction = PlayerGestureDirection.COLLAPSING,
                emitHaptic = emitHaptic
            )
        }
    }

    fun dispose() {
        endDragTracking()
        compactAnimationHapticTracker.end(compactAnimationHapticSession)
        expansionAnimationHapticTracker.end(expansionAnimationHapticSession)
        gestureHapticArbiter.end(gestureHapticSession)
        dragState = null
        transitionJob?.cancel()
        transitionJob = null
    }

    private suspend fun animateBothStages(
        sheetTargetOffsetPx: Float,
        expansionTargetProgress: Float,
        compactDirection: PlayerGestureDirection,
        expansionDirection: PlayerGestureDirection,
        hapticArbiterSession: Int? = null
    ) = coroutineScope {
        launch {
            animateSheetTo(
                targetOffset = sheetTargetOffsetPx,
                animationSpec =
                    tween(LANDSCAPE_TRANSITION_DURATION_MILLIS),
                direction = compactDirection,
                hapticArbiterSession = hapticArbiterSession
            )
        }
        launch {
            animateExpansionTo(
                targetProgress = expansionTargetProgress,
                animationSpec =
                    tween(LANDSCAPE_TRANSITION_DURATION_MILLIS),
                direction = expansionDirection,
                hapticArbiterSession = hapticArbiterSession
            )
        }
    }

    private suspend fun animateSheetTo(
        targetOffset: Float,
        animationSpec: AnimationSpec<Float>,
        direction: PlayerGestureDirection,
        emitHaptic: Boolean = true,
        hapticArbiterSession: Int? = null
    ) {
        val startProgress = playerProgressFromSheetOffset(
            sheetOffset.value,
            collapsedOffsetPx
        )
        val session = if (emitHaptic) {
            compactAnimationHapticTracker.begin(
                progress = startProgress,
                criticalPoint = playerGestureCriticalPoint(
                    direction,
                    PLAYER_LANDSCAPE_CRITICAL_EDGE_FRACTION
                ),
                hapticArbiterSession = hapticArbiterSession
            )
        } else {
            null
        }
        if (session != null) {
            compactAnimationHapticSession = session
        }
        try {
            sheetOffset.animateTo(
                targetOffset,
                animationSpec = animationSpec
            )
        } finally {
            if (session != null) {
                compactAnimationHapticTracker.end(session)
            }
        }
    }

    private suspend fun animateExpansionTo(
        targetProgress: Float,
        animationSpec: AnimationSpec<Float>,
        direction: PlayerGestureDirection,
        emitHaptic: Boolean = true,
        hapticArbiterSession: Int? = null
    ) {
        val startProgress = expansionProgress.value
        val session = if (emitHaptic) {
            expansionAnimationHapticTracker.begin(
                progress = startProgress,
                criticalPoint = playerGestureCriticalPoint(
                    direction,
                    PLAYER_LANDSCAPE_CRITICAL_EDGE_FRACTION
                ),
                hapticArbiterSession = hapticArbiterSession
            )
        } else {
            null
        }
        if (session != null) {
            expansionAnimationHapticSession = session
        }
        try {
            expansionProgress.animateTo(
                targetProgress,
                animationSpec = animationSpec
            )
        } finally {
            if (session != null) {
                expansionAnimationHapticTracker.end(session)
            }
        }
    }

    private fun stopCatchUp() {
        compactCatchUpActive = false
        expansionCatchUpActive = false
    }

    private fun endDragTracking() {
        isDragging = false
        dragSession += 1
        stopCatchUp()
        compactDragHapticTracker.end(compactDragHapticSession)
        expansionDragHapticTracker.end(expansionDragHapticSession)
        dragVelocityTracker.end()
    }

    private fun endActiveGestureBeforeTransition() {
        if (!isDragging) return
        endDragTracking()
        gestureHapticArbiter.end(gestureHapticSession)
    }

    private fun replaceTransition(block: (suspend () -> Unit)?) {
        transitionJob?.cancel()
        transitionJob = block?.let { transition ->
            scope.launch { transition() }
        }
    }

    private fun setFullPlayerOpen(open: Boolean) {
        if (reportedFullPlayerOpen == open) return
        reportedFullPlayerOpen = open
        onFullPlayerOpenChanged(open)
    }
}

/**
 * Remembers the landscape gesture coordinator and keeps its animation observers bound to
 * exactly one coordinator instance. Callback and haptic lambdas are updated without
 * recreating the coordinator or restarting its in-flight transitions.
 */
@Composable
internal fun rememberLandscapePlayerGestureCoordinator(
    isLandscape: Boolean,
    initiallyFullyOpen: Boolean,
    collapsedOffsetPx: Float,
    expansionDistancePx: Float,
    miniPlayerWidthPx: Float,
    onFullPlayerOpenChanged: (Boolean) -> Unit,
    onDetentHaptic: () -> Unit
): LandscapePlayerGestureCoordinator {
    val scope = rememberCoroutineScope()
    val currentOnFullPlayerOpenChanged by rememberUpdatedState(
        onFullPlayerOpenChanged
    )
    val currentOnDetentHaptic by rememberUpdatedState(onDetentHaptic)
    val coordinator = remember(
        isLandscape,
        collapsedOffsetPx,
        expansionDistancePx,
        miniPlayerWidthPx
    ) {
        LandscapePlayerGestureCoordinator(
            scope = scope,
            collapsedOffsetPx = collapsedOffsetPx,
            expansionDistancePx = expansionDistancePx,
            miniPlayerWidthPx = miniPlayerWidthPx,
            initiallyFullyOpen = isLandscape && initiallyFullyOpen,
            onFullPlayerOpenChanged = {
                currentOnFullPlayerOpenChanged(it)
            },
            onDetentHaptic = { currentOnDetentHaptic() }
        )
    }

    LaunchedEffect(coordinator) {
        coordinator.observeAnimationBoundaries()
    }
    LaunchedEffect(coordinator) {
        snapshotFlow { coordinator.isCatchUpActive }
            .collect { active ->
                if (active) {
                    coordinator.runCatchUp()
                }
            }
    }
    DisposableEffect(coordinator) {
        onDispose { coordinator.dispose() }
    }
    return coordinator
}
