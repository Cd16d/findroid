package dev.jdtech.jellyfin.player.core.presentation

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.jdtech.jellyfin.core.presentation.theme.PaletteBlue
import dev.jdtech.jellyfin.core.presentation.theme.PaletteBrown
import dev.jdtech.jellyfin.core.presentation.theme.PaletteGreen
import dev.jdtech.jellyfin.core.presentation.theme.PaletteOrange
import dev.jdtech.jellyfin.core.presentation.theme.PalettePurple
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidSegmentType
import dev.jdtech.jellyfin.player.core.R
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import java.util.Locale

/**
 * Returns the string resource ID for the localized label of a [FindroidSegmentType], or null if
 * unlabeled.
 */
val FindroidSegmentType.labelRes: Int?
    @StringRes
    get() =
        when (this) {
            FindroidSegmentType.INTRO -> R.string.segment_intro
            FindroidSegmentType.OUTRO -> R.string.segment_outro
            FindroidSegmentType.RECAP -> R.string.segment_recap
            FindroidSegmentType.PREVIEW -> R.string.segment_preview
            FindroidSegmentType.COMMERCIAL -> R.string.segment_commercial
            FindroidSegmentType.UNKNOWN -> null
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
        FindroidSegmentType.INTRO -> PaletteGreen
        FindroidSegmentType.OUTRO -> PaletteOrange
        FindroidSegmentType.RECAP -> PaletteBlue
        FindroidSegmentType.PREVIEW -> PalettePurple
        FindroidSegmentType.COMMERCIAL -> PaletteBrown
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

    val segmentLabel = segment?.type?.labelRes?.let { context.getString(it) }

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
