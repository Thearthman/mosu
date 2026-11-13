package com.mosu.app.domain.render.mania

import android.graphics.Bitmap

/**
 * Preloaded, immutable skin data for one mania column.
 *
 * All pixel dimensions use the renderer's fixed 1024x1024 playfield
 * coordinate space. Bitmap decoding and file lookup happen in
 * [ManiaSkinLoader] before the frame loop starts.
 */
data class ManiaColumnSkin(
    val width: Float,
    val leftSpacing: Float,
    val rightSpacing: Float,
    /** Reference height used when scaling legacy note textures. */
    val noteHeight: Float,
    val note: Bitmap?,
    val holdHead: Bitmap?,
    val holdTail: Bitmap?,
    val holdBody: Bitmap?,
    val keyUp: Bitmap?,
    val keyDown: Bitmap?,
    val accentColour: Int
)

/**
 * The complete set of resources needed to draw a mania preview.
 *
 * Imported-skin bitmaps belong to this instance and are recycled by [close].
 */
data class ManiaSkin(
    val keyCount: Int,
    val columns: List<ManiaColumnSkin>,
    val hitPositionFromBottom: Float,
    val barlineHeight: Float,
    val columnLineWidths: List<Float>,
    val columnLineColour: Int?,
    val judgementLineColour: Int?,
    val barlineColour: Int?,
    val showJudgementLine: Boolean,
    val keysUnderNotes: Boolean,
    val noteBodyStyle: ManiaNoteBodyStyle,
    val stageLeft: Bitmap?,
    val stageRight: Bitmap?,
    val stageBottom: Bitmap?,
    val stageHint: Bitmap?,
    private val ownedBitmaps: List<Bitmap> = emptyList()
) {
    fun close() {
        ownedBitmaps.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}
