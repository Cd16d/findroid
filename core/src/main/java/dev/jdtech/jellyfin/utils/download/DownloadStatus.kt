package dev.jdtech.jellyfin.utils.download

/** Status of an in-app OkHttp download task. */
enum class DownloadStatus {
    NONE,
    PENDING,
    RUNNING,
    PAUSED,
    SUCCESSFUL,
    FAILED,
}
