package dev.jdtech.jellyfin.ui.components.player

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidSegmentType
import dev.jdtech.jellyfin.player.core.R as PlayerCoreR
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun VideoPlayerSeeker(
    focusRequester: FocusRequester,
    state: VideoPlayerState,
    onSeek: (Float) -> Unit,
    contentProgress: Duration,
    contentDuration: Duration,
    chapters: List<PlayerChapter> = emptyList(),
    segments: List<FindroidSegment> = emptyList(),
    trickplayIntervalMs: Long = 0L,
    isScrubbing: Boolean = false,
    scrubPositionMs: Long = 0L,
    onScrubbingChanged: (isScrubbing: Boolean, scrubPositionMs: Long) -> Unit = { _, _ -> },
    onDown: (() -> Unit)? = null,
    onOpenChapters: (() -> Unit)? = null,
    playPauseFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val rawDurationMs = contentDuration.inWholeMilliseconds
    val durationMs = if (rawDurationMs > 0L) rawDurationMs else 0L
    val rawProgressMs = contentProgress.inWholeMilliseconds
    val progressMs = if (rawProgressMs > 0L) rawProgressMs else 0L
    val activePositionMs = if (isScrubbing) scrubPositionMs.coerceAtLeast(0L) else progressMs

    val slices =
        remember(durationMs, chapters, segments) {
            calculateBarSlices(durationMs, chapters, segments)
        }

    val displayedProgressMs = if (isScrubbing) scrubPositionMs.coerceAtLeast(0L) else progressMs
    val contentProgressString = formatDuration(displayedProgressMs)
    val contentDurationString = formatDuration(durationMs)
    val scrubTimeString = formatDuration(scrubPositionMs)

    val sectionTitle =
        remember(activePositionMs, chapters, segments) {
            getSectionTitle(activePositionMs, chapters, segments, context)
        }

    val endsAtText =
        remember(durationMs, activePositionMs, context) {
            if (durationMs > 0L) {
                val remainingMs = (durationMs - activePositionMs).coerceAtLeast(0L)
                val endTimeMillis = System.currentTimeMillis() + remainingMs
                val formattedTime = DateFormat.getTimeFormat(context).format(Date(endTimeMillis))
                "Ends at $formattedTime"
            } else {
                null
            }
        }

    val chapterFocusRequester = remember { FocusRequester() }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(32.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            // Far Left: Timestamp Pill and Chapter Pill
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Pill 1: Current progress • Content duration
                Box(
                    modifier =
                        Modifier.clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = contentProgressString,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isScrubbing) FontWeight.Bold else FontWeight.Medium,
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
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }

                // Pill 2: Chapter Title (clickable if chapters are available)
                if (!sectionTitle.isNullOrBlank()) {
                    if (chapters.isNotEmpty() && onOpenChapters != null) {
                        val chapterInteractionSource = remember { MutableInteractionSource() }
                        val isChapterFocused by chapterInteractionSource.collectIsFocusedAsState()
                        Surface(
                            onClick = onOpenChapters,
                            interactionSource = chapterInteractionSource,
                            modifier =
                                Modifier.focusRequester(chapterFocusRequester).focusProperties {
                                    down = focusRequester
                                },
                            shape = ClickableSurfaceDefaults.shape(CircleShape),
                            colors =
                                ClickableSurfaceDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.15f),
                                    focusedContainerColor = Color.White,
                                    pressedContainerColor = Color.White,
                                    contentColor = Color.White,
                                    focusedContentColor = Color.Black,
                                ),
                            scale = ClickableSurfaceScale.None,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = sectionTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isChapterFocused) Color.Black else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    painter = painterResource(id = CoreR.drawable.ic_arrow_right),
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint =
                                        if (isChapterFocused) Color.Black
                                        else Color.White.copy(alpha = 0.85f),
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier =
                                Modifier.clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
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

            // Far Right: Pill 3 (Ends at HH:mm)
            if (endsAtText != null) {
                Box(
                    modifier =
                        Modifier.align(Alignment.CenterEnd)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = endsAtText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))

        VideoPlayerSeekBar(
            progress =
                if (durationMs > 0L) {
                    (progressMs.toFloat() / durationMs).coerceIn(0f, 1f)
                } else {
                    0f
                },
            onSeek = onSeek,
            state = state,
            durationMs = durationMs,
            chapters = chapters,
            segments = segments,
            trickplayIntervalMs = trickplayIntervalMs,
            isScrubbing = isScrubbing,
            onScrubbingChanged = onScrubbingChanged,
            onDown = onDown,
            onUp =
                if (chapters.isNotEmpty() && onOpenChapters != null) {
                    { chapterFocusRequester.requestFocus() }
                } else null,
            modifier =
                Modifier.focusRequester(focusRequester)
                    .then(
                        if (playPauseFocusRequester != null) {
                            Modifier.focusProperties { down = playPauseFocusRequester }
                        } else Modifier
                    ),
        )
    }
}

private fun formatDuration(millis: Long): String {
    val duration = millis.coerceAtLeast(0L).milliseconds
    return duration.toComponents { h, m, s, _ ->
        if (h > 0) {
            "$h:${m.padStartWith0()}:${s.padStartWith0()}"
        } else {
            "${m.padStartWith0()}:${s.padStartWith0()}"
        }
    }
}

private fun getSectionTitle(
    posMs: Long,
    chapters: List<PlayerChapter>,
    segments: List<FindroidSegment>,
    context: Context,
): String? {
    val segment = segments.firstOrNull { posMs in it.startTicks..it.endTicks }
    val segmentLabel =
        when (segment?.type) {
            FindroidSegmentType.INTRO -> context.getString(PlayerCoreR.string.segment_intro)
            FindroidSegmentType.OUTRO -> context.getString(PlayerCoreR.string.segment_outro)
            FindroidSegmentType.RECAP -> context.getString(PlayerCoreR.string.segment_recap)
            FindroidSegmentType.PREVIEW -> context.getString(PlayerCoreR.string.segment_preview)
            FindroidSegmentType.COMMERCIAL ->
                context.getString(PlayerCoreR.string.segment_commercial)
            else -> null
        }

    val chapterIndex = chapters.indexOfLast { posMs >= it.startPosition }
    val chapter = if (chapterIndex >= 0) chapters[chapterIndex] else null
    val chapterLabel =
        if (chapter != null) {
            if (!chapter.name.isNullOrBlank()) {
                chapter.name
            } else {
                context.getString(PlayerCoreR.string.chapter_title, chapterIndex + 1)
            }
        } else {
            null
        }

    return when {
        chapterLabel != null && segmentLabel != null -> "$chapterLabel • $segmentLabel"
        chapterLabel != null -> chapterLabel
        segmentLabel != null -> segmentLabel
        else -> null
    }
}

@Preview
@Composable
private fun VideoPlayerSeekerPreview() {
    FindroidTheme {
        VideoPlayerSeeker(
            focusRequester = FocusRequester(),
            state = rememberVideoPlayerState(),
            onSeek = {},
            contentProgress = Duration.parse("7m 51s"),
            contentDuration = Duration.parse("23m 40s"),
        )
    }
}

private fun Number.padStartWith0() = this.toString().padStart(2, '0')
