package com.mosu.app.domain.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

data class OsuPlayfieldSize(val widthPx: Float, val heightPx: Float)

/**
 * Draws a fully precomputed timeline. All rendered values are direct functions
 * of [mapTimeMs]; the renderer stores only reusable paints and scratch bounds.
 * [replayInput] is an intentionally ignored v1.2 extension point.
 */
class OsuRenderer(private val skin: OsuSkin) {
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sliderBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val sliderBodyMesh = OsuSliderBodyMesh()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val dst = RectF()
    private val arcBounds = RectF()
    private val tintFilters = HashMap<Int, PorterDuffColorFilter>()
    private val comboDigits = IntArray(12)
    private val comboDigitBitmaps = arrayOfNulls<Bitmap>(12)
    private val comboDigitWidths = FloatArray(12)
    private val comboText = StringBuilder(12)

    @Suppress("UNUSED_PARAMETER")
    fun draw(
        canvas: Canvas,
        timeline: OsuRenderTimeline,
        mapTimeMs: Double,
        playfieldSize: OsuPlayfieldSize,
        replayInput: ReplayInput? = null,
        edgeInsetPx: Float = 0f,
        borderWidthPx: Float = 0f
    ) {
        if (playfieldSize.widthPx <= 0f || playfieldSize.heightPx <= 0f) return
        val scale = osuPlayfieldScale(
            playfieldSize.widthPx,
            playfieldSize.heightPx,
            edgeInsetPx
        )
        if (scale <= 0f) return
        val offsetX = (playfieldSize.widthPx - OSU_PLAYFIELD_WIDTH * scale) / 2f
        val offsetY = (playfieldSize.heightPx - OSU_PLAYFIELD_HEIGHT * scale) / 2f

        canvas.save()
        canvas.clipRect(0f, 0f, playfieldSize.widthPx, playfieldSize.heightPx)
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        timeline.forEachActiveObject(mapTimeMs) { obj ->
            when (obj) {
                is OsuCircleObject -> drawCircle(canvas, timeline, obj, mapTimeMs)
                is OsuSliderObject -> drawSlider(canvas, timeline, obj, mapTimeMs)
                is OsuSpinnerObject -> drawSpinner(canvas, timeline, obj, mapTimeMs)
            }
        }
        canvas.restore()

        if (borderWidthPx > 0f) {
            drawPlayfieldBorder(canvas, playfieldSize, scale, borderWidthPx)
        }
    }

    private fun drawCircle(
        canvas: Canvas,
        timeline: OsuRenderTimeline,
        obj: OsuCircleObject,
        time: Double
    ) {
        val fadeIn = fadeInAlpha(obj, time)
        if (fadeIn <= 0f) return
        val centre = obj.stackedPosition
        val colour = comboColour(timeline, obj.comboColourIndex)
        val postHit = postHitAlpha(obj.startTimeMs, time)

        if (postHit > 0f) {
            drawHitCircle(
                canvas = canvas,
                centre = centre,
                objectScale = obj.objectScale,
                sizeMultiplier = postHitScale(obj.startTimeMs, time),
                comboNumber = obj.comboNumber,
                colour = colour,
                alpha = fadeIn * postHit
            )
        }
        if (time < obj.startTimeMs) {
            drawApproachCircle(
                canvas = canvas,
                centre = centre,
                objectScale = obj.objectScale,
                approachProgress = ((time - obj.activeStartMs) / obj.preemptMs)
                    .coerceIn(0.0, 1.0).toFloat(),
                colour = colour,
                alpha = fadeIn
            )
        }
    }

