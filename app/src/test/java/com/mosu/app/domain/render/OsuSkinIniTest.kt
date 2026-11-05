package com.mosu.app.domain.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OsuSkinIniTest {
    @Test
    fun sectionsAndKeysAreCaseInsensitiveAndBomAndCommentsAreIgnored() {
        val ini = SkinIni.parse(
            """
            ﻿[GENERAL]
            ; comment
            // another comment
            Name : Case-insensitive
            LayeredHitSounds = 0

            [fonts]
            HitCircleOverlap: 12
            """.trimIndent()
        )

        assertEquals("Case-insensitive", ini.value("general", "NAME"))
        assertEquals("12", ini.value("FONTS", "hitcircleoverlap"))
        assertFalse(ini.boolean("General", "LayeredHitSounds", true))
    }

    @Test
    fun bothColonAndEqualsSeparatorsAreAccepted() {
        val ini = SkinIni.parse(
            """
            [General]
            Name: Colon
            Author = Equals
            [Colours]
            SliderBorder = 10, 20, 30, 128
            Combo1: 1, 2, 3
            """.trimIndent()
        )

        assertEquals("Colon", ini.value("General", "Name"))
        assertEquals("Equals", ini.value("General", "Author"))
        assertEquals(0x800A141E.toInt(), ini.colour("SliderBorder"))
    }

    @Test
    fun booleansAcceptCommonTruthyAndFalsyValues() {
        val ini = SkinIni.parse(
            """
            [General]
            TrueValue=yes
            FalseValue=off
            NumericTrue=1
            NumericFalse=0
            Invalid=maybe
            """.trimIndent()
        )

        assertTrue(ini.boolean("General", "TrueValue", false))
        assertFalse(ini.boolean("General", "FalseValue", true))
        assertTrue(ini.boolean("General", "NumericTrue", false))
        assertFalse(ini.boolean("General", "NumericFalse", true))
        assertTrue(ini.boolean("General", "Invalid", true))
        assertFalse(ini.boolean("General", "Missing", false))
    }

    @Test
    fun coloursUseArgbAndInvalidColoursAreRejected() {
        val ini = SkinIni.parse(
            """
            [Colours]
            SliderBorder=255, 0, 128, 64
            SliderTrackOverride=1, 2
            Combo1=255, 255, 0
            """.trimIndent()
        )

        assertEquals(0x40FF0080, ini.colour("SliderBorder"))
        assertNull(ini.colour("SliderTrackOverride"))
        assertEquals(0xFFFFFF00.toInt(), ini.colour("Combo1"))
    }

    @Test
    fun comboColoursAreOrderedNumericallyAndDeduplicated() {
        val ini = SkinIni.parse(
            """
            [Colours]
            Combo10=10, 20, 30
            Combo2=40, 50, 60
            Combo1=70, 80, 90
            Combo3=70, 80, 90
            """.trimIndent()
        )

        assertEquals(
            listOf(
                0xFF46505A.toInt(),
                0xFF28323C.toInt(),
                0xFF0A141E.toInt()
            ),
            ini.comboColours()
        )
    }
}
