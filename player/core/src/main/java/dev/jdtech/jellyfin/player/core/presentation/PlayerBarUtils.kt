package dev.jdtech.jellyfin.player.core.presentation

import android.content.Context
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidSegmentType
import dev.jdtech.jellyfin.player.core.R
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import java.util.Locale

/**
 * Represents a segmented section of the media progress bar.
 *
 * @property startMs The start time of the slice in milliseconds.
 * @property endMs The end time of the slice in milliseconds.
 * @property segmentType The [FindroidSegmentType] associated with this slice, if any (e.g. intro,
 *   outro).
 * @property title The optional chapter or section title spanning this slice.
 */
data class BarSlice(
    val startMs: Long,
    val endMs: Long,
    val segmentType: FindroidSegmentType? = null,
    val title: String? = null,
    val color: Color? = null,
)

/**
 * Determines whether a [PlayerChapter] represents a specific [FindroidSegment] based on significant
 * time overlap.
 *
 * @param chapter The chapter being evaluated.
 * @param nextChapterStart The start position of the subsequent chapter, or Long.MAX_VALUE.
 * @param segments List of media segments for the current media item.
 * @param durationMs Total media duration in milliseconds.
 * @return The matched [FindroidSegment], or null if this chapter is standard content.
 */
fun findChapterSegment(
    chapter: PlayerChapter,
    nextChapterStart: Long,
    segments: List<FindroidSegment>,
    durationMs: Long = Long.MAX_VALUE,
): FindroidSegment? {
    val chStart = chapter.startPosition
    val chEnd =
        if (nextChapterStart != Long.MAX_VALUE && nextChapterStart > chStart) {
            nextChapterStart
        } else if (durationMs != Long.MAX_VALUE && durationMs > chStart) {
            durationMs
        } else {
            Long.MAX_VALUE
        }
    val chDuration = if (chEnd != Long.MAX_VALUE) (chEnd - chStart).coerceAtLeast(1L) else 0L

    for (seg in segments) {
        val segStart = seg.startTicks
        val segEnd = seg.endTicks

        val overlapStart = maxOf(chStart, segStart)
        val overlapEnd = if (chEnd != Long.MAX_VALUE) minOf(chEnd, segEnd) else segEnd
        val overlap = (overlapEnd - overlapStart).coerceAtLeast(0L)

        if (overlap <= 0L) continue

        // Associate chapter if it is predominantly within the segment (at least 65% overlap)
        if (chDuration > 0L) {
            val chapterCoverage = overlap.toFloat() / chDuration.toFloat()
            if (chapterCoverage >= 0.65f) {
                return seg
            }
        }
    }

    return null
}

fun findChapterSegmentType(
    chapter: PlayerChapter,
    nextChapterStart: Long,
    segments: List<FindroidSegment>,
    durationMs: Long = Long.MAX_VALUE,
): FindroidSegmentType? {
    return findChapterSegment(chapter, nextChapterStart, segments, durationMs)?.type
}

/**
 * Splits the media duration into contiguous [BarSlice] sections based on chapter boundaries and
 * media segments.
 *
 * @param durationMs Total media duration in milliseconds.
 * @param chapters List of [PlayerChapter] markers for the media item.
 * @param segments List of [FindroidSegment] intervals (intro, outro, recap, etc.).
 * @return A list of contiguous [BarSlice] objects covering the full duration without gaps.
 */