    private fun drawSlider(
        canvas: Canvas,
        timeline: OsuRenderTimeline,
        obj: OsuSliderObject,
        time: Double
    ) {
        val fadeIn = fadeInAlpha(obj, time)
        if (fadeIn <= 0f) return
        val afterEnd = ((time - obj.endTimeMs) / OsuRenderObject.OBJECT_FADE_OUT_MS)
            .coerceIn(0.0, 1.0).toFloat()
        val trackAlpha = fadeIn * (1f - afterEnd)
        if (trackAlpha > 0f) {
            val snakeProgress = ((time - obj.activeStartMs) / (obj.preemptMs / 3.0))
                .coerceIn(0.0, 1.0).toFloat()
            val colour = comboColour(timeline, obj.comboColourIndex)
            val trackColour = timeline.sliderTrackOverride
                ?: skin.sliderTrackOverride
                ?: colour
            val borderColour = timeline.sliderBorder
                ?: skin.sliderBorder
                ?: colour
            drawSliderTrack(
                canvas = canvas,
                slider = obj,
                trackedLength = obj.path.length * snakeProgress,
                trackColour = trackColour,
                borderColour = borderColour,
                alpha = trackAlpha
            )
            drawSliderTail(canvas, obj, colour, trackAlpha)
            drawRepeatArrows(canvas, obj, colour, trackAlpha)
        }

        val headAlpha = fadeIn * postHitAlpha(obj.startTimeMs, time)
        if (headAlpha > 0f) {
            drawSliderHead(
                canvas = canvas,
                centre = obj.stackedPosition,
                objectScale = obj.objectScale,
                sizeMultiplier = postHitScale(obj.startTimeMs, time),
                comboNumber = obj.comboNumber,
                colour = comboColour(timeline, obj.comboColourIndex),
                alpha = headAlpha
            )
        }
        if (time < obj.startTimeMs) {
            drawApproachCircle(
                canvas = canvas,
                centre = obj.stackedPosition,
                objectScale = obj.objectScale,
                approachProgress = ((time - obj.activeStartMs) / obj.preemptMs)
                    .coerceIn(0.0, 1.0).toFloat(),
                colour = comboColour(timeline, obj.comboColourIndex),
                alpha = fadeIn
            )
        }

        if (time in obj.startTimeMs..obj.endTimeMs) {
            val ballCentre = obj.stackedPosition +
                (obj.pathPositionAt(time) - obj.position)
            drawSliderBall(
                canvas = canvas,
                centre = ballCentre,
                objectScale = obj.objectScale,
                fallbackDiameter = obj.circleDiameter,
                colour = comboColour(timeline, obj.comboColourIndex),
                alpha = trackAlpha
            )
        }
    }

