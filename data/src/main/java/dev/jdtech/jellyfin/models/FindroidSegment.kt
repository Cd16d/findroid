package dev.jdtech.jellyfin.models

import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType

/** Specifies the classification of a media segment in playback (e.g. intro, outro, recap). */
enum class FindroidSegmentType {
    /** Opening credits or introductory segment of the media item. */
    INTRO,

    /** End credits or concluding outro segment of the media item. */
    OUTRO,

    /** Recap summarizing previous events or earlier episodes. */
    RECAP,

    /** Teaser or preview of upcoming scenes or subsequent episodes. */
    PREVIEW,

    /** Advertisement or commercial break segment. */
    COMMERCIAL,

    /** Unrecognized or unspecified segment type. */
    UNKNOWN,
}

private fun MediaSegmentType.toFindroidSegmentType(): FindroidSegmentType =
    when (this) {
        MediaSegmentType.UNKNOWN -> FindroidSegmentType.UNKNOWN
        MediaSegmentType.INTRO -> FindroidSegmentType.INTRO
        MediaSegmentType.OUTRO -> FindroidSegmentType.OUTRO
        MediaSegmentType.RECAP -> FindroidSegmentType.RECAP
        MediaSegmentType.PREVIEW -> FindroidSegmentType.PREVIEW
        MediaSegmentType.COMMERCIAL -> FindroidSegmentType.COMMERCIAL
    }

/**
 * Represents a marked segment within a media stream, such as an intro or credits.
 *
 * @property type The [FindroidSegmentType] classifying this segment.
 * @property startTicks The start timestamp of the segment in playback ticks or milliseconds.
 * @property endTicks The end timestamp of the segment in playback ticks or milliseconds.
 * @property color Optional custom color hex (Long) for this segment.
 */
data class FindroidSegment(
    val type: FindroidSegmentType,
    val startTicks: Long,
    val endTicks: Long,
    val color: Long? = null,
)

/** Converts a database [FindroidSegmentDto] to a domain [FindroidSegment]. */
fun FindroidSegmentDto.toFindroidSegment(): FindroidSegment {
    return FindroidSegment(type = type, startTicks = startTicks, endTicks = endTicks)
}

/**
 * Converts a Jellyfin API [MediaSegmentDto] to a domain [FindroidSegment], converting ticks to
 * milliseconds.
 */
fun MediaSegmentDto.toFindroidSegment(): FindroidSegment {
    return FindroidSegment(
        type = type.toFindroidSegmentType(),
        startTicks = startTicks / 10000,
        endTicks = endTicks / 10000,
    )
}
