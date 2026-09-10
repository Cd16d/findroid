package dev.jdtech.jellyfin.models

import androidx.room.Embedded
import androidx.room.Relation

data class FindroidShowWithEpisodes(
    @Embedded val show: FindroidShowDto,
    @Relation(parentColumn = "id", entityColumn = "seriesId")
    val episodes: List<FindroidEpisodeDto>,
)
