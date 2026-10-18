package dev.jdtech.jellyfin.presentation.player.components

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.player.core.R as PlayerCoreR
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.presentation.formatDuration
import dev.jdtech.jellyfin.player.core.presentation.getSectionTitle
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import java.util.Date
import kotlin.time.Duration

@Composable
fun VideoPlayerSeeker(
    focusRequester: FocusRequester,
    state: VideoPlayerState,
    onSeek: (Float) -> Unit,
    contentProgress: Duration,
    contentDuration: Duration,
    modifier: Modifier = Modifier,
    chapters: List<PlayerChapter> = emptyList(),
    segments: List<FindroidSegment> = emptyList(),
    isScrubbing: Boolean = false,
    scrubPositionMs: Long = 0L,
    onScrubbingChanged: (isScrubbing: Boolean, scrubPositionMs: Long) -> Unit = { _, _ -> },
    onDown: (() -> Unit)? = null,
    onOpenChapters: (() -> Unit)? = null,
    playPauseFocusRequester: FocusRequester? = null,
) {
    val context = LocalContext.current
    val rawDurationMs = contentDuration.inWholeMilliseconds
    val durationMs = if (rawDurationMs > 0L) rawDurationMs else 0L
    val rawProgressMs = contentProgress.inWholeMilliseconds
    val progressMs = if (rawProgressMs > 0L) rawProgressMs else 0L
    val activePositionMs = if (isScrubbing) scrubPositionMs.coerceAtLeast(0L) else progressMs

    val displayedProgressMs = if (isScrubbing) scrubPositionMs.coerceAtLeast(0L) else progressMs
    val contentProgressString = formatDuration(displayedProgressMs)
    val contentDurationString = formatDuration(durationMs)

    val sectionTitle =
        remember(activePositionMs, chapters, segments) {
            getSectionTitle(activePositionMs, chapters, segments, context)
        }

    val remainingMs = if (durationMs > 0L) (durationMs - activePositionMs).coerceAtLeast(0L) else 0L
    val formattedEndTime =
        remember(remainingMs, context) {
            if (durationMs > 0L) {
                val endTimeMillis = System.currentTimeMillis() + remainingMs
                DateFormat.getTimeFormat(context).format(Date(endTimeMillis))
            } else {
                null
            }
        }
    val endsAtText = formattedEndTime?.let { stringResource(PlayerCoreR.string.ends_at, it) }

    val chapterFocusRequester = remember { FocusRequester() }

    val pillBackground = Color(0xFF262626).copy(alpha = 0.70f)
    val pillBorder = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))

    val pillsAlpha by
        animateFloatAsState(
            targetValue = if (isScrubbing) 0f else 1f,
            animationSpec = tween(durationMillis = 180),
            label = "seekerPillsAlpha",
        )

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(36.dp).graphicsLayer { alpha = pillsAlpha },
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
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isScrubbing) FontWeight.Bold else FontWeight.SemiBold,
                            color =
                                if (isScrubbing) MaterialTheme.colorScheme.primary else Color.White,
                        )
                        Box(
                            modifier =
                                Modifier.size(5.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.6f))
                        )
                        Text(
                            text = contentDurationString,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
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
                            border =
                                ClickableSurfaceDefaults.border(
                                    border = Border(pillBorder, shape = CircleShape),
                                    focusedBorder =
                                        Border(
                                            BorderStroke(2.dp, Color.White),
                                            shape = CircleShape,
                                        ),
                                ),
                            colors =
                                ClickableSurfaceDefaults.colors(
                                    containerColor = pillBackground,
                                    focusedContainerColor = Color.White,
                                    pressedContainerColor = Color.White,
                                    contentColor = Color.White,
                                    focusedContentColor = Color.Black,
                                ),
                            scale = ClickableSurfaceScale.None,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = sectionTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isChapterFocused) Color.Black else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    painter = painterResource(id = CoreR.drawable.ic_arrow_right),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
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
                                    .background(pillBackground)
                                    .border(pillBorder, CircleShape)
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = sectionTitle,
                                style = MaterialTheme.typography.titleSmall,
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
                            .background(pillBackground)
                            .border(pillBorder, CircleShape)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = endsAtText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
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

@Preview
@Composable
private fun VideoPlayerSeekerPreview() {
    FindroidTheme {
        VideoPlayerSeeker(
            focusRequester = remember { FocusRequester() },
            state = rememberVideoPlayerState(),
            onSeek = {},
            contentProgress = Duration.parse("7m 51s"),
            contentDuration = Duration.parse("23m 40s"),
        )
    }
}
