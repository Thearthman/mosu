package com.mosu.app.domain.render

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** osu!standard's fixed playfield coordinate space. */
const val OSU_PLAYFIELD_WIDTH = 512f
const val OSU_PLAYFIELD_HEIGHT = 384f
private const val PLAYFIELD_OBJECT_OVERFLOW_MARGIN = 64f

/**
 * Uniform scale for fitting the 4:3 playfield inside a canvas with an optional
 * edge inset. Space for half an object is reserved around the 512x384
 * coordinate box so circles placed directly on an edge are not clipped by the
 * render canvas. The playfield remains centred by the caller.
 */
fun osuPlayfieldScale(
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    edgeInsetPx: Float = 0f
): Float {
    if (canvasWidthPx <= 0f || canvasHeightPx <= 0f) return 0f
    val inset = edgeInsetPx.coerceAtLeast(0f)
    val availableWidth = (canvasWidthPx - inset * 2f).coerceAtLeast(0f)
    val availableHeight = (canvasHeightPx - inset * 2f).coerceAtLeast(0f)
    if (availableWidth <= 0f || availableHeight <= 0f) return 0f
    return min(
        availableWidth /
            (OSU_PLAYFIELD_WIDTH + PLAYFIELD_OBJECT_OVERFLOW_MARGIN * 2f),
        availableHeight /
            (OSU_PLAYFIELD_HEIGHT + PLAYFIELD_OBJECT_OVERFLOW_MARGIN * 2f)
    )
}

data class RenderPoint(val x: Float, val y: Float) {
    operator fun plus(other: RenderPoint) = RenderPoint(x + other.x, y + other.y)
    operator fun minus(other: RenderPoint) = RenderPoint(x - other.x, y - other.y)
    operator fun times(scale: Float) = RenderPoint(x * scale, y * scale)

    fun distanceTo(other: RenderPoint): Float {
        val dx = x - other.x
        val dy = y - other.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    fun lerp(other: RenderPoint, amount: Float): RenderPoint =
        this + (other - this) * amount
}

enum class OsuCurveType { LINEAR, BEZIER, PERFECT_CIRCLE, CATMULL }

/**
 * One vertex in a legacy slider path.
 *
 * A non-null [curveType] starts a new explicit path segment. Null keeps the
 * vertex in the segment started by the previous typed vertex. This mirrors
 * lazer's `PathControlPoint`, including the fact that the first vertex of an
 * explicit segment is also the final vertex of the preceding segment.
 */
data class OsuPathControlPoint(
    val position: RenderPoint,
    val curveType: OsuCurveType? = null
)

/**
 * A normalized slider path. [distances] are cumulative and always begin at 0;
 * [length] is the gameplay length declared by the .osu file (or the sampled
 * geometry length when the declaration is missing/invalid).
 */
data class OsuSliderPath(
    val points: List<RenderPoint>,
    val distances: List<Float>,
    val length: Float
) {
    fun positionAtDistance(distance: Float): RenderPoint {
        if (points.isEmpty()) return RenderPoint(0f, 0f)
        if (points.size == 1 || length <= 0f) return points.first()

        val clamped = distance.coerceIn(0f, length)
        var low = 0
        var high = distances.lastIndex
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (distances[mid] <= clamped) low = mid else high = mid - 1
        }
        if (low >= points.lastIndex) return points.last()

        val segmentStart = distances[low]
        val segmentEnd = distances[low + 1]
        val segmentLength = segmentEnd - segmentStart
        if (segmentLength <= 0f) return points[low]
        return points[low].lerp(points[low + 1], (clamped - segmentStart) / segmentLength)
    }

    fun positionAtFraction(fraction: Float): RenderPoint =
        positionAtDistance(length * fraction.coerceIn(0f, 1f))

