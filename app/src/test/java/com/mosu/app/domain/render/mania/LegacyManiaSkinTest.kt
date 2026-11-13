package com.mosu.app.domain.render.mania

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyManiaSkinTest {
    @Test
    fun usesLegacyStableDefaults() {
        val config = requireNotNull(
            LegacyManiaSkinParser.parse(
                """
                    [General]
                    Version: 2.5
                    [Mania]
                    Keys: 4
                """.trimIndent(),
                keyCount = 4
            )
        )

        assertEquals(listOf(48f, 48f, 48f, 48f), config.columnWidths)
        assertEquals(listOf(0f, 0f, 0f), config.columnSpacings)
        assertEquals(List(5) { 2f }, config.columnLineWidths)
        assertEquals(124.8f, config.hitPositionFromBottom ?: 0f, 0.0001f)
        assertEquals(1.2f, config.barlineHeight, 0.0001f)
        assertEquals(48f, config.resolvedNoteHeight(48f), 0.0001f)
        assertTrue(config.showJudgementLine)
        assertFalse(config.keysUnderNotes)
        assertEquals(ManiaNoteBodyStyle.RepeatBottom, config.noteBodyStyle)
    }

    @Test
    fun scalesColumnLayoutValuesButNotLineWidths() {
        val config = requireNotNull(
            LegacyManiaSkinParser.parse(
                """
                    [General]
                    Version: 2.5
                    [Mania]
                    Keys: 4
                    ColumnWidth: 30, 40, 50, 60
                    ColumnSpacing: 2, 3, 4
                    ColumnLineWidth: 1.5, 2.5, 3.5, 4.5, 5.5
                    WidthForNoteHeightScale: 7.5
                """.trimIndent(),
                keyCount = 4
            )
        )

        assertEquals(listOf(48f, 64f, 80f, 96f), config.columnWidths)
        assertEquals(listOf(3.2f, 4.8f, 6.4f), config.columnSpacings)
        assertEquals(
            listOf(1.5f, 2.5f, 3.5f, 4.5f, 5.5f),
            config.columnLineWidths
        )
        assertEquals(12f, config.widthForNoteHeightScale ?: 0f, 0.0001f)
        assertEquals(12f, config.resolvedNoteHeight(48f), 0.0001f)
        assertEquals(12f, config.resolvedNoteHeight(96f), 0.0001f)
    }

    @Test
    fun usesEachColumnsWidthAsTheDefaultNoteHeight() {
        val config = requireNotNull(
            LegacyManiaSkinParser.parse(
                """
                    [General]
                    Version: 2.5
                    [Mania]
                    Keys: 4
                    ColumnWidth: 30, 40, 50, 60
                """.trimIndent(),
                keyCount = 4
            )
        )

        assertEquals(48f, config.resolvedNoteHeight(48f), 0.0001f)
        assertEquals(96f, config.resolvedNoteHeight(96f), 0.0001f)
    }

    @Test
    fun parsesColumnColoursUsingOneBasedIndices() {
        val config = requireNotNull(
            LegacyManiaSkinParser.parse(
                """
                    [General]
                    Version: 2.5
                    [Mania]
                    Keys: 4
                    Colour1: 255, 0, 0
                    Colour4: 0, 255, 0, 128
                    ColourLight2: 0, 0, 255
                    ColourColumnLine: 10, 20, 30
                    ColourJudgementLine: 40, 50, 60
                    ColourBarline: 70, 80, 90
                """.trimIndent(),
                keyCount = 4
            )
        )

        assertEquals(0xFFFF0000.toInt(), config.columnColours[0])
        assertEquals(0x8000FF00.toInt(), config.columnColours[3])
        assertEquals(0xFF0000FF.toInt(), config.lightColours[1])
        assertEquals(0xFF0A141E.toInt(), config.columnLineColour)
        assertEquals(0xFF28323C.toInt(), config.judgementLineColour)
        assertEquals(0xFF46505A.toInt(), config.barlineColour)
    }

    @Test
    fun resolvesNoteAndKeyImageNamesUsingZeroBasedIndices() {
        val config = requireNotNull(
            LegacyManiaSkinParser.parse(
                """
                    [General]
                    Version: 2.5
                    [Mania]
                    Keys: 4
                    NoteImage0: note
                    NoteImage0H: note-head
                    NoteImage0T: note-tail
                    NoteImage0L: note-body
                    KeyImage0: key
                    KeyImage0D: key-down
                """.trimIndent(),
                keyCount = 4
            )
        )

        assertEquals("note", config.imageNameFor(0, ManiaSkinImageKind.NOTE))
        assertEquals("note-head", config.imageNameFor(0, ManiaSkinImageKind.HOLD_HEAD))
        assertEquals("note-tail", config.imageNameFor(0, ManiaSkinImageKind.HOLD_TAIL))
        assertEquals("note-body", config.imageNameFor(0, ManiaSkinImageKind.HOLD_BODY))
        assertEquals("key", config.imageNameFor(0, ManiaSkinImageKind.KEY_UP))
        assertEquals("key-down", config.imageNameFor(0, ManiaSkinImageKind.KEY_DOWN))
        assertNull(config.imageNameFor(1, ManiaSkinImageKind.NOTE))
    }

    @Test
    fun buildsStableManiaImageFallbackRequests() {
        val note = LegacyManiaSkinParser.imageRequest(
            config = null,
            keyCount = 4,
            column = 1,
            kind = ManiaSkinImageKind.NOTE
        )
        val holdHead = LegacyManiaSkinParser.imageRequest(
            config = null,
            keyCount = 5,
            column = 2,
            kind = ManiaSkinImageKind.HOLD_HEAD
        )
        val keyDown = LegacyManiaSkinParser.imageRequest(
            config = null,
            keyCount = 4,
            column = 0,
            kind = ManiaSkinImageKind.KEY_DOWN
        )
        val stage = LegacyManiaSkinParser.imageRequest(
            config = null,
            keyCount = 4,
            column = 0,
            kind = ManiaSkinImageKind.STAGE_LEFT
        )

        assertEquals("mania-note2", note.fallbackName)
        assertEquals("mania-noteSH", holdHead.fallbackName)
        assertEquals("mania-key1D", keyDown.fallbackName)
        assertEquals("mania-stage-left", stage.fallbackName)
    }

    @Test
    fun explicitImageNamesTakePriorityOverFallbacks() {
        val config = requireNotNull(
            LegacyManiaSkinParser.parse(
                """
                    [General]
                    Version: 2.5
                    [Mania]
                    Keys: 4
                    NoteImage2: custom-note
                    StageRight: custom-stage-right
                """.trimIndent(),
                keyCount = 4
            )
        )

        val note = LegacyManiaSkinParser.imageRequest(
            config,
            keyCount = 4,
            column = 2,
            kind = ManiaSkinImageKind.NOTE
        )
        val stageRight = LegacyManiaSkinParser.imageRequest(
            config,
            keyCount = 4,
            column = 0,
            kind = ManiaSkinImageKind.STAGE_RIGHT
        )

        assertEquals("custom-note", note.explicitName)
        assertEquals("custom-stage-right", stageRight.explicitName)
    }

    @Test
    fun usesVersionDependentDefaultNoteBodyStyles() {
        val oldConfig = requireNotNull(parseWithBodyStyle(version = "2.4"))
        val modernConfig = requireNotNull(parseWithBodyStyle(version = "2.5"))

        assertEquals(ManiaNoteBodyStyle.Stretch, oldConfig.noteBodyStyle)
        assertEquals(ManiaNoteBodyStyle.RepeatBottom, modernConfig.noteBodyStyle)
    }

    @Test
    fun parsesExplicitNoteBodyStyleNamesAndNumbers() {
        assertEquals(
            ManiaNoteBodyStyle.RepeatTop,
            requireNotNull(
                parseWithBodyStyle(maniaLine = "NoteBodyStyle: RepeatTop")
            ).noteBodyStyle
        )
        assertEquals(
            ManiaNoteBodyStyle.RepeatTopAndBottom,
            requireNotNull(
                parseWithBodyStyle(maniaLine = "NoteBodyStyle: 4")
            ).noteBodyStyle
        )
    }

    @Test
    fun selectsTheManiaBlockMatchingTheRequestedKeyCount() {
        val config = requireNotNull(
            LegacyManiaSkinParser.parse(
                """
                    [General]
                    Version: 2.5
                    [Mania]
                    Keys: 7
                    HitPosition: 300
                    [Mania]
                    Keys: 4
                    HitPosition: 402
                """.trimIndent(),
                keyCount = 4
            )
        )

        assertEquals(4, config.keyCount)
        assertEquals(124.8f, config.hitPositionFromBottom ?: 0f, 0.0001f)
    }

    @Test
    fun returnsNullForUnsupportedRequestedKeyCounts() {
        assertNull(LegacyManiaSkinParser.parse("[Mania]\nKeys: 4", keyCount = 0))
        assertNull(LegacyManiaSkinParser.parse("[Mania]\nKeys: 4", keyCount = 11))
    }

    private fun parseWithBodyStyle(
        version: String = "2.5",
        maniaLine: String = ""
    ): LegacyManiaSkinConfiguration? =
        LegacyManiaSkinParser.parse(
            """
                [General]
                Version: $version
                [Mania]
                Keys: 4
                $maniaLine
            """.trimIndent(),
            keyCount = 4
        )
}
