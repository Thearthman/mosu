package com.mosu.app.domain.render.mania

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mosu.app.domain.render.RenderSkinSelection
import com.mosu.app.domain.render.SkinFileIndex
import java.io.File

/**
 * Loads the mania-specific portion of a legacy skin.
 *
 * A custom skin's `[Mania]` block uses stable's legacy defaults. When no
 * matching block exists, the preview uses lazer's default column layout but
 * may still resolve conventional mania image names from the imported folder.
 */
object ManiaSkinLoader {
    private const val MIN_NON_BLANK_BYTES = 256
    private const val DEFAULT_NOTE_HEIGHT = 12f
    private const val DEFAULT_HIT_POSITION_FROM_BOTTOM =
        ManiaRenderTimeline.DEFAULT_HIT_POSITION_FROM_BOTTOM
    private const val DEFAULT_BARLINE_HEIGHT = 1.2f
    private const val DEFAULT_COLUMN_LINE_WIDTH = 2f
    private const val DEFAULT_COLUMN_WIDTH = 80f
    private const val DEFAULT_SPECIAL_COLUMN_WIDTH = 70f
    private const val DEFAULT_COLUMN_SPACING = 1f
    private val imageExtensions = listOf("png", "jpg", "jpeg")

    fun load(
        keyCount: Int,
        selection: RenderSkinSelection
    ): ManiaSkin {
        require(keyCount in 1..10) { "Mania preview supports 1..10 keys" }

        val customRoot = selection.directory?.takeIf(File::isDirectory)
        val files = customRoot?.let(SkinFileIndex::build)
        val iniText = files?.find("skin.ini")?.let { file ->
            runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
        }
        val legacyConfig = iniText?.let {
            LegacyManiaSkinParser.parse(it, keyCount)
        }
        val legacy = legacyConfig != null
        val ownedBitmaps = mutableListOf<Bitmap>()
        val decodedFiles = mutableMapOf<String, Bitmap?>()

        fun decode(file: File): Bitmap? {
            val key = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
            if (decodedFiles.containsKey(key)) return decodedFiles[key]
            val decoded = runCatching { file.readBytes() }.getOrNull()
                ?.let { decodeBitmap(it) }
            decoded?.let(ownedBitmaps::add)
            decodedFiles[key] = decoded
            return decoded
        }

        fun bitmap(request: LegacyManiaSkinImageRequest): Bitmap? {
            val file = resolveImageFile(files, request.explicitName)
                ?: resolveImageFile(files, request.fallbackName)
                ?: return null
            return decode(file)
        }

        fun columnImage(column: Int, kind: ManiaSkinImageKind): Bitmap? =
            bitmap(
                LegacyManiaSkinParser.imageRequest(
                    config = legacyConfig,
                    keyCount = keyCount,
                    column = column,
                    kind = kind
                )
            )

        val widths = if (legacy) {
            legacyConfig!!.columnWidths
        } else {
            List(keyCount) { index ->
                if (keyCount % 2 == 1 && index == keyCount / 2) {
                    DEFAULT_SPECIAL_COLUMN_WIDTH
                } else {
                    DEFAULT_COLUMN_WIDTH
                }
            }
        }
        val spacings = if (legacy) {
            legacyConfig!!.columnSpacings
        } else {
            List((keyCount - 1).coerceAtLeast(0)) { DEFAULT_COLUMN_SPACING }
        }
        val lineWidths = if (legacy) {
            legacyConfig!!.columnLineWidths
        } else {
            List(keyCount + 1) { DEFAULT_COLUMN_LINE_WIDTH }
        }

        val columns = List(keyCount) { column ->
            val width = widths.getOrNull(column)?.takeIf { it > 0f }
                ?: DEFAULT_COLUMN_WIDTH
            val noteHeight = if (legacy) {
                legacyConfig!!.resolvedNoteHeight(width)
            } else {
                DEFAULT_NOTE_HEIGHT
            }
            ManiaColumnSkin(
                width = width,
                leftSpacing = if (column == 0) {
                    0f
                } else {
                    (spacings.getOrNull(column - 1) ?: 0f) / 2f
                },
                rightSpacing = if (column == keyCount - 1) {
                    0f
                } else {
                    (spacings.getOrNull(column) ?: 0f) / 2f
                },
                noteHeight = noteHeight,
                note = columnImage(column, ManiaSkinImageKind.NOTE),
                holdHead = columnImage(column, ManiaSkinImageKind.HOLD_HEAD),
                holdTail = columnImage(column, ManiaSkinImageKind.HOLD_TAIL),
                holdBody = columnImage(column, ManiaSkinImageKind.HOLD_BODY),
                keyUp = columnImage(column, ManiaSkinImageKind.KEY_UP),
                keyDown = columnImage(column, ManiaSkinImageKind.KEY_DOWN),
                accentColour = legacyConfig?.columnColours?.getOrNull(column)
                    ?: LegacyManiaSkinParser.DEFAULT_ACCENT_COLOUR
            )
        }

        fun stageImage(kind: ManiaSkinImageKind): Bitmap? =
            bitmap(
                LegacyManiaSkinParser.imageRequest(
                    config = legacyConfig,
                    keyCount = keyCount,
                    column = 0,
                    kind = kind
                )
            )

        return ManiaSkin(
            keyCount = keyCount,
            columns = columns,
            hitPositionFromBottom = legacyConfig?.hitPositionFromBottom
                ?: DEFAULT_HIT_POSITION_FROM_BOTTOM,
            barlineHeight = legacyConfig?.barlineHeight ?: DEFAULT_BARLINE_HEIGHT,
            columnLineWidths = lineWidths.takeIf { it.size == keyCount + 1 }
                ?: List(keyCount + 1) { DEFAULT_COLUMN_LINE_WIDTH },
            columnLineColour = legacyConfig?.columnLineColour,
            judgementLineColour = legacyConfig?.judgementLineColour,
            barlineColour = legacyConfig?.barlineColour,
            showJudgementLine = legacyConfig?.showJudgementLine ?: true,
            keysUnderNotes = legacyConfig?.keysUnderNotes ?: false,
            noteBodyStyle = legacyConfig?.noteBodyStyle ?: ManiaNoteBodyStyle.Stretch,
            stageLeft = stageImage(ManiaSkinImageKind.STAGE_LEFT),
            stageRight = stageImage(ManiaSkinImageKind.STAGE_RIGHT),
            stageBottom = stageImage(ManiaSkinImageKind.STAGE_BOTTOM),
            stageHint = stageImage(ManiaSkinImageKind.STAGE_HINT),
            ownedBitmaps = ownedBitmaps.distinct()
        )
    }