    private fun drawSpinner(
        canvas: Canvas,
        timeline: OsuRenderTimeline,
        obj: OsuSpinnerObject,
        time: Double
    ) {
        val fadeIn = fadeInAlpha(obj, time)
        if (fadeIn <= 0f) return
        val afterEnd = ((time - obj.endTimeMs) / OsuRenderObject.OBJECT_FADE_OUT_MS)
            .coerceIn(0.0, 1.0).toFloat()
        val alpha = fadeIn * (1f - afterEnd)
        if (alpha <= 0f) return

        val duration = (obj.endTimeMs - obj.startTimeMs).coerceAtLeast(1.0)
        val fill = ((time - obj.startTimeMs) / duration).coerceIn(0.0, 1.0).toFloat()
        val centreX = OSU_PLAYFIELD_WIDTH / 2f
        val centreY = OSU_PLAYFIELD_HEIGHT / 2f
        val spinDegrees = (-(time - obj.startTimeMs) * 0.05 * 180.0 / PI).toFloat()
        val spinnerScale = OsuLegacySkinMetrics.spinnerPlayfieldScale()

        if (time < obj.endTimeMs) {
            val approachScale = if (time < obj.startTimeMs) {
                1f
            } else {
                1f - 0.9f * fill
            }
            skin.spinnerApproach?.let { bitmap ->
                val textureScale = spinnerScale * 1.86f * approachScale
                drawCenteredBitmap(
                    canvas,
                    bitmap,
                    centreX,
                    centreY,
                    bitmap.width * textureScale,
                    bitmap.height * textureScale,
                    alpha * 0.85f
                )
            } ?: run {
                if (time < obj.startTimeMs) {
                    val progress = ((time - obj.activeStartMs) / obj.preemptMs)
                        .coerceIn(0.0, 1.0).toFloat()
                    val size = OSU_PLAYFIELD_HEIGHT * (1.12f - 0.12f * progress)
                    setStroke(comboColour(timeline, obj.comboColourIndex), alpha * 0.8f, 4f)
                    canvas.drawCircle(centreX, centreY, size / 2f, strokePaint)
                }
            }
        }

        if (time >= obj.startTimeMs) {
            val background = skin.spinnerBackground
            val circle = skin.spinnerCircle
            background?.let { bitmap ->
                drawCenteredBitmap(
                    canvas,
                    bitmap,
                    centreX,
                    centreY,
                    bitmap.width * spinnerScale,
                    bitmap.height * spinnerScale,
                    alpha * 0.35f
                )
            }

            canvas.save()
            canvas.rotate(spinDegrees, centreX, centreY)
            circle?.let { bitmap ->
                drawCenteredBitmap(
                    canvas,
                    bitmap,
                    centreX,
                    centreY,
                    bitmap.width * spinnerScale,
                    bitmap.height * spinnerScale,
                    alpha * 0.7f
                )
            }
            // The bundled legacy spinner is an old-style spinner, which has no
            // glow layer. Only use glow when a background layer is absent.
            if (background == null) {
                skin.spinnerGlow?.let { bitmap ->
                    val fillScale = 0.92f + fill * 0.16f
                    drawCenteredBitmap(
                        canvas,
                        bitmap,
                        centreX,
                        centreY,
                        bitmap.width * spinnerScale * fillScale,
                        bitmap.height * spinnerScale * fillScale,
                        alpha * (0.2f + fill * 0.55f)
                    )
                }
            }
            canvas.restore()

            if (circle == null) {
                val fallbackSize = OSU_PLAYFIELD_HEIGHT * 0.85f
                setStroke(comboColour(timeline, obj.comboColourIndex), alpha * 0.8f, 4f)
                canvas.drawCircle(centreX, centreY, fallbackSize / 2f, strokePaint)
            }
            val arcSize = (circle?.width?.toFloat() ?: OSU_PLAYFIELD_HEIGHT * 0.85f) *
                spinnerScale * 0.92f
            arcBounds.set(
                centreX - arcSize / 2f,
                centreY - arcSize / 2f,
                centreX + arcSize / 2f,
                centreY + arcSize / 2f
            )
            setStroke(Color.WHITE, alpha * 0.9f, 6f)
            canvas.drawArc(arcBounds, -90f, 360f * fill, false, strokePaint)
        }
    }

    private fun drawHitCircle(
        canvas: Canvas,
        centre: RenderPoint,
        objectScale: Float,
        sizeMultiplier: Float,
        comboNumber: Int,
        colour: Int,
        alpha: Float
    ) {
        drawCirclePiece(
            canvas = canvas,
            centre = centre,
            objectScale = objectScale,
            sizeMultiplier = sizeMultiplier,
            comboNumber = comboNumber,
            colour = colour,
            alpha = alpha,
            baseBitmap = skin.hitCircle,
            overlayBitmap = skin.hitCircleOverlay
        )
    }

    private fun drawSliderHead(
        canvas: Canvas,
        centre: RenderPoint,
        objectScale: Float,
        sizeMultiplier: Float,
        comboNumber: Int,
        colour: Int,
        alpha: Float
    ) {
        val hasPrefixedStart = skin.sliderStartCircle != null
        drawCirclePiece(
            canvas = canvas,
            centre = centre,
            objectScale = objectScale,
            sizeMultiplier = sizeMultiplier,
            comboNumber = comboNumber,
            colour = colour,
            alpha = alpha,
            baseBitmap = skin.sliderStartCircle ?: skin.hitCircle,
            // A prefixed circle only pairs with its matching overlay. Falling
            // back to hitcircleoverlay would mix textures from different pieces.
            overlayBitmap = if (hasPrefixedStart) {
                skin.sliderStartOverlay
            } else {
                skin.hitCircleOverlay
            }
        )
    }

