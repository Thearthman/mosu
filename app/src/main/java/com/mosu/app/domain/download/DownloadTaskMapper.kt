package com.mosu.app.domain.download

import com.mosu.app.data.db.DownloadTaskEntity

fun DownloadTaskEntity.toDownloadTask(): DownloadTask {
    return DownloadTask(
        setId = setId,
        title = title,
        artist = artist,
        status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.Error),
        progress = progress,
        statusMessage = statusMessage,
        creator = creator,
        genreId = genreId,
        coverUrl = coverUrl,
        errorMessage = errorMessage
    )
}
