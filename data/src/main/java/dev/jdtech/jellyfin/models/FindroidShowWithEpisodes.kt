package dev.jdtech.jellyfin.models

import androidx.room3.Embedded
import androidx.room3.Relation

data class FindroidShowWithEpisodes(
    @Embedded val show: FindroidShowDto,
    @Relation(parentColumns = ["id"], entityColumns = ["seriesId"])
    val episodes: List<FindroidEpisodeDto>,
)
