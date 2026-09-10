package dev.jdtech.jellyfin.presentation.download.models

import androidx.compose.runtime.Immutable

@Immutable
data class DownloadEpisodeTileState(
    val status: DownloadStatus = DownloadStatus.DOWNLOADED,
    val downloadProgress: Float = 0f,
    val playbackProgress: Float = 0f,
    val isSelected: Boolean = false,
    val isSelectionMode: Boolean = false,
    val sizeBytes: Long = 0L,
    val durationTicks: Long = 0L,
    val downloadSpeedBytesPerSec: Long = 0L,
    val etaSeconds: Long? = null,
    val downloadedSizeBytes: Long = 0L,
    val isPaused: Boolean = false,
    val pendingDeletionSeconds: Int? = null,
    val displayExtraInfo: Boolean = false,
)
