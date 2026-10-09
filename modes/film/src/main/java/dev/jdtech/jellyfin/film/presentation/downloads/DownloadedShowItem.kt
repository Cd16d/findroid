package dev.jdtech.jellyfin.film.presentation.downloads

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidShow

data class DownloadedShowItem(
    val show: FindroidShow,
    val episodes: List<FindroidEpisode>,
    val totalDiskSize: Long,
    val seasonsFormatted: String,
)
