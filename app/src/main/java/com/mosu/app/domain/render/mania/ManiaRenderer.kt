package com.mosu.app.domain.render.mania

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.mosu.app.domain.render.RenderSurface
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a precomputed mania timeline. Every visual value is derived directly
 * from [mapTimeMs]; this class keeps only reusable paints, bounds and the
 * immutable column layout.
 */
class ManiaRenderer(private val skin: ManiaSkin) {
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dst = RectF()
    private val borderBounds = RectF()
    private val geometry: ManiaPlayfieldGeometry

    init {
        require(skin.columns.size == skin.keyCount) {
            "Mania skin must provide exactly one layout per column"
        }
        val spacings = List((skin.keyCount - 1).coerceAtLeast(0)) { index ->
            skin.columns[index].rightSpacing + skin.columns[index + 1].leftSpacing
        }
        geometry = maniaPlayfieldGeometry(
            keyCount = skin.keyCount,
            columnWidths = skin.columns.map { it.width },
            columnSpacings = spacings,
            hitPosition = skin.hitPositionFromBottom,
            barlineHeight = skin.barlineHeight
        )
    }

    fun close() {
        skin.close()
    }

    fun draw(
        canvas: Canvas,
        timeline: ManiaRenderTimeline,
        mapTimeMs: Double,
        surface: RenderSurface
    ) {
        val transform = maniaViewportTransform(
            surfaceWidthPx = surface.widthPx,
            surfaceHeightPx = surface.heightPx,
            edgeInsetPx = surface.edgeInsetPx
        )
        if (transform.scale <= 0f) return

        val hitTargetY = ManiaRenderTimeline.MANIA_PLAYFIELD_HEIGHT -
            geometry.hitPosition
        val timeRangeMs = timeline.timeRangeForHitPosition(geometry.hitPosition)

        canvas.save()
        canvas.clipRect(0f, 0f, surface.widthPx, surface.heightPx)
        canvas.translate(transform.offsetX, transform.offsetY)
        canvas.scale(transform.scale, transform.scale)

        drawStageBackground(canvas)
        drawStageSides(canvas)
        drawColumns(canvas)
        drawBarlines(canvas, timeline, mapTimeMs, timeRangeMs, hitTargetY)
        drawHitTarget(canvas, hitTargetY)

        if (skin.keysUnderNotes) {
            drawKeys(canvas)
        }
        drawHolds(canvas, timeline, mapTimeMs, timeRangeMs, hitTargetY)
        drawNotes(canvas, timeline, mapTimeMs, timeRangeMs, hitTargetY)
        if (!skin.keysUnderNotes) {
            drawKeys(canvas)
        }
        drawStageForeground(canvas)

        canvas.restore()

        if (surface.borderWidthPx > 0f) {
            drawSurfaceBorder(canvas, transform, surface.borderWidthPx)
        }
    }

    private fun drawStageBackground(canvas: Canvas) {
        setFill(Color.BLACK, 1f)
        canvas.drawRect(
            0f,
            0f,
            ManiaRenderTimeline.MANIA_PLAYFIELD_WIDTH,
            ManiaRenderTimeline.MANIA_PLAYFIELD_HEIGHT,
            fillPaint
        )
    }

    private fun drawStageSides(canvas: Canvas) {
        val stageHeight = ManiaRenderTimeline.MANIA_PLAYFIELD_HEIGHT
        skin.stageLeft?.let { bitmap ->
            drawHeightScaledBitmap(
                canvas = canvas,
                bitmap = bitmap,
                right = geometry.left + geometry.width * 0.05f,
                top = 0f,
                height = stageHeight
            )
        }
        skin.stageRight?.let { bitmap ->
            drawHeightScaledBitmap(
                canvas = canvas,
                bitmap = bitmap,
                left = geometry.right - geometry.width * 0.05f,
                top = 0f,
                height = stageHeight
            )
        }
    }

    private fun drawColumns(canvas: Canvas) {
        setFill(COLUMN_BACKGROUND_COLOUR, 1f)
        geometry.columns.forEach { column ->
            canvas.drawRect(column.left, 0f, column.right, STAGE_BOTTOM, fillPaint)
        }

        val lineColour = skin.columnLineColour ?: Color.WHITE
        val lineAlpha = if (skin.columnLineColour == null) 0.18f else 1f
        geometry.columns.forEach { column ->
            drawColumnLine(
                canvas = canvas,
                x = column.left,
                width = skin.columnLineWidths.getOrNull(column.index) ?: 0f,
                colour = lineColour,
                alpha = lineAlpha
            )
            drawColumnLine(
                canvas = canvas,
                x = column.right,
                width = skin.columnLineWidths.getOrNull(column.index + 1) ?: 0f,
                colour = lineColour,
                alpha = lineAlpha
            )
        }
    }

