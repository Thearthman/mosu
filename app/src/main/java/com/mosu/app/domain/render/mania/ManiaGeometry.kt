package com.mosu.app.domain.render.mania

import kotlin.math.min
import kotlin.math.max

data class ManiaViewportTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
) {
    fun mapX(x: Float): Float = offsetX + x * scale
    fun mapY(y: Float): Float = offsetY + y * scale
    fun mapLength(value: Float): Float = value * scale
}

data class ManiaColumnGeometry(
    val index: Int,
    val left: Float,
    val width: Float,
    val right: Float = left + width
)

data class ManiaPlayfieldGeometry(
    val left: Float,
    val right: Float,
    val columns: List<ManiaColumnGeometry>,
    val hitPosition: Float,
    val barlineHeight: Float
) {
    val width: Float get() = right - left
}

data class ManiaVerticalBounds(
    val top: Float,
    val bottom: Float
)

data class ManiaNotePieceGeometry(
    val top: Float,
    val bottom: Float,
    val verticallyMirrored: Boolean
) {
    val height: Float get() = bottom - top
}

/**
 * Legacy mania note pieces are scaled from their texture width. A non-square
 * texture therefore keeps its aspect ratio instead of being forced into a
 * square.
 */
fun maniaNotePieceHeight(
    referenceHeight: Float,
    textureWidth: Int,
    textureHeight: Int
): Float {
    val height = referenceHeight.coerceAtLeast(0f)
    if (textureWidth <= 0 || textureHeight <= 0) return height
    return height * textureHeight.toFloat() / textureWidth.toFloat()
}

/**
 * Notes are bottom-anchored at their timeline position. Hold tails use the
 * same anchor but draw a vertically mirrored texture.
 */
fun maniaNotePieceGeometry(
    y: Float,
    pieceHeight: Float,
    isTail: Boolean
): ManiaNotePieceGeometry {
    val height = pieceHeight.coerceAtLeast(0f)
    return ManiaNotePieceGeometry(
        top = y - height,
        bottom = y,
        verticallyMirrored = isTail
    )
}

/**
 * Hold bodies extend exactly between the centres of both endpoint icons.
 */
fun maniaHoldBodyBounds(
    headY: Float,
    tailY: Float,
    headHeight: Float,
    tailHeight: Float
): ManiaVerticalBounds {
    val headCenter = headY - headHeight.coerceAtLeast(0f) / 2f
    val tailCenter = tailY - tailHeight.coerceAtLeast(0f) / 2f
    return ManiaVerticalBounds(
        top = min(headCenter, tailCenter),
        bottom = max(headCenter, tailCenter)
    )
}

fun maniaViewportTransform(
    surfaceWidthPx: Float,
    surfaceHeightPx: Float,
    edgeInsetPx: Float = 0f
): ManiaViewportTransform {
    if (surfaceWidthPx <= 0f || surfaceHeightPx <= 0f) {
        return ManiaViewportTransform(0f, 0f, 0f)
    }
    val inset = edgeInsetPx.coerceAtLeast(0f)
    val availableWidth = (surfaceWidthPx - inset * 2f).coerceAtLeast(0f)
    val availableHeight = (surfaceHeightPx - inset * 2f).coerceAtLeast(0f)
    if (availableWidth <= 0f || availableHeight <= 0f) {
        return ManiaViewportTransform(0f, 0f, 0f)
    }
    val scale = min(
        availableWidth / ManiaRenderTimeline.MANIA_PLAYFIELD_WIDTH,
        availableHeight / ManiaRenderTimeline.MANIA_PLAYFIELD_HEIGHT
    )
    return ManiaViewportTransform(
        scale = scale,
        offsetX = (surfaceWidthPx - ManiaRenderTimeline.MANIA_PLAYFIELD_WIDTH * scale) / 2f,
        offsetY =
            (surfaceHeightPx - ManiaRenderTimeline.MANIA_PLAYFIELD_HEIGHT * scale) / 2f
    )
}

/**
 * Resolves the stage's horizontal layout. The stage is centred in the fixed
 * 1024px native canvas while all note/hold/barline drawing is clipped to the
 * returned playfield bounds.
 */
fun maniaPlayfieldGeometry(
    keyCount: Int,
    columnWidths: List<Float>,
    columnSpacings: List<Float>,
    hitPosition: Float,
    barlineHeight: Float
): ManiaPlayfieldGeometry {
    require(keyCount in 1..10) { "Mania preview supports 1..10 keys" }
    val widths = List(keyCount) { index ->
        columnWidths.getOrNull(index)?.coerceAtLeast(1f)
            ?: if (keyCount % 2 == 1 && index == keyCount / 2) 70f else 80f
    }
    val spacing = List((keyCount - 1).coerceAtLeast(0)) { index ->
        columnSpacings.getOrNull(index)?.coerceAtLeast(0f) ?: 1f
    }
    val stageWidth = widths.sum() + spacing.sum()
    val stageLeft = (ManiaRenderTimeline.MANIA_PLAYFIELD_WIDTH - stageWidth) / 2f
    var cursor = stageLeft
    val columns = widths.mapIndexed { index, width ->
        val column = ManiaColumnGeometry(
            index = index,
            left = cursor,
            width = width
        )
        cursor += width + (spacing.getOrNull(index) ?: 0f)
        column
    }
    return ManiaPlayfieldGeometry(
        left = stageLeft,
        right = stageLeft + stageWidth,
        columns = columns,
        hitPosition = hitPosition.coerceIn(0f, ManiaRenderTimeline.MANIA_PLAYFIELD_HEIGHT),
        barlineHeight = barlineHeight.coerceAtLeast(0f)
    )
}

fun maniaColumnFallbackSuffix(keyCount: Int, column: Int): String {
    val special = keyCount % 2 == 1 && column == keyCount / 2
    if (special) return "S"
    val distanceToEdge = min(column, keyCount - 1 - column)
    return if (distanceToEdge % 2 == 0) "1" else "2"
}
