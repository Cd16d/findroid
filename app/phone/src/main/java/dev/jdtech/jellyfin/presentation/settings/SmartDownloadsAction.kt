package dev.jdtech.jellyfin.presentation.settings

sealed interface SmartDownloadsAction {
    data class SetSmartDownloadNextEpisode(val enabled: Boolean) : SmartDownloadsAction
    data class SetNextEpisodesCount(val count: Int) : SmartDownloadsAction
    data class SetStorageLimitGb(val limitGb: Int) : SmartDownloadsAction
    data class SetAutoDeleteWatched(val enabled: Boolean) : SmartDownloadsAction
}