    internal fun resolveImageFile(
        files: SkinFileIndex?,
        requestedName: String?
    ): File? {
        val name = requestedName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (files == null) return null

        val normalized = name.replace('\\', '/')
        val slash = normalized.lastIndexOf('/')
        val directory = if (slash >= 0) normalized.substring(0, slash + 1) else ""
        val filename = normalized.substring(slash + 1)
        val extension = filename.substringAfterLast('.', missingDelimiterValue = "")
        val hasExtension = extension.lowercase() in imageExtensions
        val stem = if (hasExtension) filename.substringBeforeLast('.') else filename
        val extensionCandidates = if (hasExtension) listOf(extension) else imageExtensions
        val candidates = buildList {
            extensionCandidates.forEach { candidateExtension ->
                add("$directory$stem@2x.$candidateExtension")
            }
            extensionCandidates.forEach { candidateExtension ->
                add("$directory$stem.$candidateExtension")
            }
        }
        return candidates.firstNotNullOfOrNull(files::find)
    }

    private fun decodeBitmap(bytes: ByteArray): Bitmap? {
        if (bytes.size < MIN_NON_BLANK_BYTES) return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        if (!hasVisiblePixels(bitmap)) {
            bitmap.recycle()
            return null
        }
        return bitmap
    }

    private fun hasVisiblePixels(bitmap: Bitmap): Boolean {
        if (bitmap.width <= 1 || bitmap.height <= 1) return false
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (pixel in row) {
                if (pixel ushr 24 != 0) return true
            }
        }
        return false
    }
}
