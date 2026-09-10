package dev.jdtech.jellyfin.models

import java.util.UUID

data class StorageTransferProgress(
    val itemId: UUID,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val progress: Float = 0f,
)