fun calculateBarSlices(
    durationMs: Long,
    chapters: List<PlayerChapter> = emptyList(),
    segments: List<FindroidSegment> = emptyList(),
): List<BarSlice> {
    if (durationMs <= 0L) return emptyList()

    val validChapters =
        chapters
            .filter { it.startPosition in 0..durationMs }
            .sortedBy { it.startPosition }
            .distinctBy { it.startPosition }
    val validSegments =
        segments
            .filter { it.startTicks < durationMs && it.endTicks > 0L }
            .sortedBy { it.startTicks }

    // Normalize chapter cuts: snap cuts within 3 seconds of the boundaries
    val chapterCuts = validChapters.map {
        val pos = it.startPosition.coerceIn(0L, durationMs)
        when {
            pos <= 3000L -> 0L
            pos >= durationMs - 3000L -> durationMs
            else -> pos
        }
    }

    // Snap segment cuts within 3 seconds of a chapter cut, and normalize boundaries
    val segmentCuts =
        validSegments
            .flatMap {
                val s = if (it.startTicks <= 3000L) 0L else it.startTicks.coerceIn(0L, durationMs)
                val e =
                    if (it.endTicks >= durationMs - 3000L) durationMs
                    else it.endTicks.coerceIn(0L, durationMs)
                listOf(s, e)
            }
            .map { segCut ->
                val nearestChapter = chapterCuts.minByOrNull { kotlin.math.abs(it - segCut) }
                if (nearestChapter != null && kotlin.math.abs(nearestChapter - segCut) <= 3000L) {
                    nearestChapter
                } else {
                    segCut
                }
            }

    val rawCuts = (listOf(0L, durationMs) + chapterCuts + segmentCuts).distinct().sorted()
    if (rawCuts.size <= 1) return listOf(BarSlice(startMs = 0L, endMs = durationMs))

    // Eliminate micro-slices smaller than 2 seconds between adjacent cuts
    val cuts = mutableListOf<Long>()
    for (cut in rawCuts) {
        if (cuts.isEmpty()) {
            cuts.add(cut)
        } else {
            val prev = cuts.last()
            if (cut - prev >= 2000L || cut == durationMs) {
                if (cut == durationMs && cut - prev < 2000L && cuts.size > 1) {
                    cuts[cuts.size - 1] = durationMs
                } else {
                    cuts.add(cut)
                }
            }
        }
    }
    if (cuts.size <= 1) return listOf(BarSlice(startMs = 0L, endMs = durationMs))

    val rawSlices = mutableListOf<BarSlice>()
    for (i in 0 until cuts.size - 1) {
        val start = cuts[i]
        val end = cuts[i + 1]
        if (end > start) {
            val matchingSegment = validSegments.firstOrNull { seg ->
                val segStart = if (seg.startTicks <= 3000L) 0L else seg.startTicks
                val segEnd = if (seg.endTicks >= durationMs - 3000L) durationMs else seg.endTicks
                val overlapStart = maxOf(start, segStart)
                val overlapEnd = minOf(end, segEnd)
                val overlap = overlapEnd - overlapStart
                val sliceDuration = end - start
                val segDuration = segEnd - segStart
                overlap > 0L &&
                    ((start >= segStart && end <= segEnd) ||
                        (segStart >= start && segEnd <= end) ||
                        overlap >= sliceDuration / 2 ||
                        (segDuration > 0L && overlap >= segDuration / 2))
            }
            val matchingChapterIndex = validChapters.indexOfLast {
                val chPos = if (it.startPosition <= 3000L) 0L else it.startPosition
                chPos <= start
            }
            val matchingChapter =
                if (matchingChapterIndex >= 0) validChapters[matchingChapterIndex] else null

            val effectiveSegmentType = matchingSegment?.type

            val sliceColor =
                when (val colorVal = matchingSegment?.color) {
                    0L -> null
                    null -> getSegmentColor(effectiveSegmentType)
                    else -> Color(colorVal)
                }

            rawSlices.add(
                BarSlice(
                    startMs = start,
                    endMs = end,
                    segmentType = effectiveSegmentType,
                    title = matchingChapter?.name,
                    color = sliceColor,
                )
            )
        }
    }

    // Merge adjacent slices sharing the same segmentType and chapter to prevent
    // duplicate/micro slices while preserving distinct chapters (e.g. Ending and Epilogue both
    // outro)
    val mergedSlices = mutableListOf<BarSlice>()
    for (slice in rawSlices) {
        val prev = mergedSlices.lastOrNull()
        if (prev != null && prev.segmentType == slice.segmentType && prev.title == slice.title) {
            mergedSlices[mergedSlices.size - 1] =
                prev.copy(
                    endMs = slice.endMs,
                    title = prev.title,
                    color = prev.color ?: slice.color,
                )
        } else {
            mergedSlices.add(slice)
        }
    }

    return mergedSlices
}

/**
 * Returns the distinctive UI accent [Color] assigned to each [FindroidSegmentType], or null if
 * unstyled.
 *
 * @param type The segment category, or null.
 * @return The corresponding [Color], or null for unknown / default segments.
 */
