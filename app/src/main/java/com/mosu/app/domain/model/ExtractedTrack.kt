package com.mosu.app.domain.model

import java.io.File

data class ExtractedTrack(
    val audioFile: File,
    val coverFile: File?,
    val title: String,
    val artist: String,
    val difficultyName: String,
    val titleUnicode: String? = null,
    val artistUnicode: String? = null,
    val creator: String? = null
)
