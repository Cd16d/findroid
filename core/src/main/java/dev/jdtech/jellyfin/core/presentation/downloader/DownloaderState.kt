package dev.jdtech.jellyfin.core.presentation.downloader

import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.utils.download.DownloadStatus

data class DownloaderState(
    val status: DownloadStatus = DownloadStatus.NONE,
    val progress: Float = 0f,
    val errorText: UiText? = null,
    val extraInfo: String? = null,
) {
    val isDownloading: Boolean
        get() =
            status == DownloadStatus.PENDING ||
                status == DownloadStatus.RUNNING ||
                status == DownloadStatus.PAUSED
}
