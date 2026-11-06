package dev.jdtech.jellyfin.player.core.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.presentation.theme.CentralShapes
import dev.jdtech.jellyfin.core.presentation.theme.PlayerOverlayBackground
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.domain.models.Trickplay
import kotlin.math.roundToInt

private val ScrubberCardShape = CentralShapes.MediumCard

/**
 * Shared trickplay scrubber overlay for both Phone and Android TV player interfaces.
 *
 * Renders a thumbnail preview card for the current scrub position with source rect clipping from
 * the whole-sheet bitmap, accompanied by chapter/segment pills and formatted timestamp indicators.
 *
 * @param trickplay Active [Trickplay] image frames manager.
 * @param currentPositionMs Current playback or scrubbed position in milliseconds.
 * @param durationMs Total media duration in milliseconds.
 * @param modifier Optional [Modifier] applied to the card column.
 * @param segments Media [FindroidSegment] intervals (intro, outro, etc.).
 * @param chapters Media [PlayerChapter] markers.
 * @param seekDeltaMs Relative seek difference in milliseconds (e.g. +10000 for +10s), or 0L.
 * @param videoAspectRatio Video aspect ratio used to calculate preview card dimensions.
 * @param cardHeight Fixed height of the thumbnail card container (default 110.dp on phone, 185.dp
 *   on TV).
 */
@Composable
fun TrickplayScrubber(
    trickplay: Trickplay?,
    currentPositionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
    segments: List<FindroidSegment> = emptyList(),
    chapters: List<PlayerChapter> = emptyList(),
    seekDeltaMs: Long = 0L,
    videoAspectRatio: Float? = null,
    cardHeight: Dp = 110.dp,
) {
    val context = LocalContext.current
    val safePosMs = currentPositionMs.coerceAtLeast(0L)

    val cardWidth =
        remember(trickplay, videoAspectRatio, cardHeight) {
            calculateTrickplayCardWidth(trickplay, videoAspectRatio, cardHeight)
        }

    val matchingSegment =
        remember(safePosMs, segments) {
            segments.firstOrNull { seg -> safePosMs in seg.startTicks..seg.endTicks }
        }
    val sectionTitle =
        remember(safePosMs, chapters, segments, context) {
            getSectionTitle(safePosMs, chapters, segments, context)
        }

    val segmentColor =
        when (val matchingColor = matchingSegment?.color) {
            0L -> null
            null -> getSegmentColor(matchingSegment?.type)
            else -> Color(matchingColor)
        }
    val cardBorderColor = segmentColor ?: MaterialTheme.colorScheme.primary

    val deltaSeconds = (seekDeltaMs.toFloat() / 1000f).roundToInt()
    val deltaString =
        remember(deltaSeconds) {
            if (deltaSeconds == 0) null
            else if (deltaSeconds > 0) "+${deltaSeconds}s" else "${deltaSeconds}s"
        }

    val showThumbnail = trickplay != null && trickplay.hasThumbnails

    Column(
        modifier = modifier.width(cardWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top Chapter / Segment pill (if available)
        if (!sectionTitle.isNullOrBlank()) {
            Box(
                modifier =
                    Modifier.clip(CircleShape)
                        .background(PlayerOverlayBackground.copy(alpha = 0.85f))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = sectionTitle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = segmentColor ?: Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Center Trickplay Thumbnail Card
        if (showThumbnail) {
            Box(
                modifier =
                    Modifier.width(cardWidth)
                        .height(cardHeight)
                        .clip(ScrubberCardShape)
                        .background(Color.Black)
                        .border(width = 3.dp, color = cardBorderColor, shape = ScrubberCardShape),
                contentAlignment = Alignment.Center,
            ) {
                TrickplayThumbnail(
                    trickplay = trickplay,
                    positionMs = safePosMs,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Bottom Pill: [ 14:30  (+10s) ]
        Box(
            modifier =
                Modifier.clip(CircleShape)
                    .background(PlayerOverlayBackground.copy(alpha = 0.85f))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = formatDuration(safePosMs),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )

                if (deltaString != null) {
                    Text(
                        text = "($deltaString)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = segmentColor ?: MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