    fun tangentAtDistance(distance: Float): RenderPoint {
        if (points.size < 2) return RenderPoint(1f, 0f)
        val clamped = distance.coerceIn(0f, length)
        var low = 0
        var high = distances.lastIndex
        while (low < high) {
            val mid = (low + high) ushr 1
            if (distances[mid] < clamped) low = mid + 1 else high = mid
        }
        val index = low.coerceAtLeast(1)
        val a = points[index - 1]
        val b = points[index]
        val dx = b.x - a.x
        val dy = b.y - a.y
        val magnitude = kotlin.math.sqrt(dx * dx + dy * dy)
        return if (magnitude < 0.0001f) RenderPoint(1f, 0f) else RenderPoint(dx / magnitude, dy / magnitude)
    }
}

data class OsuTimingPoint(
    val timeMs: Double,
    val beatLengthMs: Double,
    val meter: Int,
    val uninherited: Boolean,
    /** Reciprocal inherited speed adjustment (see [sliderDurationMs]). */
    val bpmMultiplier: Double
)

sealed class OsuRenderObject {
    abstract val startTimeMs: Double
    abstract val endTimeMs: Double
    abstract val position: RenderPoint
    abstract val stackHeight: Int
    abstract val comboNumber: Int
    abstract val comboColourIndex: Int
    abstract val objectScale: Float
    abstract val preemptMs: Double
    abstract val fadeInMs: Double

    open val activeStartMs: Double
        get() = startTimeMs - preemptMs
    open val activeEndMs: Double
        get() = endTimeMs + OBJECT_FADE_OUT_MS

    val stackedPosition: RenderPoint
        get() = position + RenderPoint(
            -STACK_OFFSET * objectScale * stackHeight,
            -STACK_OFFSET * objectScale * stackHeight
        )

    /** Diameter of a hit circle in playfield coordinates. */
    val circleDiameter: Float
        get() = OSU_OBJECT_RADIUS * 2f * objectScale

    companion object {
        const val OBJECT_FADE_OUT_MS = 240.0
        const val OSU_OBJECT_RADIUS = 64f
        const val STACK_OFFSET = 6.4f
    }
}

data class OsuCircleObject(
    override val startTimeMs: Double,
    override val position: RenderPoint,
    override val stackHeight: Int,
    override val comboNumber: Int,
    override val comboColourIndex: Int,
    override val objectScale: Float,
    override val preemptMs: Double,
    override val fadeInMs: Double
) : OsuRenderObject() {
    override val endTimeMs: Double get() = startTimeMs
}

data class OsuSliderObject(
    override val startTimeMs: Double,
    override val endTimeMs: Double,
    override val position: RenderPoint,
    override val stackHeight: Int,
    override val comboNumber: Int,
    override val comboColourIndex: Int,
    override val objectScale: Float,
    override val preemptMs: Double,
    override val fadeInMs: Double,
    val path: OsuSliderPath,
    val spanCount: Int,
    val spanDurationMs: Double
) : OsuRenderObject() {
    val repeatCount: Int get() = (spanCount - 1).coerceAtLeast(0)

    /** The slider ball's gameplay path position at [timeMs], including repeats. */
    fun pathPositionAt(timeMs: Double): RenderPoint {
        if (spanCount <= 0 || spanDurationMs <= 0.0) return path.points.firstOrNull() ?: position
        val elapsed = (timeMs - startTimeMs).coerceIn(0.0, endTimeMs - startTimeMs)
        val rawSpan = elapsed / spanDurationMs
        val span = min(rawSpan.toInt(), spanCount - 1).coerceAtLeast(0)
        val spanProgress = (rawSpan - span).coerceIn(0.0, 1.0).toFloat()
        val forward = span % 2 == 0
        return path.positionAtFraction(if (forward) spanProgress else 1f - spanProgress)
    }
}

data class OsuSpinnerObject(
    override val startTimeMs: Double,
    override val endTimeMs: Double,
    override val stackHeight: Int,
    override val comboNumber: Int,
    override val comboColourIndex: Int,
    override val objectScale: Float,
    override val preemptMs: Double,
    override val fadeInMs: Double
) : OsuRenderObject() {
    override val position: RenderPoint get() = RenderPoint(OSU_PLAYFIELD_WIDTH / 2f, OSU_PLAYFIELD_HEIGHT / 2f)
}

