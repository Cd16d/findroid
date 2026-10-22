package dev.jdtech.jellyfin.presentation.cast.components

import android.text.format.DateFormat
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.theme.PlayerOverlayBackground
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.player.cast.models.CastPlaybackStatus
import dev.jdtech.jellyfin.player.core.R
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.presentation.formatDuration
import dev.jdtech.jellyfin.player.core.presentation.getSectionTitle
import dev.jdtech.jellyfin.presentation.player.components.VideoPlayerSeekBar
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import java.util.Date

/**
 * Timeline component for the expanded Cast player, including badges, chapter info, remaining time,
 * and seek bar.
 *
 * @param playerStatus Current playback status.
 * @param currentProgress Current progress in milliseconds.
 * @param duration Duration in milliseconds.
 * @param chapters List of chapters for the current media item.
 * @param onScrubStart Callback when scrub drag starts.
 * @param onScrubStop Callback when scrub drag stops.
 * @param modifier Modifier applied to the timeline layout. Defaults to [Modifier].
 * @param segments List of media segments (e.g. intros, credits). Defaults to empty list.
 * @param onOpenChapters Optional callback when chapter badge is tapped to open chapter selector.
 */
@Composable
fun CastScrubbingTimeline(
    playerStatus: CastPlaybackStatus,
    currentProgress: Float,
    duration: Float,
    chapters: List<PlayerChapter>,
    onScrubStart: (Float) -> Unit,
    onScrubStop: () -> Unit,
    modifier: Modifier = Modifier,
    segments: List<FindroidSegment> = emptyList(),
    onOpenChapters: (() -> Unit)? = null,
) {
    val safeDuration = if (duration.isFinite() && duration > 0f) duration else 0f
    val safeProgress =
        if (currentProgress.isFinite() && currentProgress > 0f) {
            currentProgress.coerceIn(0f, if (safeDuration > 0f) safeDuration else 1f)
        } else {
            0f
        }

    val durationMs = safeDuration.toLong()
    val progressMs = safeProgress.toLong()

    val context = LocalContext.current
    val contentProgressString = formatDuration(progressMs)
    val contentDurationString = formatDuration(durationMs)

    val sectionTitle =
        remember(progressMs, chapters, segments) {
            getSectionTitle(progressMs, chapters, segments, context)
        }

    val remainingMs = if (durationMs > 0L) (durationMs - progressMs).coerceAtLeast(0L) else 0L
    val formattedEndTime =
        remember(remainingMs, context) {
            if (durationMs > 0L) {
                val endTimeMillis = System.currentTimeMillis() + remainingMs
                DateFormat.getTimeFormat(context).format(Date(endTimeMillis))
            } else {
                null
            }
        }
    val endsAtText = formattedEndTime?.let { stringResource(R.string.ends_at, it) }

    val pillBackground = PlayerOverlayBackground.copy(alpha = 0.75f)
    val pillBorder = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))

    Column(modifier = modifier.padding(horizontal = 24.dp)) {
        // Badges row: Chapter pill & Progress • Duration pill on left, Ends at pill on right
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .alpha(if (safeDuration > 0f) 1f else 0f)
        ) {
            Column(
                modifier =
                    Modifier.align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .padding(end = if (endsAtText != null) 90.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Pill 1: Chapter Title (clickable if chapters are available)
                if (!sectionTitle.isNullOrBlank()) {
                    if (chapters.isNotEmpty() && onOpenChapters != null) {
                        Box(
                            modifier =
                                Modifier.clip(CircleShape)
                                    .background(pillBackground)
                                    .border(pillBorder, CircleShape)
                                    .clickable(onClick = onOpenChapters)
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
                                Modifier.clip(CircleShape)
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

                // Pill 2: Current progress • Content duration (right above seekbar)
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
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
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
            }

            // Pill 3: Ends at HH:mm
            if (endsAtText != null) {
                Box(
                    modifier =
                        Modifier.align(Alignment.BottomEnd)
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

        val isBuffering = playerStatus == CastPlaybackStatus.BUFFERING
        val progressFraction =
            if (durationMs > 0L) {
                (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }

        VideoPlayerSeekBar(
            progress = progressFraction,
            onSeek = { fraction ->
                val targetMs = fraction * durationMs
                onScrubStart(targetMs)
                onScrubStop()
            },
            durationMs = durationMs,
            chapters = chapters,
            segments = segments,
            onScrubbingChanged = { isScrubbing, scrubPosMs, _ ->
                if (isScrubbing) {
                    onScrubStart(scrubPosMs.toFloat())
                }
            },
            isBuffering = isBuffering,
            trackHeight = 10.dp,
            highlightTrackHeight = 10.dp,
            thumbWidth = 3.dp,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CastScrubbingTimelinePreview() {
    FindroidTheme {
        CastScrubbingTimeline(
            playerStatus = CastPlaybackStatus.PLAYING,
            currentProgress = 120000f,
            duration = 600000f,
            chapters =
                listOf(
                    PlayerChapter(0L, "Chapter 1"),
                    PlayerChapter(300000L, "Chapter 2"),
                ),
            onScrubStart = {},
            onScrubStop = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CastScrubbingTimelineBufferingPreview() {
    FindroidTheme {
        CastScrubbingTimeline(
            playerStatus = CastPlaybackStatus.BUFFERING,
            currentProgress = 120000f,
            duration = 600000f,
            chapters = emptyList(),
            onScrubStart = {},
            onScrubStop = {},
        )
    }
}
