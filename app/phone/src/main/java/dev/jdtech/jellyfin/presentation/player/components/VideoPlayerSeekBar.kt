package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidSegmentType
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.presentation.calculateBarSlices
import dev.jdtech.jellyfin.player.core.presentation.getSegmentColor
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

private fun calculateProgressFromX(x: Float, width: Float): Float {
    if (width <= 0f) return 0f
    return (x / width).coerceIn(0f, 1f)
}

/**
 * Interactive timeline progress bar with chapter slicing, segment color accents, dynamic touch
 * expansion, and scrub position tracking.
 *
 * @param progress Current playback completion ratio between 0.0f and 1.0f.
 * @param onSeek Callback invoked when the user commits a seek gesture, passing target progress
 *   fraction.
 * @param state Active [VideoPlayerState] instance.
 * @param modifier Optional [Modifier] applied to the canvas.
 * @param durationMs Total media duration in milliseconds.
 * @param chapters List of [PlayerChapter] markers used to split the bar into visual chapter slices.
 * @param segments List of [FindroidSegment] intervals used to tint segment slices (intro, outro,
 *   recap, etc.).
 * @param isScrubbing Whether external scrubbing interaction is active.
 * @param onScrubbingChanged Callback reporting scrub status, scrub timestamp in ms, and touch X
 *   offset.
 */
@Composable
fun VideoPlayerSeekBar(
    progress: Float,
    onSeek: (seekProgress: Float) -> Unit,
    state: VideoPlayerState,
    modifier: Modifier = Modifier,
    durationMs: Long = 0L,
    chapters: List<PlayerChapter> = emptyList(),
    segments: List<FindroidSegment> = emptyList(),
    isScrubbing: Boolean = false,
    onScrubbingChanged: (isScrubbing: Boolean, scrubPositionMs: Long, touchX: Float) -> Unit =
        { _, _, _ ->
        },
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val primaryColor = MaterialTheme.colorScheme.primary

    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnScrubbingChanged by rememberUpdatedState(onScrubbingChanged)
    val currentProgress by rememberUpdatedState(progress)
    val currentState by rememberUpdatedState(state)

    val isHighlight = isDragging || isScrubbing
    val animatedTrackHeight by
        animateDpAsState(
            targetValue = if (isHighlight) 10.dp else 6.dp,
            animationSpec = tween(150),
            label = "trackHeight",
        )
    val animatedThumbHeight by
        animateDpAsState(
            targetValue = if (isHighlight) 24.dp else 14.dp,
            animationSpec = tween(150),
            label = "thumbHeight",
        )
    val animatedThumbGap by
        animateDpAsState(
            targetValue = if (isHighlight) 6.dp else 4.dp,
            animationSpec = tween(150),
            label = "thumbGap",
        )

    val safeDurationMs = if (durationMs > 0L) durationMs else 0L

    val slices =
        remember(safeDurationMs, chapters, segments) {
            calculateBarSlices(safeDurationMs, chapters, segments)
        }

    Canvas(
        modifier =
            modifier.fillMaxWidth().height(36.dp).padding(horizontal = 4.dp).pointerInput(
                safeDurationMs
            ) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    currentState.resetTimeout()
                    isDragging = true
                    var currentFraction =
                        calculateProgressFromX(down.position.x, size.width.toFloat())
                    dragProgress = currentFraction
                    var scrubPos = (currentFraction * safeDurationMs).toLong()
                    currentOnScrubbingChanged(true, scrubPos, down.position.x)

                    var releasedNormally = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.pressed) {
                                change.consume()
                                currentState.resetTimeout()
                                currentFraction =
                                    calculateProgressFromX(
                                        change.position.x,
                                        size.width.toFloat(),
                                    )
                                dragProgress = currentFraction
                                scrubPos = (currentFraction * safeDurationMs).toLong()
                                currentOnScrubbingChanged(true, scrubPos, change.position.x)
                            } else {
                                change.consume()
                                releasedNormally = true
                                currentOnSeek(currentFraction)
                                currentOnScrubbingChanged(false, scrubPos, change.position.x)
                                break
                            }
                        }
                    } finally {
                        isDragging = false
                        if (!releasedNormally) {
                            currentOnScrubbingChanged(
                                false,
                                (currentProgress * safeDurationMs).toLong(),
                                currentProgress * size.width,
                            )
                        }
                    }
                }
            }
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

        val currentProgressFraction = (if (isDragging) dragProgress else progress).coerceIn(0f, 1f)
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
            for ((startMs, endMs, segmentType) in slices) {
                val sliceDuration = (endMs - startMs).coerceAtLeast(1L)
                val sliceFraction = sliceDuration.toFloat() / safeDurationMs
                val sliceWidth = sliceFraction * trackAvailableWidth
                val sliceStartX = currentX
                val sliceEndX = sliceStartX + sliceWidth

                val segmentColor = segmentType?.let { getSegmentColor(it) }
                val trackBgColor =
                    segmentColor?.copy(alpha = 0.35f) ?: Color.White.copy(alpha = 0.20f)
                val trackFgColor = segmentColor ?: primaryColor

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
                    color = primaryColor,
                    topLeft = Offset(0f, yOffset - trackHeight / 2f),
                    size = Size(activeLimit, trackHeight),
                    cornerRadius = cornerRadius,
                )
            }
            // Inactive track (right of thumb)
            if (inactiveLimit < size.width) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.20f),
                    topLeft = Offset(inactiveLimit, yOffset - trackHeight / 2f),
                    size = Size(size.width - inactiveLimit, trackHeight),
                    cornerRadius = cornerRadius,
                )
            }
        }

        // 3. Draw Thumb (Material 3 Expressive cursor bar colored with primary or active segment
        // color)
        val activeSegmentColor =
            slices
                .firstOrNull { currentPlayMs in it.startMs..it.endMs }
                ?.segmentType
                ?.let { getSegmentColor(it) }
        val thumbColor = activeSegmentColor ?: primaryColor
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(thumbLeft, yOffset - thumbHeight / 2f),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun VideoPlayerSeekBarPreview() {
    FindroidTheme {
        val state = rememberVideoPlayerState()
        VideoPlayerSeekBar(
            progress = 0.35f,
            onSeek = {},
            state = state,
            durationMs = 600_000L,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun VideoPlayerSeekBarWithSegmentsPreview() {
    FindroidTheme {
        val state = rememberVideoPlayerState()
        VideoPlayerSeekBar(
            progress = 0.20f,
            onSeek = {},
            state = state,
            durationMs = 1_800_000L,
            chapters =
                listOf(
                    PlayerChapter(name = "Intro", startPosition = 0L),
                    PlayerChapter(name = "Act I", startPosition = 180_000L),
                    PlayerChapter(name = "Act II", startPosition = 700_000L),
                    PlayerChapter(name = "Credits", startPosition = 1_650_000L),
                ),
            segments =
                listOf(
                    FindroidSegment(
                        type = FindroidSegmentType.INTRO,
                        startTicks = 60_000L,
                        endTicks = 180_000L,
                    ),
                    FindroidSegment(
                        type = FindroidSegmentType.OUTRO,
                        startTicks = 1_650_000L,
                        endTicks = 1_800_000L,
                    ),
                ),
        )
    }
}
