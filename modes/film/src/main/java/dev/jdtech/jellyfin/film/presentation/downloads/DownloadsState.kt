package dev.jdtech.jellyfin.film.presentation.downloads

import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueue
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.StorageTransferProgress
import java.util.UUID

data class DownloadsState(
    val isLoading: Boolean = false,
    val movies: List<FindroidMovie> = emptyList(),
    val shows: List<DownloadedShowItem> = emptyList(),
    val activeDownloads: List<DownloadQueue.Entry> = emptyList(),
    val allQueueEntries: List<DownloadQueue.Entry> = emptyList(),
    val usedStorageFormatted: String = "0 B",
    val freeStorageFormatted: String = "0 B",
    val deviceUsedFormatted: String = "",
    val deviceTotalFormatted: String = "",
    val downloadedFraction: Float = 0f,
    val otherUsedFraction: Float = 0f,
    val freeFraction: Float = 1f,
    val hasSdCard: Boolean = false,
    val displayExtraInfo: Boolean = false,
    val selectedItemIds: Set<UUID> = emptySet(),
    val isSelectionMode: Boolean = false,
    val pendingDeletionIds: Map<UUID, Int> = emptyMap(),
    val isSmartDownloadsActive: Boolean = false,
    val activeTransfers: Map<UUID, StorageTransferProgress> = emptyMap(),
    val error: Exception? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading && movies.isEmpty() && shows.isEmpty() && activeDownloads.isEmpty()
}
