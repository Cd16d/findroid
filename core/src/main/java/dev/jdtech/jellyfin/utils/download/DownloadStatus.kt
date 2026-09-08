package dev.jdtech.jellyfin.utils.download

/**
 * Integer status constants for in-app OkHttp downloads. Values are identical to
 * [android.app.DownloadManager].STATUS_* for backward compatibility.
 */
object DownloadStatus {
    const val PENDING = 1
    const val RUNNING = 2
    const val PAUSED = 4
    const val SUCCESSFUL = 8
    const val FAILED = 16
}
