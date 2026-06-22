package com.mosu.app.utils

import android.net.Uri
import java.io.File

object MediaPathUtils {
    fun asUri(path: String?): Uri? {
        if (path.isNullOrBlank()) return null
        return if (path.startsWith("content://") || path.startsWith("file://")) {
            Uri.parse(path)
        } else {
            Uri.fromFile(File(path))
        }
    }

    fun asImageModel(path: String?, fallbackUrl: String? = null): Any? {
        return asUri(path) ?: fallbackUrl
    }
}
