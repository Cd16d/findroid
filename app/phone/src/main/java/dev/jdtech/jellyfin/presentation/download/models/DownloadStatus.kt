package dev.jdtech.jellyfin.presentation.download.models

enum class DownloadStatus {
    DOWNLOADED,
    DOWNLOADING,
    CONVERTING,
    PENDING,
    FAILED,
    TRANSFERRING,
}
