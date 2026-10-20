package dev.jdtech.jellyfin.player.core.domain.utils

import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidSegmentType
import dev.jdtech.jellyfin.player.core.R
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import timber.log.Timber

/** Utility functions for handling media segments like intros and outros. */
object SegmentUtils {

    /**
     * Fetches media segments for the given item from the Jellyfin repository.
     *
     * @param itemId The UUID of the media item to fetch segments for.
     * @param repository The repository to fetch segments from.
     * @param colorResolver Optional resolver for segment colors given a [FindroidSegmentType].
     * @return A list of [FindroidSegment] objects, or an empty list if fetching fails.
     */
    suspend fun getSegments(
        itemId: UUID,
        repository: JellyfinRepository,
        colorResolver: ((FindroidSegmentType) -> Long?)? = null,
    ): List<FindroidSegment> {
        return try {
            val segments = repository.getSegments(itemId)
            if (colorResolver != null) {
                segments.map { segment ->
                    segment.copy(color = segment.color ?: colorResolver(segment.type))
                }
            } else {
                segments
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch segments for item: $itemId")
            emptyList()
        }
    }

    /**
     * Determines if the player should skip directly to the next episode instead of just skipping
     * the segment. This applies to outro segments that end near the end of the media duration.
     *
     * @param segment The current active segment.
     * @param hasNextMediaItem Whether there is another media item following the current one.
     * @param playerDurationMillis The total duration of the current media in milliseconds.
     * @param nextEpisodeThreshold The threshold in milliseconds from the end of the media to
     *   trigger a next episode skip.
     * @return True if skipping should transition to the next episode, false otherwise.
     */
    fun shouldSkipToNextEpisode(
        segment: FindroidSegment,
        hasNextMediaItem: Boolean,
        playerDurationMillis: Long,
        nextEpisodeThreshold: Long,
    ): Boolean {
        return segment.type == FindroidSegmentType.OUTRO &&
            hasNextMediaItem &&
            segment.endTicks > (playerDurationMillis - nextEpisodeThreshold)
    }

    /**
     * Returns the string resource ID for the skip button text based on the segment type and skip
     * behavior.
     *
     * @param segment The segment to be skipped.
     * @param shouldSkipToNextEpisode Whether the skip action will transition to the next episode.
     * @return The resource ID of the string to display on the skip button.
     */
    fun getSkipButtonTextStringId(segment: FindroidSegment, shouldSkipToNextEpisode: Boolean): Int {
        if (shouldSkipToNextEpisode) {
            return R.string.player_controls_next_episode
        }

        return when (segment.type) {
            FindroidSegmentType.INTRO -> R.string.player_controls_skip_intro
            FindroidSegmentType.OUTRO -> R.string.player_controls_skip_outro
            FindroidSegmentType.RECAP -> R.string.player_controls_skip_recap
            FindroidSegmentType.COMMERCIAL -> R.string.player_controls_skip_commercial
            FindroidSegmentType.PREVIEW -> R.string.player_controls_skip_preview
            else -> R.string.player_controls_skip_unknown
        }
    }
}