/**
 * Immutable, fully sampled standard-mode timeline. Every visual value is a
 * function of [mapTimeMs]; there is no mutable playback state in this model.
 */
data class OsuRenderTimeline(
    val beatmapSetId: Long,
    val difficultyName: String,
    val audioFilename: String,
    val approachRate: Float,
    val overallDifficulty: Float,
    val circleSize: Float,
    val preemptMs: Double,
    val fadeInMs: Double,
    val sliderMultiplier: Float,
    val objects: List<OsuRenderObject>,
    val timingPoints: List<OsuTimingPoint>,
    val comboColours: List<Int>,
    val sliderTrackOverride: Int?,
    val sliderBorder: Int?
) {
    val durationMs: Double = objects.maxOfOrNull { it.endTimeMs } ?: 0.0
    @PublishedApi
    internal val maxActiveDurationMs: Double = objects.maxOfOrNull {
        it.activeEndMs - it.activeStartMs
    } ?: 0.0
    @PublishedApi
    internal val activeOrder: IntArray = objects.indices
        .sortedBy { objects[it].activeStartMs }
        .toIntArray()
    @PublishedApi
    internal val activeStarts: DoubleArray = DoubleArray(activeOrder.size) {
        objects[activeOrder[it]].activeStartMs
    }

    /**
     * Iterates only objects whose preempt/fade interval can be visible at
     * [timeMs]. No list is allocated on the frame path.
     */
    inline fun forEachActiveObject(timeMs: Double, block: (OsuRenderObject) -> Unit) {
        val ordered = activeOrder
        var low = 0
        var high = activeStarts.size - 1
        var candidate = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (activeStarts[mid] <= timeMs) {
                candidate = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        if (candidate < 0) return

        val earliestRelevantStart = timeMs - maxActiveDurationMs
        var firstRelevant = candidate
        while (firstRelevant > 0 && activeStarts[firstRelevant - 1] >= earliestRelevantStart) {
            firstRelevant--
        }
        var index = firstRelevant
        while (index <= candidate) {
            val obj = objects[ordered[index]]
            if (timeMs <= obj.activeEndMs) block(obj)
            index++
        }
    }
}

data class ReplayPosition(val x: Float, val y: Float)

data class ReplayFrame(
    val timeMs: Long,
    val position: ReplayPosition,
    val buttons: Int
)

/** Extension point only; v1.2 deliberately does not render replay input. */
fun interface ReplayInput {
    fun sampleAt(timeMs: Long): ReplayFrame?
}

/** AR → preempt, floored exactly as specified for v1.2. */
fun approachRateToPreemptMs(approachRate: Float): Double {
    val raw = when {
        approachRate < 5f -> 1800f - 120f * approachRate
        approachRate == 5f -> 1200f
        else -> 1950f - 150f * approachRate
    }
    return floor(raw.toDouble())
}

fun preemptToFadeInMs(preemptMs: Double): Double =
    400.0 * min(1.0, preemptMs / 450.0)

/**
 * osu!stable/lazer's legacy circle-size scale. A CS value of 5 gives an
 * object diameter of 64 playfield pixels once multiplied by the 128px base.
 */
fun circleScaleFromCircleSize(circleSize: Float): Float {
    val normalized = ((circleSize.coerceIn(0f, 10f) - 5f) / 5f)
    return (1f - 0.7f * normalized) / 2f * 1.00041f
}

/**
 * Slider velocity and duration use the v1.2 formula:
 * velocity = 100 * SliderMultiplier / (beatLength * bpmMultiplier).
 */
fun sliderDurationMs(
    sliderMultiplier: Float,
    beatLengthMs: Double,
    bpmMultiplier: Double,
    pathLength: Float,
    spanCount: Int
): Double {
    val denominator = beatLengthMs * bpmMultiplier
    val velocity = 100.0 * sliderMultiplier / denominator
    if (!velocity.isFinite() || velocity <= 0.0) return 0.0
    return spanCount.coerceAtLeast(1) * pathLength / velocity
}