    private fun drawCirclePiece(
        canvas: Canvas,
        centre: RenderPoint,
        objectScale: Float,
        sizeMultiplier: Float,
        comboNumber: Int?,
        colour: Int,
        alpha: Float,
        baseBitmap: Bitmap?,
        overlayBitmap: Bitmap?
    ) {
        if (alpha <= 0f) return
        if (baseBitmap != null) {
            val textureScale = OsuLegacySkinMetrics.textureFitScale(
                baseBitmap.width,
                baseBitmap.height,
                OsuLegacySkinMetrics.HIT_CIRCLE_TEXTURE_MAX_SIZE
            ) * objectScale * sizeMultiplier
            drawCentredBitmap(
                canvas,
                baseBitmap,
                centre,
                baseBitmap.width * textureScale,
                baseBitmap.height * textureScale,
                alpha,
                colour
            )
        } else {
            val diameter = OsuLegacySkinMetrics.OBJECT_DIMENSIONS * objectScale * sizeMultiplier
            setFill(colour, alpha * 0.88f)
            canvas.drawCircle(centre.x, centre.y, diameter / 2f, fillPaint)
        }

        if (comboNumber == null) {
            overlayBitmap?.let {
                drawCircleOverlay(canvas, it, centre, objectScale, sizeMultiplier, alpha)
            }
        } else if (skin.hitCircleOverlayAboveNumber) {
            drawComboNumber(
                canvas,
                centre,
                objectScale,
                sizeMultiplier,
                comboNumber,
                alpha
            )
            overlayBitmap?.let {
                drawCircleOverlay(canvas, it, centre, objectScale, sizeMultiplier, alpha)
            }
        } else {
            overlayBitmap?.let {
                drawCircleOverlay(canvas, it, centre, objectScale, sizeMultiplier, alpha)
            }
            drawComboNumber(
                canvas,
                centre,
                objectScale,
                sizeMultiplier,
                comboNumber,
                alpha
            )
        }
    }

    private fun drawCircleOverlay(
        canvas: Canvas,
        bitmap: Bitmap,
        centre: RenderPoint,
        objectScale: Float,
        sizeMultiplier: Float,
        alpha: Float
    ) {
        val textureScale = OsuLegacySkinMetrics.textureFitScale(
            bitmap.width,
            bitmap.height,
            OsuLegacySkinMetrics.HIT_CIRCLE_TEXTURE_MAX_SIZE
        ) * objectScale * sizeMultiplier
        drawCentredBitmap(
            canvas,
            bitmap,
            centre,
            bitmap.width * textureScale,
            bitmap.height * textureScale,
            alpha
        )
    }

    private fun drawComboNumber(
        canvas: Canvas,
        centre: RenderPoint,
        objectScale: Float,
        sizeMultiplier: Float,
        comboNumber: Int,
        alpha: Float
    ) {
        val count = fillComboDigits(comboNumber)
        var allBitmapsAvailable = true
        for (index in 0 until count) {
            val bitmap = skin.digitAt(comboDigits[index])
            comboDigitBitmaps[index] = bitmap
            if (bitmap == null) allBitmapsAvailable = false
        }
        if (!allBitmapsAvailable) {
            comboText.setLength(0)
            for (index in 0 until count) comboText.append('0' + comboDigits[index])
            textPaint.alpha = (255 * alpha).toInt().coerceIn(0, 255)
            textPaint.textSize = 60f * OsuLegacySkinMetrics.HIT_CIRCLE_TEXT_SCALE *
                objectScale * sizeMultiplier
            val baseline = centre.y - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(comboText, 0, comboText.length, centre.x, baseline, textPaint)
            return
        }

        val overlap = OsuLegacySkinMetrics.comboOverlap(
            skin.hitCircleOverlap,
            objectScale * sizeMultiplier
        )
        var totalWidth = -overlap
        for (index in 0 until count) {
            val bitmap = comboDigitBitmaps[index] ?: continue
            val fitScale = OsuLegacySkinMetrics.textureFitScale(
                bitmap.width,
                bitmap.height,
                OsuLegacySkinMetrics.HIT_CIRCLE_TEXT_TEXTURE_MAX_SIZE
            )
            val width = bitmap.width * fitScale *
                OsuLegacySkinMetrics.HIT_CIRCLE_TEXT_SCALE * objectScale * sizeMultiplier
            comboDigitWidths[index] = width
            totalWidth += width
            if (index != count - 1) totalWidth -= overlap
        }
        var x = centre.x - totalWidth / 2f
        for (index in 0 until count) {
            val width = comboDigitWidths[index]
            val bitmap = comboDigitBitmaps[index] ?: continue
            val fitScale = OsuLegacySkinMetrics.textureFitScale(
                bitmap.width,
                bitmap.height,
                OsuLegacySkinMetrics.HIT_CIRCLE_TEXT_TEXTURE_MAX_SIZE
            )
            val height = bitmap.height * fitScale *
                OsuLegacySkinMetrics.HIT_CIRCLE_TEXT_SCALE * objectScale * sizeMultiplier
            drawCentredBitmap(
                canvas = canvas,
                bitmap = bitmap,
                centre = RenderPoint(x + width / 2f, centre.y),
                width = width,
                height = height,
                alpha = alpha
            )
            x += width - overlap
        }
    }