    private fun drawColumnLine(
        canvas: Canvas,
        x: Float,
        width: Float,
        colour: Int,
        alpha: Float
    ) {
        if (width <= 0f) return
        setFill(colour, alpha)
        canvas.drawRect(
            x - width / 2f,
            0f,
            x + width / 2f,
            STAGE_BOTTOM,
            fillPaint
        )
    }

    private fun drawBarlines(
        canvas: Canvas,
        timeline: ManiaRenderTimeline,
        mapTimeMs: Double,
        timeRangeMs: Double,
        hitTargetY: Float
    ) {
        if (timeline.barlines.isEmpty()) return
        val firstVisibleTime = mapTimeMs - timeRangeMs
        val lastVisibleTime = mapTimeMs + timeRangeMs
        var index = lowerBoundBarLine(timeline.barlines, firstVisibleTime)
        while (index < timeline.barlines.size) {
            val barline = timeline.barlines[index]
            if (barline.timeMs > lastVisibleTime) break
            val y = timeline.verticalPositionAt(
                timeMs = barline.timeMs,
                currentTimeMs = mapTimeMs,
                timeRangeMs = timeRangeMs,
                hitPosition = geometry.hitPosition
            )
            val height = if (barline.major) {
                max(skin.barlineHeight, MAJOR_BARLINE_HEIGHT)
            } else {
                skin.barlineHeight
            }
            if (y + height >= 0f && y - height <= hitTargetY) {
                val colour = skin.barlineColour ?: Color.WHITE
                val alpha = if (skin.barlineColour != null) {
                    1f
                } else if (barline.major) {
                    0.55f
                } else {
                    0.25f
                }
                setFill(colour, alpha)
                canvas.drawRect(
                    geometry.left,
                    y - height / 2f,
                    geometry.right,
                    y + height / 2f,
                    fillPaint
                )
            }
            index++
        }
    }

