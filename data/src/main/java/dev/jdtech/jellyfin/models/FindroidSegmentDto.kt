package dev.jdtech.jellyfin.models

import androidx.room3.Entity
import java.util.UUID

/**
 * Room database entity storing a media segment for a particular item.
 *
 * @property itemId The unique identifier of the media item.
 * @property type The [FindroidSegmentType] of the segment (intro, outro, etc.).
 * @property startTicks The start time of the segment in playback ticks or milliseconds.
 * @property endTicks The end time of the segment in playback ticks or milliseconds.
 */
@Entity(tableName = "segments", primaryKeys = ["itemId", "type"])
data class FindroidSegmentDto(
    val itemId: UUID,
    val type: FindroidSegmentType,
    val startTicks: Long,
    val endTicks: Long,
)

/**
 * Converts a domain [FindroidSegment] into a database [FindroidSegmentDto] associated with
 * [itemId].
 *
 * @param itemId The unique identifier of the item the segment belongs to.
 */
fun FindroidSegment.toFindroidSegmentsDto(itemId: UUID): FindroidSegmentDto {
    return FindroidSegmentDto(
        itemId = itemId,
        type = type,
        startTicks = startTicks,
        endTicks = endTicks,
    )
}