fun getSegmentColor(type: FindroidSegmentType?): Color? {
    return when (type) {
        FindroidSegmentType.INTRO -> Color(0xFF4CAF50) // Green
        FindroidSegmentType.OUTRO -> Color(0xFFFF9800) // Orange / Yellow
        FindroidSegmentType.RECAP -> Color(0xFF2196F3) // Blue
        FindroidSegmentType.PREVIEW -> Color(0xFF9C27B0) // Purple
        FindroidSegmentType.COMMERCIAL -> Color(0xFF795548) // Brown
        else -> null
    }
}

/**
 * Computes a user-facing label for the media position, combining chapter name and segment label if
 * present.
 *
 * @param posMs The current playback timestamp in milliseconds.
 * @param chapters List of [PlayerChapter] objects.
 * @param segments List of [FindroidSegment] objects.
 * @param context Android [Context] for resolving localized string resources.
 * @return A formatted section title string, or null if neither chapter nor segment applies.
 */
fun getSectionTitle(
    posMs: Long,
    chapters: List<PlayerChapter>,
    segments: List<FindroidSegment>,
    context: Context,
): String? {
    val chapterIndex = chapters.indexOfLast { posMs >= it.startPosition }
    val chapter = if (chapterIndex >= 0) chapters[chapterIndex] else null

    val segment = segments.firstOrNull { posMs in it.startTicks..it.endTicks }

    val segmentLabel =
        when (segment?.type) {
            FindroidSegmentType.INTRO -> context.getString(R.string.segment_intro)
            FindroidSegmentType.OUTRO -> context.getString(R.string.segment_outro)
            FindroidSegmentType.RECAP -> context.getString(R.string.segment_recap)
            FindroidSegmentType.PREVIEW -> context.getString(R.string.segment_preview)
            FindroidSegmentType.COMMERCIAL -> context.getString(R.string.segment_commercial)
            else -> null
        }

    val chapterLabel =
        if (chapter != null) {
            if (!chapter.name.isNullOrBlank()) {
                chapter.name
            } else {
                context.getString(R.string.chapter_title, chapterIndex + 1)
            }
        } else {
            null
        }

    return when {
        chapterLabel != null && segmentLabel != null -> {
            if (chapterLabel.equals(segmentLabel, ignoreCase = true)) {
                val fallback = context.getString(R.string.chapter_title, chapterIndex + 1)
                "$fallback • $segmentLabel"
            } else if (chapterLabel.contains(segmentLabel, ignoreCase = true)) {
                chapterLabel
            } else {
                "$chapterLabel • $segmentLabel"
            }
        }
        chapterLabel != null -> chapterLabel
        segmentLabel != null -> segmentLabel
        else -> null
    }
}

/**
 * Formats a duration in milliseconds to standard digital clock format (e.g. "04:15" or "1:22:45").
 *
 * @param millis Duration in milliseconds.
 * @return Formatted time string.
 */
fun formatDuration(millis: Long): String {
    val durationSeconds = (millis.coerceAtLeast(0L) / 1000)
    val hours = durationSeconds / 3600
    val minutes = (durationSeconds % 3600) / 60
    val seconds = durationSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

/** Calculates a progress fraction (0.0f ... 1.0f) given an X coordinate and component width. */
fun calculateProgressFromX(x: Float, width: Float): Float {
    if (width <= 0f) return 0f
    return (x / width).coerceIn(0f, 1f)
}

/** Renders a rounded rectangle path with individual corner radii on a [DrawScope]. */
fun DrawScope.drawCustomRoundRect(
    color: Color,
    topLeft: Offset,
    size: Size,
    topLeftRadius: CornerRadius = CornerRadius.Zero,
    topRightRadius: CornerRadius = CornerRadius.Zero,
    bottomRightRadius: CornerRadius = CornerRadius.Zero,
    bottomLeftRadius: CornerRadius = CornerRadius.Zero,
) {
    val path =
        Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(offset = topLeft, size = size),
                    topLeft = topLeftRadius,
                    topRight = topRightRadius,
                    bottomRight = bottomRightRadius,
                    bottomLeft = bottomLeftRadius,
                )
            )
        }
    drawPath(path = path, color = color)
}
