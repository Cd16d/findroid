package dev.jdtech.jellyfin.presentation.player.components

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.presentation.formatDuration
import dev.jdtech.jellyfin.player.core.presentation.getSectionTitle
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Bottom player control section comprising metadata badges (timestamps, chapter title, end time)
 * and the [VideoPlayerSeekBar].
 *
 * @param state Active [VideoPlayerState] controlling player interactions.
 * @param onSeek Callback invoked when committing a seek action, passing target progress fraction.
 * @param contentProgress Current played [Duration].
 * @param contentDuration Total media [Duration].
 * @param modifier Optional [Modifier] applied to the seeker container.
 * @param chapters List of [PlayerChapter] markers.
 * @param segments List of [FindroidSegment] intervals.
 * @param isScrubbing Whether seeking is actively in progress.
 * @param scrubPositionMs Scrub target position in milliseconds.
 * @param onScrubbingChanged Callback reporting scrub status, scrub timestamp in ms, and touch X
 *   offset.
 * @param onOpenChapters Optional callback to navigate to the chapters sheet/panel.
 */
@Composable
fun VideoPlayerSeeker(
    state: VideoPlayerState,
    onSeek: (Float) -> Unit,
    contentProgress: Duration,
    contentDuration: Duration,
    modifier: Modifier = Modifier,
    chapters: List<PlayerChapter> = emptyList(),
    segments: List<FindroidSegment> = emptyList(),
    isScrubbing: Boolean = false,
    scrubPositionMs: Long = 0L,
    onScrubbingChanged: (isScrubbing: Boolean, scrubPositionMs: Long, touchX: Float) -> Unit =
        { _, _, _ ->
        },
    onOpenChapters: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val rawDurationMs = contentDuration.inWholeMilliseconds
    val durationMs = if (rawDurationMs > 0L) rawDurationMs else 0L
    val rawProgressMs = contentProgress.inWholeMilliseconds
    val progressMs = if (rawProgressMs > 0L) rawProgressMs else 0L

    val displayedProgressMs = if (isScrubbing) scrubPositionMs.coerceAtLeast(0L) else progressMs
    val contentProgressString = formatDuration(displayedProgressMs)
    val contentDurationString = formatDuration(durationMs)

    val sectionTitle =
        remember(displayedProgressMs, chapters, segments) {
            getSectionTitle(displayedProgressMs, chapters, segments, context)
        }

    val remainingMs =
        if (durationMs > 0L) (durationMs - displayedProgressMs).coerceAtLeast(0L) else 0L
    val formattedEndTime =
        remember(remainingMs, context) {
            if (durationMs > 0L) {
                val endTimeMillis = System.currentTimeMillis() + remainingMs
                DateFormat.getTimeFormat(context).format(Date(endTimeMillis))
            } else {
                null
            }
        }
    val endsAtText = formattedEndTime?.let { stringResource(PlayerR.string.ends_at, it) }

    val pillBackground = Color(0xFF262626).copy(alpha = 0.75f)
    val pillBorder = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))

    val pillsAlpha by
        animateFloatAsState(
            targetValue = if (isScrubbing) 0f else 1f,
            animationSpec = tween(durationMillis = 180),
            label = "seekerPillsAlpha",
        )

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(36.dp).graphicsLayer { alpha = pillsAlpha }) {
            Row(
                modifier =
                    Modifier.align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .padding(end = if (endsAtText != null) 90.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Pill 1: Current progress • Content duration
                Box(
                    modifier =
                        Modifier.clip(CircleShape)
                            .background(pillBackground)
                            .border(pillBorder, CircleShape)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = contentProgressString,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isScrubbing) FontWeight.Bold else FontWeight.SemiBold,
                            color =
                                if (isScrubbing) MaterialTheme.colorScheme.primary else Color.White,
                        )
                        Box(
                            modifier =
                                Modifier.size(4.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.6f))
                        )
                        Text(
                            text = contentDurationString,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }

                // Pill 2: Chapter Title (clickable if chapters are available)
                if (!sectionTitle.isNullOrBlank()) {
                    if (chapters.isNotEmpty() && onOpenChapters != null) {
                        Box(
                            modifier =
                                Modifier.weight(1f, fill = false)
                                    .clip(CircleShape)
                                    .background(pillBackground)
                                    .border(pillBorder, CircleShape)
                                    .clickable {
                                        state.resetTimeout()
                                        onOpenChapters()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = sectionTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    painter = painterResource(id = CoreR.drawable.ic_arrow_right),
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = Color.White.copy(alpha = 0.85f),
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier =
                                Modifier.weight(1f, fill = false)
                                    .clip(CircleShape)
                                    .background(pillBackground)
                                    .border(pillBorder, CircleShape)
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = sectionTitle,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // Pill 3: Ends at HH:mm (aligned bottom right above seekbar, TV-style)
            if (endsAtText != null) {
                Box(
                    modifier =
                        Modifier.align(Alignment.CenterEnd)
                            .clip(CircleShape)
                            .background(pillBackground)
                            .border(pillBorder, CircleShape)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = endsAtText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }

        val progressFraction =
            if (durationMs > 0L) {
                (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }

        VideoPlayerSeekBar(
            progress = progressFraction,
            onSeek = onSeek,
            state = state,
            durationMs = durationMs,
            chapters = chapters,
            segments = segments,
            isScrubbing = isScrubbing,
            onScrubbingChanged = onScrubbingChanged,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun VideoPlayerSeekerPreview() {
    FindroidTheme {
        val state = rememberVideoPlayerState()
        VideoPlayerSeeker(
            state = state,
            onSeek = {},
            contentProgress = 14.minutes,
            contentDuration = 45.minutes,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun VideoPlayerSeekerWithChaptersPreview() {
    FindroidTheme {
        val state = rememberVideoPlayerState()
        VideoPlayerSeeker(
            state = state,
            onSeek = {},
            contentProgress = 2.minutes,
            contentDuration = 24.minutes,
            chapters =
                listOf(
                    PlayerChapter(name = "Prologue", startPosition = 0L),
                    PlayerChapter(name = "Main Story", startPosition = 180_000L),
                ),
            onOpenChapters = {},
        )
    }
}