    private fun fillComboDigits(value: Int): Int {
        val number = value.coerceAtLeast(1)
        var divisor = 1
        var count = 1
        while (number / divisor >= 10 && count < comboDigits.size) {
            divisor *= 10
            count++
        }
        var remaining = number
        for (index in 0 until count) {
            comboDigits[index] = remaining / divisor
            remaining %= divisor
            divisor /= 10
        }
        return count
    }

    private fun drawApproachCircle(
        canvas: Canvas,
        centre: RenderPoint,
        objectScale: Float,
        approachProgress: Float,
        colour: Int,
        alpha: Float
    ) {
        val scaledDiameter = OsuLegacySkinMetrics.OBJECT_DIMENSIONS * objectScale *
            (1f + 3f * (1f - approachProgress))
        val bitmap = skin.approachCircle
        if (bitmap != null) {
            val textureScale = OsuLegacySkinMetrics.textureFitScale(
                bitmap.width,
                bitmap.height,
                OsuLegacySkinMetrics.HIT_CIRCLE_TEXTURE_MAX_SIZE
            ) * objectScale * (1f + 3f * (1f - approachProgress))
            drawCentredBitmap(
                canvas,
                bitmap,
                centre,
                bitmap.width * textureScale,
                bitmap.height * textureScale,
                alpha * 0.95f,
                colour
            )
        } else {
            setStroke(
                colour,
                alpha * 0.95f,
                max(2f, OsuLegacySkinMetrics.OBJECT_DIMENSIONS * objectScale * 0.04f)
            )
            canvas.drawCircle(centre.x, centre.y, scaledDiameter / 2f, strokePaint)
        }
    }

    private fun drawSliderTrack(
        canvas: Canvas,
        slider: OsuSliderObject,
        trackedLength: Float,
        trackColour: Int,
        borderColour: Int,
        alpha: Float
    ) {
        sliderBodyMesh.draw(
            canvas = canvas,
            paint = sliderBodyPaint,
            path = slider.path,
            trackedLength = trackedLength,
            offset = slider.stackedPosition - slider.position,
            objectScale = slider.objectScale,
            trackColour = trackColour,
            borderColour = borderColour,
            alpha = alpha
        )
    }

