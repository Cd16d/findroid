package dev.jdtech.jellyfin.film.presentation.downloads

import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueue
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidShow
import java.util.UUID

data class SeasonEpisodeGroup(
    val seasonNumber: Int,
    val headerTitle: String,
    val episodes: List<FindroidEpisode>,
)

data class ShowDownloadsState(
    val isLoading: Boolean = false,
    val show: FindroidShow? = null,
    val seasonGroups: List<SeasonEpisodeGroup> = emptyList(),
    val totalEpisodesCount: Int = 0,
    val totalDiskSizeFormatted: String = "0 B",
    val selectedEpisodeIds: Set<UUID> = emptySet(),
    val isSelectionMode: Boolean = false,
    val pendingDeletionIds: Map<UUID, Int> = emptyMap(),
    val hasSdCard: Boolean = false,
    val displayExtraInfo: Boolean = false,
    val activeDownloads: List<DownloadQueue.Entry> = emptyList(),
    val activeTransfers: Map<UUID, dev.jdtech.jellyfin.models.StorageTransferProgress> = emptyMap(),
    val error: Exception? = null,
) {
    val subtitle: String
        get() {
            val epStr = if (totalEpisodesCount == 1) "1 episode" else "$totalEpisodesCount episodes"
            return if (totalDiskSizeFormatted.isNotEmpty()) "$epStr • $totalDiskSizeFormatted" else epStr
        }

    val isEmpty: Boolean
        get() = !isLoading && totalEpisodesCount == 0
}
