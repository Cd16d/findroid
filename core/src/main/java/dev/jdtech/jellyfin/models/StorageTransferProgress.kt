package dev.jdtech.jellyfin.models

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class StorageTransferProgress(
    val itemId: UUID,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val progress: Float = 0f,
)