    private fun drawSliderBall(
        canvas: Canvas,
        centre: RenderPoint,
        objectScale: Float,
        fallbackDiameter: Float,
        colour: Int,
        alpha: Float
    ) {
        skin.sliderFollowCircle?.let { bitmap ->
            val textureScale = OsuLegacySkinMetrics.textureFitScale(
                bitmap.width,
                bitmap.height,
                OsuLegacySkinMetrics.SLIDER_TEXTURE_MAX_SIZE
            ) * 0.5f * objectScale
            drawCentredBitmap(
                canvas,
                bitmap,
                centre,
                bitmap.width * textureScale,
                bitmap.height * textureScale,
                alpha * 0.8f
            )
        } ?: run {
            val followDiameter = fallbackDiameter * 2f
            setStroke(Color.WHITE, alpha * 0.45f, max(1.5f, fallbackDiameter * 0.025f))
            canvas.drawCircle(centre.x, centre.y, followDiameter / 2f, strokePaint)
        }

        skin.sliderBall?.let { bitmap ->
            val textureScale = OsuLegacySkinMetrics.textureFitScale(
                bitmap.width,
                bitmap.height,
                OsuLegacySkinMetrics.SLIDER_TEXTURE_MAX_SIZE
            ) * objectScale
            drawCentredBitmap(
                canvas,
                bitmap,
                centre,
                bitmap.width * textureScale,
                bitmap.height * textureScale,
                alpha,
                colour
            )
        } ?: run {
            setFill(colour, alpha)
            canvas.drawCircle(centre.x, centre.y, fallbackDiameter / 2f, fillPaint)
            setStroke(Color.WHITE, alpha * 0.75f, max(1.5f, fallbackDiameter * 0.025f))
            canvas.drawCircle(centre.x, centre.y, fallbackDiameter / 2f, strokePaint)
        }
    }

    private fun drawSliderTail(
        canvas: Canvas,
        slider: OsuSliderObject,
        colour: Int,
        alpha: Float
    ) {
        val finalFraction = if (slider.spanCount % 2 == 0) 0f else 1f
        val tail = slider.stackedPosition +
            (slider.path.positionAtFraction(finalFraction) - slider.position)
        val hasPrefixedEnd = skin.sliderEndCircle != null
        drawCirclePiece(
            canvas = canvas,
            centre = tail,
            objectScale = slider.objectScale,
            sizeMultiplier = 1f,
            comboNumber = null,
            colour = colour,
            alpha = alpha,
            baseBitmap = skin.sliderEndCircle ?: skin.hitCircle,
            overlayBitmap = if (hasPrefixedEnd) {
                skin.sliderEndOverlay
            } else {
                skin.hitCircleOverlay
            }
        )
    }

    private fun drawRepeatArrows(
        canvas: Canvas,
        slider: OsuSliderObject,
        colour: Int,
        alpha: Float
    ) {
        if (slider.repeatCount <= 0) return
        val arrowBitmap = skin.reverseArrow
        val textureScale = arrowBitmap?.let {
            OsuLegacySkinMetrics.textureFitScale(
                it.width,
                it.height,
                OsuLegacySkinMetrics.HIT_CIRCLE_TEXTURE_MAX_SIZE
            ) * slider.objectScale
        } ?: slider.objectScale
        val fallbackSize = OsuLegacySkinMetrics.OBJECT_DIMENSIONS * slider.objectScale
        for (repeat in 1..slider.repeatCount) {
            val forwardEndpoint = repeat % 2 == 1
            val fraction = if (forwardEndpoint) 1f else 0f
            val point = slider.stackedPosition +
                (slider.path.positionAtFraction(fraction) - slider.position)
            val tangent = slider.path.tangentAtDistance(
                if (forwardEndpoint) slider.path.length else 0f
            )
            val angle = (atan2(tangent.y.toDouble(), tangent.x.toDouble()) * 180.0 / PI).toFloat()
            if (arrowBitmap == null) {
                setStroke(colour, alpha, max(2f, fallbackSize * 0.09f))
                val nx = -tangent.y
                val ny = tangent.x
                canvas.drawLine(
                    point.x - tangent.x * fallbackSize * 0.25f,
                    point.y - tangent.y * fallbackSize * 0.25f,
                    point.x + tangent.x * fallbackSize * 0.25f,
                    point.y + tangent.y * fallbackSize * 0.25f,
                    strokePaint
                )
                canvas.drawLine(
                    point.x + tangent.x * fallbackSize * 0.25f,
                    point.y + tangent.y * fallbackSize * 0.25f,
                    point.x + nx * fallbackSize * 0.18f,
                    point.y + ny * fallbackSize * 0.18f,
                    strokePaint
                )
                canvas.drawLine(
                    point.x + tangent.x * fallbackSize * 0.25f,
                    point.y + tangent.y * fallbackSize * 0.25f,
                    point.x - nx * fallbackSize * 0.18f,
                    point.y - ny * fallbackSize * 0.18f,
                    strokePaint
                )
            } else {
                canvas.save()
                canvas.rotate(angle + 90f, point.x, point.y)
                drawCentredBitmap(
                    canvas,
                    arrowBitmap,
                    point,
                    arrowBitmap.width * textureScale,
                    arrowBitmap.height * textureScale,
                    alpha,
                    colour
                )
                canvas.restore()
            }
        }
    }

