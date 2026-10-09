package dev.jdtech.jellyfin.core.presentation.downloader

import dev.jdtech.jellyfin.models.FindroidItem

sealed interface DownloaderAction {
    data class Download(
        val item: FindroidItem,
        val storageIndex: Int = -1,
        val presetId: String? = null,
        val downloadExternalAudio: Boolean = false,
        val audioStreamIndex: Int? = null,
    ) : DownloaderAction

    data class DownloadMany(
        val items: List<FindroidItem>,
        val storageIndex: Int = -1,
        val presetId: String? = null,
        val downloadExternalAudio: Boolean = false,
        val audioStreamIndex: Int? = null,
    ) : DownloaderAction

    data class DeleteDownload(val item: FindroidItem) : DownloaderAction

    data class DeleteDownloadMany(val items: List<FindroidItem>) : DownloaderAction

    data class CancelDownload(val item: FindroidItem) : DownloaderAction

    data class CancelDownloadMany(val items: List<FindroidItem> = emptyList()) : DownloaderAction
}
