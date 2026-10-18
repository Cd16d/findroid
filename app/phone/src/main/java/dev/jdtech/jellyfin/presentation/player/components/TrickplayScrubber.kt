package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.domain.models.Trickplay
import dev.jdtech.jellyfin.player.core.domain.models.intervalMs
import dev.jdtech.jellyfin.player.core.presentation.formatDuration
import dev.jdtech.jellyfin.player.core.presentation.getSectionTitle
import dev.jdtech.jellyfin.player.core.presentation.getSegmentColor
import kotlin.math.roundToInt

private val ScrubberCardShape = RoundedCornerShape(16.dp)

fun calculateTrickplayCardWidth(
    trickplay: Trickplay?,
    videoAspectRatio: Float?,
    baseHeight: Dp = 110.dp,
): Dp {
    val sampleBitmap = trickplay?.images?.firstOrNull()
    val aspect =
        if (sampleBitmap != null && sampleBitmap.height > 0) {
            sampleBitmap.width.toFloat() / sampleBitmap.height.toFloat()
        } else if (videoAspectRatio != null && videoAspectRatio > 0f) {
            videoAspectRatio
        } else {
            16f / 9f
        }
    return (baseHeight.value * aspect.coerceIn(0.75f, 2.75f)).dp
}

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
    val safeDurationMs = durationMs.coerceAtLeast(0L)

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

    val segmentColor = getSegmentColor(matchingSegment?.type)
    val cardBorderColor = segmentColor ?: MaterialTheme.colorScheme.primary

    val deltaSeconds = (seekDeltaMs.toFloat() / 1000f).roundToInt()
    val deltaString =
        remember(deltaSeconds) {
            if (deltaSeconds == 0) null
            else if (deltaSeconds > 0) "+${deltaSeconds}s" else "${deltaSeconds}s"
        }

    Column(
        modifier = modifier.width(cardWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top Chapter / Segment pill (if available)
        if (!sectionTitle.isNullOrBlank()) {
            Box(
                modifier =
                    Modifier.clip(CircleShape)
                        .background(Color(0xFF262626).copy(alpha = 0.85f))
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
        Box(
            modifier =
                Modifier.width(cardWidth)
                    .height(cardHeight)
                    .clip(ScrubberCardShape)
                    .background(Color.Black)
                    .border(width = 3.dp, color = cardBorderColor, shape = ScrubberCardShape),
            contentAlignment = Alignment.Center,
        ) {
            if (trickplay != null && trickplay.images.isNotEmpty()) {
                val intervalMs = trickplay.intervalMs
                val maxDurationCount =
                    if (safeDurationMs > 0L && intervalMs > 0L) {
                        ((safeDurationMs / intervalMs).toInt() + 1).coerceAtLeast(1)
                    } else {
                        trickplay.images.size
                    }
                val totalImages = minOf(trickplay.images.size, maxDurationCount)
                val imageIndex =
                    if (intervalMs > 0) {
                        (safePosMs / intervalMs).toInt().coerceIn(0, totalImages - 1)
                    } else 0

                val bitmap = trickplay.images.getOrNull(imageIndex)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bottom Pill: [ 14:30  (+10s) ]
        Box(
            modifier =
                Modifier.clip(CircleShape)
                    .background(Color(0xFF262626).copy(alpha = 0.85f))
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