    private fun drawPlayfieldBorder(
        canvas: Canvas,
        playfieldSize: OsuPlayfieldSize,
        scale: Float,
        borderWidthPx: Float
    ) {
        val playfieldWidth = OSU_PLAYFIELD_WIDTH * scale
        val playfieldHeight = OSU_PLAYFIELD_HEIGHT * scale
        val left = (playfieldSize.widthPx - playfieldWidth) / 2f
        val top = (playfieldSize.heightPx - playfieldHeight) / 2f
        val halfBorder = borderWidthPx / 2f
        setStroke(Color.WHITE, 0.13f, borderWidthPx)
        canvas.drawRect(
            left + halfBorder,
            top + halfBorder,
            left + playfieldWidth - halfBorder,
            top + playfieldHeight - halfBorder,
            strokePaint
        )
    }

    private fun comboColour(timeline: OsuRenderTimeline, index: Int): Int {
        val palette = if (timeline.comboColours.isNotEmpty()) {
            timeline.comboColours
        } else {
            skin.paletteColours()
        }
        return palette[index.mod(palette.size)]
    }

    private fun fadeInAlpha(obj: OsuRenderObject, time: Double): Float {
        if (time < obj.activeStartMs) return 0f
        if (obj.fadeInMs <= 0.0) return 1f
        return ((time - obj.activeStartMs) / obj.fadeInMs)
            .coerceIn(0.0, 1.0)
            .toFloat()
    }

    private fun postHitAlpha(startTimeMs: Double, time: Double): Float {
        if (time < startTimeMs) return 1f
        return (1.0 - (time - startTimeMs) / OsuRenderObject.OBJECT_FADE_OUT_MS)
            .coerceIn(0.0, 1.0)
            .toFloat()
    }

    private fun postHitScale(startTimeMs: Double, time: Double): Float {
        if (time < startTimeMs) return 1f
        return 1f + 0.4f *
            ((time - startTimeMs) / OsuRenderObject.OBJECT_FADE_OUT_MS)
                .coerceIn(0.0, 1.0)
                .toFloat()
    }

    private fun drawCentredBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        centre: RenderPoint,
        width: Float,
        height: Float,
        alpha: Float,
        tint: Int? = null
    ) {
        dst.set(
            centre.x - width / 2f,
            centre.y - height / 2f,
            centre.x + width / 2f,
            centre.y + height / 2f
        )
        imagePaint.alpha = (255f * alpha).toInt().coerceIn(0, 255)
        imagePaint.colorFilter = tint?.let {
            tintFilters.getOrPut(it) { PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN) }
        }
        canvas.drawBitmap(bitmap, null, dst, imagePaint)
        imagePaint.colorFilter = null
    }

    private fun drawCenteredBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        centreX: Float,
        centreY: Float,
        width: Float,
        height: Float,
        alpha: Float
    ) {
        drawCentredBitmap(
            canvas,
            bitmap,
            RenderPoint(centreX, centreY),
            width,
            height,
            alpha
        )
    }

    private fun setFill(colour: Int, alpha: Float) {
        fillPaint.color = colour
        fillPaint.alpha = (255f * alpha).toInt().coerceIn(0, 255)
    }

    private fun setStroke(colour: Int, alpha: Float, width: Float) {
        strokePaint.color = colour
        strokePaint.alpha = (255f * alpha).toInt().coerceIn(0, 255)
        strokePaint.strokeWidth = width
    }

}
