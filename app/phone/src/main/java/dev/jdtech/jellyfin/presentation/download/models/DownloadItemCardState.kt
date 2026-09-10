package dev.jdtech.jellyfin.presentation.download.models

import androidx.compose.runtime.Immutable

@Immutable
data class DownloadItemCardState(
    val title: String = "",
    val metadataText: String = "",
    val sizeBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.DOWNLOADED,
    val downloadProgress: Float = 0f,
    val playbackProgress: Float = 0f,
    val isSelected: Boolean = false,
    val isSelectionMode: Boolean = false,
    val displayExtraInfo: Boolean = true,
    val isPaused: Boolean = false,
    val pendingDeletionSeconds: Int? = null,
)
