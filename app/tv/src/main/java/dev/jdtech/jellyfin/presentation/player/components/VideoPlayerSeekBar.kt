package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidSegmentType
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

data class BarSlice(
    val startMs: Long,
    val endMs: Long,
    val segmentType: FindroidSegmentType? = null,
    val title: String? = null,
)

fun calculateBarSlices(
    durationMs: Long,
    chapters: List<PlayerChapter> = emptyList(),
    segments: List<FindroidSegment> = emptyList(),
): List<BarSlice> {
    if (durationMs <= 0L) return emptyList()

    val validChapters =
        chapters.filter { it.startPosition in 0..durationMs }.sortedBy { it.startPosition }
    val validSegments =
        segments
            .filter { it.startTicks < durationMs && it.endTicks > 0L }
            .sortedBy { it.startTicks }

    val chapterCuts = validChapters.map { it.startPosition.coerceIn(0L, durationMs) }
    // Snap segment cuts within 2 seconds of a chapter cut to eliminate micro-slices/double gaps
    val segmentCuts =
        validSegments
            .flatMap {
                listOf(
                    it.startTicks.coerceIn(0L, durationMs),
                    it.endTicks.coerceIn(0L, durationMs),
                )
            }
            .map { segCut ->
                val nearestChapter = chapterCuts.minByOrNull { kotlin.math.abs(it - segCut) }
                if (nearestChapter != null && kotlin.math.abs(nearestChapter - segCut) <= 2000L) {
                    nearestChapter
                } else {
                    segCut
                }
            }

    val rawCuts = (listOf(0L, durationMs) + chapterCuts + segmentCuts).distinct().sorted()
    if (rawCuts.size <= 1) return listOf(BarSlice(startMs = 0L, endMs = durationMs))

    // Eliminate micro-slices smaller than 1.5 seconds between adjacent cuts
    val cuts = mutableListOf<Long>()
    for (cut in rawCuts) {
        if (cuts.isEmpty()) {
            cuts.add(cut)
        } else {
            val prev = cuts.last()
            if (cut - prev >= 1500L || cut == durationMs) {
                if (cut == durationMs && cut - prev < 1500L && cuts.size > 1) {
                    cuts[cuts.size - 1] = durationMs
                } else {
                    cuts.add(cut)
                }
            }
        }
    }
    if (cuts.size <= 1) return listOf(BarSlice(startMs = 0L, endMs = durationMs))

    val slices = mutableListOf<BarSlice>()
    for (i in 0 until cuts.size - 1) {
        val start = cuts[i]
        val end = cuts[i + 1]
        if (end > start) {
            val matchingSegment = validSegments.firstOrNull { seg ->
                start >= seg.startTicks && end <= seg.endTicks
            }
            val matchingChapter = validChapters.lastOrNull { it.startPosition <= start }
            slices.add(
                BarSlice(
                    startMs = start,
                    endMs = end,
                    segmentType = matchingSegment?.type,
                    title = matchingChapter?.name,
                )
            )
        }
    }
    return slices
}

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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VideoPlayerSeekBar(
    progress: Float,
    onSeek: (seekProgress: Float) -> Unit,
    state: VideoPlayerState,
    durationMs: Long = 0L,
    chapters: List<PlayerChapter> = emptyList(),
    segments: List<FindroidSegment> = emptyList(),
    isScrubbing: Boolean = false,
    onScrubbingChanged: (isScrubbing: Boolean, scrubPositionMs: Long) -> Unit = { _, _ -> },
    onDown: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var isSelected by remember { mutableStateOf(false) }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val color by rememberUpdatedState(newValue = MaterialTheme.colorScheme.primary)
    val isHighlight = isFocused || isSelected
    val animatedTrackHeight by animateDpAsState(targetValue = if (isHighlight) 10.dp else 6.dp)
    val animatedThumbHeight by animateDpAsState(targetValue = if (isHighlight) 24.dp else 14.dp)
    val animatedThumbGap by animateDpAsState(targetValue = if (isHighlight) 6.dp else 4.dp)
    var seekProgress by remember { mutableFloatStateOf(0f) }
    val focusManager = LocalFocusManager.current

    val safeDurationMs = if (durationMs > 0L) durationMs else 0L

    val slices =
        remember(safeDurationMs, chapters, segments) {
            calculateBarSlices(safeDurationMs, chapters, segments)
        }

    LaunchedEffect(isFocused) {
        if (!isFocused && isSelected) {
            onSeek(seekProgress)
            onScrubbingChanged(false, (seekProgress * safeDurationMs).toLong())
            isSelected = false
        }
    }

    LaunchedEffect(isScrubbing) {
        if (!isScrubbing && isSelected) {
            isSelected = false
        }
    }

    val stepFraction =
        if (safeDurationMs > 0) {
            10_000f / safeDurationMs
        } else {
            0.05f
        }

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(horizontal = 4.dp)
                .onPreviewKeyEvent { keyEvent ->
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                if (!isSelected) {
                                    seekProgress = progress
                                    isSelected = true
                                }
                                seekProgress = (seekProgress - stepFraction).coerceAtLeast(0f)
                                onScrubbingChanged(true, (seekProgress * safeDurationMs).toLong())
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                if (!isSelected) {
                                    seekProgress = progress
                                    isSelected = true
                                }
                                seekProgress = (seekProgress + stepFraction).coerceAtMost(1f)
                                onScrubbingChanged(true, (seekProgress * safeDurationMs).toLong())
                            }
                            true
                        }
                        Key.DirectionDown -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                if (isSelected) {
                                    onSeek(seekProgress)
                                    onScrubbingChanged(
                                        false,
                                        (seekProgress * safeDurationMs).toLong(),
                                    )
                                    isSelected = false
                                }
                                if (onDown != null) {
                                    onDown()
                                } else {
                                    focusManager.moveFocus(FocusDirection.Down)
                                }
                            }
                            true
                        }
                        Key.DirectionCenter,
                        Key.Enter,
                        Key.NumPadEnter -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                if (isSelected) {
                                    onSeek(seekProgress)
                                    onScrubbingChanged(
                                        false,
                                        (seekProgress * safeDurationMs).toLong(),
                                    )
                                    isSelected = false
                                } else {
                                    seekProgress = progress
                                    isSelected = true
                                    onScrubbingChanged(true, (progress * safeDurationMs).toLong())
                                }
                            }
                            true
                        }
                        Key.DirectionUp -> {
                            if (isSelected) {
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    onSeek(seekProgress)
                                    onScrubbingChanged(
                                        false,
                                        (seekProgress * safeDurationMs).toLong(),
                                    )
                                    isSelected = false
                                }
                                true
                            } else {
                                if (onUp != null) {
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        onUp()
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                        }
                        Key.Back -> {
                            if (isSelected) {
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    isSelected = false
                                    onScrubbingChanged(false, (progress * safeDurationMs).toLong())
                                }
                                true
                            } else {
                                false
                            }
                        }
                        Key.MediaPlayPause,
                        Key.MediaPlay,
                        Key.MediaPause -> {
                            if (isSelected && keyEvent.type == KeyEventType.KeyDown) {
                                onSeek(seekProgress)
                                onScrubbingChanged(false, (seekProgress * safeDurationMs).toLong())
                                isSelected = false
                            }
                            false
                        }
                        else -> false
                    }
                }
                .focusable(interactionSource = interactionSource)
    ) {
        val yOffset = size.height / 2f
        val trackHeight = animatedTrackHeight.toPx()
        val thumbHeight = animatedThumbHeight.toPx()
        val thumbGap = animatedThumbGap.toPx()
        val thumbWidth = 4.dp.toPx()
        val cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
        val gapPx = 3.dp.toPx()
        val numSlices = slices.size
        val totalGapsPx = if (numSlices > 1) (numSlices - 1) * gapPx else 0f
        val trackAvailableWidth = (size.width - totalGapsPx).coerceAtLeast(0f)

        val currentProgressFraction = (if (isSelected) seekProgress else progress).coerceIn(0f, 1f)
        val currentPlayMs =
            if (safeDurationMs > 0L) {
                (currentProgressFraction * safeDurationMs).toLong().coerceIn(0L, safeDurationMs)
            } else {
                0L
            }

        // Calculate thumb center X
        var thumbX = size.width * currentProgressFraction
        if (slices.isNotEmpty() && safeDurationMs > 0L) {
            var runningX = 0f
            for (slice in slices) {
                val sliceDuration = (slice.endMs - slice.startMs).coerceAtLeast(1L)
                val sliceFraction = sliceDuration.toFloat() / safeDurationMs
                val sliceWidth = sliceFraction * trackAvailableWidth
                if (currentPlayMs in slice.startMs..slice.endMs) {
                    val inSliceRatio =
                        ((currentPlayMs - slice.startMs).toFloat() / sliceDuration).coerceIn(0f, 1f)
                    thumbX = runningX + inSliceRatio * sliceWidth
                    break
                }
                runningX += sliceWidth + gapPx
            }
        }

        val clampedThumbX = thumbX.coerceIn(thumbWidth / 2f, size.width - thumbWidth / 2f)
        val thumbLeft = clampedThumbX - thumbWidth / 2f
        val thumbRight = clampedThumbX + thumbWidth / 2f

        val activeLimit = (thumbLeft - thumbGap).coerceAtLeast(0f)
        val inactiveLimit = (thumbRight + thumbGap).coerceAtMost(size.width)

        if (slices.isNotEmpty() && safeDurationMs > 0L) {
            var currentX = 0f
            for (slice in slices) {
                val sliceDuration = (slice.endMs - slice.startMs).coerceAtLeast(1L)
                val sliceFraction = sliceDuration.toFloat() / safeDurationMs
                val sliceWidth = sliceFraction * trackAvailableWidth
                val sliceStartX = currentX
                val sliceEndX = sliceStartX + sliceWidth

                val segmentColor = slice.segmentType?.let { getSegmentColor(it) }
                val trackBgColor =
                    segmentColor?.copy(alpha = 0.45f) ?: Color.White.copy(alpha = 0.28f)
                val trackFgColor = segmentColor ?: color

                // Active (played) portion of this slice
                val activeStart = sliceStartX
                val activeEnd = minOf(sliceEndX, activeLimit)
                if (activeEnd > activeStart) {
                    drawRoundRect(
                        color = trackFgColor,
                        topLeft = Offset(activeStart, yOffset - trackHeight / 2f),
                        size = Size(activeEnd - activeStart, trackHeight),
                        cornerRadius = cornerRadius,
                    )
                }

                // Inactive (unplayed) portion of this slice
                val inactiveStart = maxOf(sliceStartX, inactiveLimit)
                val inactiveEnd = sliceEndX
                if (inactiveEnd > inactiveStart) {
                    drawRoundRect(
                        color = trackBgColor,
                        topLeft = Offset(inactiveStart, yOffset - trackHeight / 2f),
                        size = Size(inactiveEnd - inactiveStart, trackHeight),
                        cornerRadius = cornerRadius,
                    )
                }

                currentX = sliceEndX + gapPx
            }
        } else {
            // Active track (left of thumb)
            if (activeLimit > 0f) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, yOffset - trackHeight / 2f),
                    size = Size(activeLimit, trackHeight),
                    cornerRadius = cornerRadius,
                )
            }
            // Inactive track (right of thumb)
            if (inactiveLimit < size.width) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.28f),
                    topLeft = Offset(inactiveLimit, yOffset - trackHeight / 2f),
                    size = Size(size.width - inactiveLimit, trackHeight),
                    cornerRadius = cornerRadius,
                )
            }
        }

        // Draw Material 3 Expressive vertical bar thumb
        val thumbCornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f)
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(clampedThumbX - thumbWidth / 2f, yOffset - thumbHeight / 2f),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = thumbCornerRadius,
        )
    }
}

@Preview
@Composable
fun VideoPlayerSeekBarPreview() {
    FindroidTheme {
        VideoPlayerSeekBar(progress = 0.4f, onSeek = {}, state = rememberVideoPlayerState())
    }
}
