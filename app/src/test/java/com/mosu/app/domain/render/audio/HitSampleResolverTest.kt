package com.mosu.app.domain.render.audio

import com.mosu.app.domain.render.RenderSample
import com.mosu.app.domain.render.RenderSampleBank
import org.junit.Assert.assertEquals
import org.junit.Test

class HitSampleResolverTest {
    @Test
    fun userSkinTriesBankPrefixedNameBeforeUnprefixedName() {
        val candidates = HitSampleResolver.userSkinCandidates(
            sample(sampleName = "hitnormal", bank = RenderSampleBank.SOFT)
        )

        assertEquals(
            listOf(
                "soft-hitnormal.wav",
                "soft-hitnormal.mp3",
                "soft-hitnormal.ogg",
                "hitnormal.wav",
                "hitnormal.mp3",
                "hitnormal.ogg"
            ),
            candidates
        )
    }

    @Test
    fun sampleExtensionsAreTriedInWavMp3OggOrder() {
        val candidates = HitSampleResolver.userSkinCandidates(
            sample(sampleName = "hitclap", bank = RenderSampleBank.DRUM)
        )

        assertEquals(
            listOf(
                "drum-hitclap.wav",
                "drum-hitclap.mp3",
                "drum-hitclap.ogg",
                "hitclap.wav",
                "hitclap.mp3",
                "hitclap.ogg"
            ),
            candidates
        )
    }

    @Test
    fun userSkinsIgnoreBeatmapCustomFilenameAndSampleIndex() {
        val candidates = HitSampleResolver.userSkinCandidates(
            sample(
                sampleName = "hitwhistle",
                bank = RenderSampleBank.NORMAL,
                customSampleIndex = 4,
                customFilename = "MyCustomClap.wav"
            )
        )

        assertEquals(
            listOf(
                "normal-hitwhistle.wav",
                "normal-hitwhistle.mp3",
                "normal-hitwhistle.ogg",
                "hitwhistle.wav",
                "hitwhistle.mp3",
                "hitwhistle.ogg"
            ),
            candidates
        )
    }

    @Test
    fun bundledFallbackPrefersCustomFilenameThenIndexedThenBankThenUniversal() {
        val candidates = HitSampleResolver.bundledAssetCandidates(
            sample(
                sampleName = "hitnormal",
                bank = RenderSampleBank.NORMAL,
                customSampleIndex = 2,
                customFilename = "custom-clap"
            )
        )

        assertEquals(
            listOf(
                "hitsounds/custom-clap.wav",
                "hitsounds/custom-clap.mp3",
                "hitsounds/custom-clap.ogg",
                "hitsounds/normal-hitnormal2.wav",
                "hitsounds/normal-hitnormal2.mp3",
                "hitsounds/normal-hitnormal2.ogg",
                "hitsounds/normal-hitnormal.wav",
                "hitsounds/normal-hitnormal.mp3",
                "hitsounds/normal-hitnormal.ogg",
                "hitsounds/hitnormal.wav",
                "hitsounds/hitnormal.mp3",
                "hitsounds/hitnormal.ogg"
            ),
            candidates
        )
    }

    @Test
    fun customFilenameWithAnExtensionIsNotGivenAnotherExtension() {
        val candidates = HitSampleResolver.bundledAssetCandidates(
            sample(
                sampleName = "hitfinish",
                bank = RenderSampleBank.SOFT,
                customFilename = "Finish.OGG"
            )
        )

        assertEquals("hitsounds/Finish.OGG", candidates.first())
        assertEquals("hitsounds/finish.ogg", candidates[1])
    }

    @Test
    fun pathsAreReducedToCasePreservingBasenames() {
        val userCandidates = HitSampleResolver.userSkinCandidates(
            sample(sampleName = "Gameplay/HitNormal.wav")
        )
        val bundledCandidates = HitSampleResolver.bundledAssetCandidates(
            sample(sampleName = "Gameplay\\HitNormal.wav")
        )

        assertEquals("normal-HitNormal.wav", userCandidates.first())
        assertEquals("hitsounds/normal-HitNormal.wav", bundledCandidates.first())
    }

    private fun sample(
        sampleName: String,
        bank: RenderSampleBank = RenderSampleBank.NORMAL,
        customSampleIndex: Int = 0,
        customFilename: String? = null
    ) = RenderSample(
        sampleName = sampleName,
        bank = bank,
        customSampleIndex = customSampleIndex,
        customFilename = customFilename
    )
}