    private fun lowerBoundBarLine(
        barlines: List<ManiaBarLine>,
        timeMs: Double
    ): Int {
        var low = 0
        var high = barlines.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (barlines[mid].timeMs < timeMs) low = mid + 1 else high = mid
        }
        return low
    }

    private fun drawHitTarget(canvas: Canvas, hitTargetY: Float) {
        val hint = skin.stageHint
        if (hint != null) {
            val width = geometry.width
            val height = if (hint.width <= 0) 0f else {
                hint.height.toFloat() / hint.width.toFloat() * width
            }
            drawBitmap(
                canvas = canvas,
                bitmap = hint,
                left = geometry.left,
                top = hitTargetY - height / 2f,
                width = width,
                height = height
            )
        }

        if (!skin.showJudgementLine) return
        val colour = skin.judgementLineColour ?: Color.WHITE
        setFill(colour, 0.9f)
        canvas.drawRect(
            geometry.left,
            hitTargetY - JUDGEMENT_LINE_HEIGHT / 2f,
            geometry.right,
            hitTargetY + JUDGEMENT_LINE_HEIGHT / 2f,
            fillPaint
        )
    }

    private fun drawHolds(
        canvas: Canvas,
        timeline: ManiaRenderTimeline,
        mapTimeMs: Double,
        timeRangeMs: Double,
        hitTargetY: Float
    ) {
        canvas.save()
        canvas.clipRect(geometry.left, 0f, geometry.right, hitTargetY)

        timeline.notes.forEach { note ->
            if (!note.isHold) return@forEach
            val column = skin.columns.getOrNull(note.column) ?: return@forEach
            val headY = timeline.verticalPositionAt(
                timeMs = note.startTimeMs,
                currentTimeMs = mapTimeMs,
                timeRangeMs = timeRangeMs,
                hitPosition = geometry.hitPosition
            )
            val tailY = timeline.verticalPositionAt(
                timeMs = note.endTimeMs,
                currentTimeMs = mapTimeMs,
                timeRangeMs = timeRangeMs,
                hitPosition = geometry.hitPosition
            )
            val headHeight = notePieceHeight(
                column = column,
                bitmap = column.holdHead ?: column.note
            )
            val tailHeight = notePieceHeight(
                column = column,
                bitmap = column.holdTail ?: column.holdHead ?: column.note
            )
            val bodyBounds = maniaHoldBodyBounds(
                headY = headY,
                tailY = tailY,
                headHeight = headHeight,
                tailHeight = tailHeight
            )
            if (bodyBounds.bottom < 0f || bodyBounds.top > hitTargetY) return@forEach

            val body = column.holdBody
            if (body != null) {
                drawHoldBody(
                    canvas = canvas,
                    bitmap = body,
                    left = columnLeft(note.column),
                    width = column.width,
                    top = bodyBounds.top,
                    bottom = bodyBounds.bottom
                )
            } else {
                setFill(column.accentColour, 0.68f)
                canvas.drawRoundRect(
                    columnLeft(note.column),
                    bodyBounds.top,
                    columnLeft(note.column) + column.width,
                    bodyBounds.bottom,
                    column.width * 0.18f,
                    column.width * 0.18f,
                    fillPaint
                )
            }
        }
        canvas.restore()
    }

    private fun drawNotes(
        canvas: Canvas,
        timeline: ManiaRenderTimeline,
        mapTimeMs: Double,
        timeRangeMs: Double,
        hitTargetY: Float
    ) {
        canvas.save()
        canvas.clipRect(geometry.left, 0f, geometry.right, hitTargetY)

        // Tails occupy a lower layer so overlapping hold heads stay visible.
        timeline.notes.forEach { note ->
            if (!note.isHold) return@forEach
            val column = skin.columns.getOrNull(note.column) ?: return@forEach
            val tailY = timeline.verticalPositionAt(
                timeMs = note.endTimeMs,
                currentTimeMs = mapTimeMs,
                timeRangeMs = timeRangeMs,
                hitPosition = geometry.hitPosition
            )
            val bitmap = column.holdTail ?: column.holdHead ?: column.note
            drawNotePiece(
                canvas = canvas,
                column = column,
                columnIndex = note.column,
                y = tailY,
                bitmap = bitmap,
                pieceHeight = notePieceHeight(column, bitmap),
                tail = true,
                hitTargetY = hitTargetY
            )
        }

        timeline.notes.forEach { note ->
            val column = skin.columns.getOrNull(note.column) ?: return@forEach
            val headY = timeline.verticalPositionAt(
                timeMs = note.startTimeMs,
                currentTimeMs = mapTimeMs,
                timeRangeMs = timeRangeMs,
                hitPosition = geometry.hitPosition
            )
            val bitmap = if (note.isHold) {
                column.holdHead ?: column.note
            } else {
                column.note
            }
            drawNotePiece(
                canvas = canvas,
                column = column,
                columnIndex = note.column,
                y = headY,
                bitmap = bitmap,
                pieceHeight = notePieceHeight(column, bitmap),
                tail = false,
                hitTargetY = hitTargetY
            )
        }
        canvas.restore()
    }

    private fun drawNotePiece(
        canvas: Canvas,
        column: ManiaColumnSkin,
        columnIndex: Int,
        y: Float,
        bitmap: Bitmap?,
        pieceHeight: Float,
        tail: Boolean,
        hitTargetY: Float
    ) {
        val piece = maniaNotePieceGeometry(
            y = y,
            pieceHeight = pieceHeight,
            isTail = tail
        )
        if (piece.bottom < 0f || piece.top > hitTargetY) return

        if (bitmap != null) {
            drawBitmap(
                canvas = canvas,
                bitmap = bitmap,
                left = columnLeft(columnIndex),
                top = piece.top,
                width = column.width,
                height = piece.height,
                verticallyMirrored = piece.verticallyMirrored
            )
        } else {
            setFill(Color.WHITE, 1f)
            canvas.drawRoundRect(
                columnLeft(columnIndex),
                piece.top,
                columnLeft(columnIndex) + column.width,
                piece.bottom,
                min(piece.height / 2f, column.width / 2f),
                min(piece.height / 2f, column.width / 2f),
                fillPaint
            )
        }
    }

    private fun notePieceHeight(
        column: ManiaColumnSkin,
        bitmap: Bitmap?
    ): Float = maniaNotePieceHeight(
        referenceHeight = column.noteHeight,
        textureWidth = bitmap?.width ?: 0,
        textureHeight = bitmap?.height ?: 0
    )

    private fun drawHoldBody(
        canvas: Canvas,
        bitmap: Bitmap,
        left: Float,
        width: Float,
        top: Float,
        bottom: Float
    ) {
        val height = bottom - top
        if (height <= 0f || width <= 0f) return
        if (skin.noteBodyStyle == ManiaNoteBodyStyle.Stretch || bitmap.height <= 0) {
            drawBitmap(canvas, bitmap, left, top, width, height)
            return
        }

        val tileHeight = max(
            1f,
            bitmap.height.toFloat() / bitmap.width.toFloat() * width
        )
        var tileTop = when (skin.noteBodyStyle) {
            ManiaNoteBodyStyle.RepeatBottom -> {
                val remainder = height % tileHeight
                top + if (remainder == 0f) 0f else remainder - tileHeight
            }
            ManiaNoteBodyStyle.RepeatTop,
            ManiaNoteBodyStyle.RepeatTopAndBottom,
            ManiaNoteBodyStyle.Stretch -> top
        }
        while (tileTop < bottom) {
            val tileBottom = min(bottom, tileTop + tileHeight)
            if (tileBottom > top) {
                drawBitmap(
                    canvas = canvas,
                    bitmap = bitmap,
                    left = left,
                    top = tileTop,
                    width = width,
                    height = tileBottom - tileTop
                )
            }
            tileTop += tileHeight
        }

        if (skin.noteBodyStyle == ManiaNoteBodyStyle.RepeatTopAndBottom) {
            var bottomTileTop = bottom - tileHeight
            while (bottomTileTop > top) {
                drawBitmap(
                    canvas = canvas,
                    bitmap = bitmap,
                    left = left,
                    top = bottomTileTop,
                    width = width,
                    height = min(tileHeight, bottom - bottomTileTop)
                )
                bottomTileTop -= tileHeight
            }
        }
    }

    private fun drawKeys(canvas: Canvas) {
        skin.columns.forEachIndexed { index, column ->
            val key = column.keyUp ?: return@forEachIndexed
            val width = column.width
            val height = if (key.width <= 0) 0f else {
                key.height.toFloat() / key.width.toFloat() * width
            }
            drawBitmap(
                canvas = canvas,
                bitmap = key,
                left = columnLeft(index),
                top = STAGE_BOTTOM - height,
                width = width,
                height = height
            )
        }
    }

    private fun drawStageForeground(canvas: Canvas) {
        val bitmap = skin.stageBottom ?: return
        val width = bitmap.width * STAGE_BOTTOM_SCALE
        val height = bitmap.height * STAGE_BOTTOM_SCALE
        drawBitmap(
            canvas = canvas,
            bitmap = bitmap,
            left = (ManiaRenderTimeline.MANIA_PLAYFIELD_WIDTH - width) / 2f,
            top = STAGE_BOTTOM - height,
            width = width,
            height = height
        )
    }

    private fun columnLeft(columnIndex: Int): Float =
        geometry.columns.getOrNull(columnIndex)?.left ?: geometry.left

    private fun drawHeightScaledBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        top: Float,
        height: Float,
        left: Float? = null,
        right: Float? = null
    ) {
        if (bitmap.height <= 0 || height <= 0f) return
        val width = bitmap.width.toFloat() / bitmap.height.toFloat() * height
        val drawLeft = when {
            left != null -> left
            right != null -> right - width
            else -> 0f
        }
        drawBitmap(canvas, bitmap, drawLeft, top, width, height)
    }

    private fun drawBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        verticallyMirrored: Boolean = false
    ) {
        if (
            bitmap.isRecycled ||
            width <= 0f ||
            height <= 0f
        ) {
            return
        }
        imagePaint.alpha = 255
        imagePaint.colorFilter = null
        if (verticallyMirrored) {
            canvas.save()
            canvas.translate(left, top + height)
            canvas.scale(1f, -1f)
            dst.set(0f, 0f, width, height)
            canvas.drawBitmap(bitmap, null, dst, imagePaint)
            canvas.restore()
            return
        }
        dst.set(left, top, left + width, top + height)
        canvas.drawBitmap(bitmap, null, dst, imagePaint)
    }

    private fun drawSurfaceBorder(
        canvas: Canvas,
        transform: ManiaViewportTransform,
        borderWidthPx: Float
    ) {
        val halfBorder = borderWidthPx / 2f
        borderBounds.set(
            transform.offsetX + halfBorder,
            transform.offsetY + halfBorder,
            transform.offsetX +
                ManiaRenderTimeline.MANIA_PLAYFIELD_WIDTH * transform.scale -
                halfBorder,
            transform.offsetY +
                ManiaRenderTimeline.MANIA_PLAYFIELD_HEIGHT * transform.scale -
                halfBorder
        )
        strokePaint.color = Color.WHITE
        strokePaint.alpha = 33
        strokePaint.strokeWidth = borderWidthPx
        canvas.drawRect(borderBounds, strokePaint)
    }

    private fun setFill(colour: Int, alpha: Float) {
        fillPaint.color = colour
        fillPaint.alpha = (255f * alpha).toInt().coerceIn(0, 255)
    }

    private companion object {
        const val STAGE_BOTTOM = ManiaRenderTimeline.MANIA_PLAYFIELD_HEIGHT
        const val COLUMN_BACKGROUND_COLOUR = 0xFF101010.toInt()
        const val MAJOR_BARLINE_HEIGHT = 1.7f
        const val JUDGEMENT_LINE_HEIGHT = 1f
        const val STAGE_BOTTOM_SCALE = 1.6f
    }
}
