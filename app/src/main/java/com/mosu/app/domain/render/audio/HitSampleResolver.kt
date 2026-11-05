package com.mosu.app.domain.render.audio

import com.mosu.app.domain.render.RenderSample
import com.mosu.app.domain.render.RenderSampleBank
import java.util.Locale

/**
 * Pure filename resolution for legacy osu! hit samples.
 *
 * Imported user skins deliberately ignore the beatmap's custom sample index
 * and explicit file name, matching ppy/osu!'s user-skin lookup. Each logical
 * name is tried as WAV, MP3, then OGG.
 */
internal object HitSampleResolver {
    private val sampleExtensions = listOf("wav", "mp3", "ogg")

    fun userSkinCandidates(sample: RenderSample): List<String> {
        val sampleName = sample.sampleName.toBaseName()
        if (sampleName.isEmpty()) return emptyList()
        val stem = sampleName.substringBeforeLast('.', sampleName)
        if (stem.isEmpty()) return emptyList()
        return linkedSetOf(
            "${sample.bank.assetPrefix}-$stem",
            stem
        ).flatMap { name -> name.withSampleExtensions() }
    }

    fun bundledAssetCandidates(sample: RenderSample): List<String> {
        val names = linkedSetOf<String>()
        val sampleName = sample.sampleName.toBaseName()
        val sampleStem = sampleName.substringBeforeLast('.', sampleName)

        sample.customFilename
            ?.toBaseName()
            ?.takeIf { it.isNotEmpty() }
            ?.let { names.addAll(it.withSampleExtensions()) }

        if (sampleName.isNotEmpty()) {
            val bankPrefix = sample.bank.assetPrefix
            val index = sample.customSampleIndex.coerceAtLeast(0)
            if (index > 0) {
                names.addAll("$bankPrefix-$sampleStem$index".withSampleExtensions())
            }
            names.addAll("$bankPrefix-$sampleStem".withSampleExtensions())
            names.addAll(sampleStem.withSampleExtensions())
        }

        return names
            .flatMap { name ->
                val lowerName = name.lowercase(Locale.ROOT)
                if (lowerName == name) listOf(name) else listOf(name, lowerName)
            }
            .distinct()
            .map { "$ASSET_ROOT/$it" }
    }

    private fun String.toBaseName(): String {
        return trim()
            .replace('\\', '/')
            .substringAfterLast('/')
    }

    private fun String.withSampleExtensions(): List<String> {
        val extension = substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension in sampleExtensions) return listOf(this)
        return sampleExtensions.map { "$this.$it" }
    }

    private const val ASSET_ROOT = "hitsounds"
}

internal val RenderSampleBank.assetPrefix: String
    get() = when (this) {
        RenderSampleBank.NORMAL -> "normal"
        RenderSampleBank.SOFT -> "soft"
        RenderSampleBank.DRUM -> "drum"
    }
