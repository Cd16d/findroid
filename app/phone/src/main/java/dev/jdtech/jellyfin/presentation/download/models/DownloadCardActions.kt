package dev.jdtech.jellyfin.presentation.download.models

import androidx.compose.runtime.Immutable

@Immutable
data class DownloadCardActions(
    val onClick: () -> Unit = {},
    val onLongClick: () -> Unit = {},
    val onSwipeDelete: () -> Unit = {},
    val onPauseDownload: () -> Unit = {},
    val onResumeDownload: () -> Unit = {},
    val onRetryDownload: () -> Unit = {},
    val onUndoDelete: () -> Unit = {},
)
