package dev.jdtech.jellyfin.ui.components.player

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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
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
    val segmentCuts = validSegments.flatMap {
        listOf(
            it.startTicks.coerceIn(0L, durationMs),
            it.endTicks.coerceIn(0L, durationMs),
        )
    }

    val cuts = (listOf(0L, durationMs) + chapterCuts + segmentCuts).distinct().sorted()
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
        else -> null
    }
}

fun calculateThumbScreenFraction(
    positionMs: Long,
    durationMs: Long,
    slices: List<BarSlice>,
    totalWidthPx: Float = 1000f,
    gapPx: Float = 6f,
): Float {
    if (durationMs <= 0L || slices.isEmpty()) return 0f
    val clampedMs = positionMs.coerceIn(0L, durationMs)
    val numSlices = slices.size
    val totalGapsPx = if (numSlices > 1) (numSlices - 1) * gapPx else 0f
    val trackAvailableWidth = (totalWidthPx - totalGapsPx).coerceAtLeast(0f)

    var currentX = 0f
    for (slice in slices) {
        val sliceDuration = (slice.endMs - slice.startMs).coerceAtLeast(1L)
        val sliceFraction = sliceDuration.toFloat() / durationMs
        val sliceWidth = sliceFraction * trackAvailableWidth
        val sliceStartX = currentX
        val sliceEndX = sliceStartX + sliceWidth

        if (clampedMs in slice.startMs..slice.endMs) {
            val inSliceRatio = (clampedMs - slice.startMs).toFloat() / sliceDuration
            val thumbX = sliceStartX + inSliceRatio * sliceWidth
            return (thumbX / totalWidthPx).coerceIn(0f, 1f)
        }
        currentX = sliceEndX + gapPx
    }
    return (clampedMs.toFloat() / durationMs).coerceIn(0f, 1f)
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
    trickplayIntervalMs: Long = 0L,
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
    val animatedHeight by animateDpAsState(targetValue = 8.dp.times(if (isFocused) 2f else 1f))
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

    LaunchedEffect(isSelected) {
        if (isSelected) {
            state.showControls(seconds = Int.MAX_VALUE)
        } else {
            state.showControls()
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
                .height(animatedHeight)
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
        val trackHeight = size.height / 2f
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

        var currentX = 0f
        var thumbX = size.width * currentProgressFraction

        if (slices.isNotEmpty() && safeDurationMs > 0L) {
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

                // 1. Draw slice background
                drawRoundRect(
                    color = trackBgColor,
                    topLeft = Offset(sliceStartX, yOffset - trackHeight / 2f),
                    size = Size(sliceWidth, trackHeight),
                    cornerRadius = cornerRadius,
                )

                // 2. Draw slice played progress
                if (currentPlayMs >= slice.endMs) {
                    drawRoundRect(
                        color = trackFgColor,
                        topLeft = Offset(sliceStartX, yOffset - trackHeight / 2f),
                        size = Size(sliceWidth, trackHeight),
                        cornerRadius = cornerRadius,
                    )
                } else if (currentPlayMs > slice.startMs) {
                    val inSliceRatio = (currentPlayMs - slice.startMs).toFloat() / sliceDuration
                    val playedWidth = (sliceWidth * inSliceRatio).coerceIn(0f, sliceWidth)

                    clipPath(
                        path =
                            Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        rect =
                                            Rect(
                                                offset =
                                                    Offset(
                                                        sliceStartX,
                                                        yOffset - trackHeight / 2f,
                                                    ),
                                                size = Size(sliceWidth, trackHeight),
                                            ),
                                        cornerRadius = cornerRadius,
                                    )
                                )
                            }
                    ) {
                        drawRect(
                            color = trackFgColor,
                            topLeft = Offset(sliceStartX, yOffset - trackHeight / 2f),
                            size = Size(playedWidth, trackHeight),
                        )
                    }
                }

                // Calculate thumb center X
                if (currentPlayMs in slice.startMs..slice.endMs) {
                    val inSliceRatio = (currentPlayMs - slice.startMs).toFloat() / sliceDuration
                    thumbX = sliceStartX + inSliceRatio * sliceWidth
                }

                currentX = sliceEndX + gapPx
            }
        } else {
            drawLine(
                color = Color.White.copy(alpha = 0.28f),
                start = Offset(x = 0f, y = yOffset),
                end = Offset(x = size.width, y = yOffset),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = Offset(x = 0f, y = yOffset),
                end = Offset(x = size.width * currentProgressFraction, y = yOffset),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
            )
            thumbX = size.width * currentProgressFraction
        }

        if (isSelected || isFocused) {
            drawCircle(
                color = Color.White,
                radius = size.height.div(2),
                center = Offset(x = thumbX.coerceIn(0f, size.width), y = yOffset),
            )
        }
    }
}

@Preview
@Composable
fun VideoPlayerSeekBarPreview() {
    FindroidTheme {
        VideoPlayerSeekBar(progress = 0.4f, onSeek = {}, state = rememberVideoPlayerState())
    }
}
