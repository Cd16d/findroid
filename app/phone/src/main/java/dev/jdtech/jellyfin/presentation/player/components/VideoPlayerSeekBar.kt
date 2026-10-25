package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidSegmentType
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.presentation.calculateBarSlices
import dev.jdtech.jellyfin.player.core.presentation.calculateProgressFromX
import dev.jdtech.jellyfin.player.core.presentation.drawCustomRoundRect
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

private fun getM3SegmentFraction(phase: Float, startPhase: Float, endPhase: Float): Float {
    if (phase <= startPhase) return 0f
    if (phase >= endPhase) return 1f
    val fraction = (phase - startPhase) / (endPhase - startPhase)
    return FastOutSlowInEasing.transform(fraction)
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
 * @param isBuffering Whether media is buffering, showing an animated shimmer along the track.
 * @param trackHeight Resting thickness of the progress bar track.
 * @param highlightTrackHeight Active thickness when scrubbing or focused.
 * @param thumbWidth Width of the scrubber thumb indicator.
 */
@Composable
fun VideoPlayerSeekBar(
    progress: Float,
    onSeek: (seekProgress: Float) -> Unit,
    modifier: Modifier = Modifier,
    bufferedProgress: Float = 0f,
    state: VideoPlayerState? = null,
    durationMs: Long = 0L,
    chapters: List<PlayerChapter> = emptyList(),
    segments: List<FindroidSegment> = emptyList(),
    isScrubbing: Boolean = false,
    onScrubbingChanged: (isScrubbing: Boolean, scrubPositionMs: Long, touchX: Float) -> Unit =
        { _, _, _ ->
        },
    isBuffering: Boolean = false,
    trackHeight: Dp = 10.dp,
    highlightTrackHeight: Dp = 10.dp,
    thumbWidth: Dp = 3.dp,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val primaryColor = MaterialTheme.colorScheme.primary

    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnScrubbingChanged by rememberUpdatedState(onScrubbingChanged)
    val currentProgress by rememberUpdatedState(progress)
    val currentState by rememberUpdatedState(state)

    val isHighlight = isDragging || isScrubbing
    val targetTrackHeight = if (isHighlight) highlightTrackHeight else trackHeight
    val animatedTrackHeight by
        animateDpAsState(
            targetValue = targetTrackHeight,
            animationSpec = tween(150),
            label = "trackHeight",
        )
    val targetThumbHeight = if (isHighlight) trackHeight + 12.dp else trackHeight + 6.dp
    val animatedThumbHeight by
        animateDpAsState(
            targetValue = targetThumbHeight,
            animationSpec = tween(150),
            label = "thumbHeight",
        )
    val targetThumbWidth = if (isHighlight) 1.5.dp else thumbWidth
    val animatedThumbWidth by
        animateDpAsState(
            targetValue = targetThumbWidth,
            animationSpec = tween(150),
            label = "thumbWidth",
        )
    val targetThumbGap = if (isBuffering) 0.dp else if (isHighlight) 5.dp else 3.5.dp
    val animatedThumbGap by
        animateDpAsState(
            targetValue = targetThumbGap,
            animationSpec = tween(150),
            label = "thumbGap",
        )
    val thumbAlpha by
        animateFloatAsState(
            targetValue = if (isBuffering) 0f else 1f,
            animationSpec = tween(200),
            label = "thumbAlpha",
        )

    val infiniteTransition = rememberInfiniteTransition(label = "seekbarBuffering")
    val bufferPhase by
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "bufferPhase",
        )

    val haptics = rememberPlayerHaptics()
    val currentChapters by rememberUpdatedState(chapters)

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
                    haptics.gestureStart()
                    currentState?.resetTimeout()
                    isDragging = true
                    var currentFraction =
                        calculateProgressFromX(down.position.x, size.width.toFloat())
                    dragProgress = currentFraction
                    var scrubPos = (currentFraction * safeDurationMs).toLong()
                    currentOnScrubbingChanged(true, scrubPos, down.position.x)

                    fun getChapterIndex(posMs: Long): Int {
                        val chs = currentChapters
                        if (chs.isEmpty()) return -1
                        for (i in chs.indices.reversed()) {
                            if (posMs >= chs[i].startPosition) return i
                        }
                        return 0
                    }

                    var lastChapterIdx = getChapterIndex(scrubPos)

                    var releasedNormally = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.pressed) {
                                change.consume()
                                currentState?.resetTimeout()
                                currentFraction =
                                    calculateProgressFromX(
                                        change.position.x,
                                        size.width.toFloat(),
                                    )
                                dragProgress = currentFraction
                                scrubPos = (currentFraction * safeDurationMs).toLong()
                                val newChapterIdx = getChapterIndex(scrubPos)
                                if (newChapterIdx != lastChapterIdx && newChapterIdx != -1) {
                                    haptics.chapterSnap()
                                    lastChapterIdx = newChapterIdx
                                }
                                currentOnScrubbingChanged(true, scrubPos, change.position.x)
                            } else {
                                change.consume()
                                releasedNormally = true
                                haptics.gestureEnd()
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
        val trackHeightPx = animatedTrackHeight.toPx()
        val thumbHeight = animatedThumbHeight.toPx()
        val thumbGap = animatedThumbGap.toPx()
        val thumbWidthPx = animatedThumbWidth.toPx()
        val fullCornerRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)
        val smallCornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
        val gapPx = 3.dp.toPx()
        val numSlices = slices.size
        val totalGapsPx = if (numSlices > 1) (numSlices - 1) * gapPx else 0f
        val trackAvailableWidth = (size.width - totalGapsPx).coerceAtLeast(0f)

        val head1 = getM3SegmentFraction(bufferPhase, 0.0f, 0.65f)
        val tail1 = getM3SegmentFraction(bufferPhase, 0.20f, 0.85f)
        val head2 = getM3SegmentFraction(bufferPhase, 0.35f, 1.0f)
        val tail2 = getM3SegmentFraction(bufferPhase, 0.55f, 1.0f)
        val indeterminateRanges = listOf(tail1 to head1, tail2 to head2)

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
            for ((startMs, endMs) in slices) {
                val sliceDuration = (endMs - startMs).coerceAtLeast(1L)
                val sliceFraction = sliceDuration.toFloat() / safeDurationMs
                val sliceWidth = sliceFraction * trackAvailableWidth
                if (currentPlayMs in startMs..endMs) {
                    val inSliceRatio =
                        ((currentPlayMs - startMs).toFloat() / sliceDuration).coerceIn(0f, 1f)
                    thumbX = runningX + inSliceRatio * sliceWidth
                    break
                }
                runningX += sliceWidth + gapPx
            }
        }

        val clampedThumbX = thumbX.coerceIn(thumbWidthPx / 2f, size.width - thumbWidthPx / 2f)
        val thumbLeft = clampedThumbX - thumbWidthPx / 2f
        val thumbRight = clampedThumbX + thumbWidthPx / 2f

        val activeLimit = (thumbLeft - thumbGap).coerceAtLeast(0f)
        val inactiveLimit = (thumbRight + thumbGap).coerceAtMost(size.width)

        if (slices.isNotEmpty() && safeDurationMs > 0L) {
            if (isBuffering) {
                // Background track for all slices
                var currentX = 0f
                for ((startMs, endMs, _, _, color) in slices) {
                    val sliceDuration = (endMs - startMs).coerceAtLeast(1L)
                    val sliceFraction = sliceDuration.toFloat() / safeDurationMs
                    val sliceWidth = sliceFraction * trackAvailableWidth
                    val sliceStartX = currentX
                    val sliceEndX = sliceStartX + sliceWidth

                    val segmentColor = color
                    val trackBgColor =
                        segmentColor?.copy(alpha = 0.35f) ?: Color.White.copy(alpha = 0.20f)

                    drawCustomRoundRect(
                        color = trackBgColor,
                        topLeft = Offset(sliceStartX, yOffset - trackHeightPx / 2f),
                        size = Size(sliceWidth, trackHeightPx),
                        topLeftRadius = fullCornerRadius,
                        bottomLeftRadius = fullCornerRadius,
                        topRightRadius = fullCornerRadius,
                        bottomRightRadius = fullCornerRadius,
                    )
                    currentX = sliceEndX + gapPx
                }

                // Material 3 Indeterminate solid loading segments across slices
                for ((tail, head) in indeterminateRanges) {
                    if (head <= tail) continue
                    val segStartX = tail * size.width
                    val segEndX = head * size.width
                    var sliceX = 0f
                    for ((startMs, endMs, _, _, color) in slices) {
                        val sliceDuration = (endMs - startMs).coerceAtLeast(1L)
                        val sliceFraction = sliceDuration.toFloat() / safeDurationMs
                        val sliceWidth = sliceFraction * trackAvailableWidth
                        val sliceStartX = sliceX
                        val sliceEndX = sliceStartX + sliceWidth
                        val segmentColor = color
                        val trackFgColor = segmentColor ?: primaryColor

                        val activeStart = maxOf(sliceStartX, segStartX)
                        val activeEnd = minOf(sliceEndX, segEndX)
                        if (activeEnd > activeStart) {
                            val leftRadius =
                                if (activeStart <= sliceStartX + 1f) fullCornerRadius
                                else smallCornerRadius
                            val rightRadius =
                                if (activeEnd >= sliceEndX - 1f) fullCornerRadius
                                else smallCornerRadius
                            drawCustomRoundRect(
                                color = trackFgColor,
                                topLeft = Offset(activeStart, yOffset - trackHeightPx / 2f),
                                size = Size(activeEnd - activeStart, trackHeightPx),
                                topLeftRadius = leftRadius,
                                bottomLeftRadius = leftRadius,
                                topRightRadius = rightRadius,
                                bottomRightRadius = rightRadius,
                            )
                        }
                        sliceX = sliceEndX + gapPx
                    }
                }
            } else {
                // Inactive and Active portions of slices
                var sliceX = 0f
                for ((startMs, endMs, _, _, color) in slices) {
                    val sliceDuration = (endMs - startMs).coerceAtLeast(1L)
                    val sliceFraction = sliceDuration.toFloat() / safeDurationMs
                    val sliceWidth = sliceFraction * trackAvailableWidth
                    val sliceStartX = sliceX
                    val sliceEndX = sliceStartX + sliceWidth

                    val segmentColor = color
                    val trackBgColor =
                        segmentColor?.copy(alpha = 0.35f) ?: Color.White.copy(alpha = 0.20f)
                    val trackFgColor = segmentColor ?: primaryColor

                    // Inactive track portion (right of thumb gap)
                    val inactiveStart = maxOf(sliceStartX, inactiveLimit)
                    if (sliceEndX > inactiveStart) {
                        val leftRadius =
                            if (inactiveStart > sliceStartX + 1f) smallCornerRadius
                            else fullCornerRadius
                        drawCustomRoundRect(
                            color = trackBgColor,
                            topLeft = Offset(inactiveStart, yOffset - trackHeightPx / 2f),
                            size = Size(sliceEndX - inactiveStart, trackHeightPx),
                            topLeftRadius = leftRadius,
                            bottomLeftRadius = leftRadius,
                            topRightRadius = fullCornerRadius,
                            bottomRightRadius = fullCornerRadius,
                        )

                        // Buffered progress on inactive slice
                        val currentBufferMs =
                            (bufferedProgress.coerceIn(0f, 1f) * safeDurationMs).toLong()
                        if (currentBufferMs > startMs) {
                            val sliceBufferRatio =
                                ((minOf(endMs, currentBufferMs) - startMs).toFloat() /
                                        sliceDuration)
                                    .coerceIn(0f, 1f)
                            val sliceBufferX = sliceStartX + sliceBufferRatio * sliceWidth
                            val bufferedEnd = minOf(sliceEndX, sliceBufferX)
                            if (bufferedEnd > inactiveStart) {
                                val bufRightRadius =
                                    if (bufferedEnd < sliceEndX - 1f) smallCornerRadius
                                    else fullCornerRadius
                                drawCustomRoundRect(
                                    color = Color.White.copy(alpha = 0.40f),
                                    topLeft = Offset(inactiveStart, yOffset - trackHeightPx / 2f),
                                    size = Size(bufferedEnd - inactiveStart, trackHeightPx),
                                    topLeftRadius = leftRadius,
                                    bottomLeftRadius = leftRadius,
                                    topRightRadius = bufRightRadius,
                                    bottomRightRadius = bufRightRadius,
                                )
                            }
                        }
                    }

                    // Active track portion (left of thumb gap)
                    val activeStart = sliceStartX
                    val activeEnd = minOf(sliceEndX, activeLimit)
                    if (activeEnd > activeStart) {
                        val rightRadius =
                            if (activeEnd < sliceEndX) smallCornerRadius else fullCornerRadius
                        drawCustomRoundRect(
                            color = trackFgColor,
                            topLeft = Offset(activeStart, yOffset - trackHeightPx / 2f),
                            size = Size(activeEnd - activeStart, trackHeightPx),
                            topLeftRadius = fullCornerRadius,
                            bottomLeftRadius = fullCornerRadius,
                            topRightRadius = rightRadius,
                            bottomRightRadius = rightRadius,
                        )
                    }
                    sliceX = sliceEndX + gapPx
                }
            }
        } else {
            if (isBuffering) {
                // Draw continuous background track
                drawCustomRoundRect(
                    color = Color.White.copy(alpha = 0.20f),
                    topLeft = Offset(0f, yOffset - trackHeightPx / 2f),
                    size = Size(size.width, trackHeightPx),
                    topLeftRadius = fullCornerRadius,
                    bottomLeftRadius = fullCornerRadius,
                    topRightRadius = fullCornerRadius,
                    bottomRightRadius = fullCornerRadius,
                )

                // Material 3 Indeterminate solid loading segments
                for ((tail, head) in indeterminateRanges) {
                    if (head <= tail) continue
                    val segStartX = tail * size.width
                    val segEndX = head * size.width
                    if (segEndX > segStartX) {
                        drawCustomRoundRect(
                            color = primaryColor,
                            topLeft = Offset(segStartX, yOffset - trackHeightPx / 2f),
                            size = Size(segEndX - segStartX, trackHeightPx),
                            topLeftRadius = fullCornerRadius,
                            bottomLeftRadius = fullCornerRadius,
                            topRightRadius = fullCornerRadius,
                            bottomRightRadius = fullCornerRadius,
                        )
                    }
                }
            } else {
                // Inactive track (right of thumb gap)
                if (inactiveLimit < size.width) {
                    drawCustomRoundRect(
                        color = Color.White.copy(alpha = 0.20f),
                        topLeft = Offset(inactiveLimit, yOffset - trackHeightPx / 2f),
                        size = Size(size.width - inactiveLimit, trackHeightPx),
                        topLeftRadius = smallCornerRadius,
                        bottomLeftRadius = smallCornerRadius,
                        topRightRadius = fullCornerRadius,
                        bottomRightRadius = fullCornerRadius,
                    )

                    val bufferedX =
                        (bufferedProgress.coerceIn(0f, 1f) * size.width).coerceAtMost(size.width)
                    if (bufferedX > inactiveLimit) {
                        val bufRightRadius =
                            if (bufferedX < size.width - 1f) smallCornerRadius else fullCornerRadius
                        drawCustomRoundRect(
                            color = Color.White.copy(alpha = 0.40f),
                            topLeft = Offset(inactiveLimit, yOffset - trackHeightPx / 2f),
                            size = Size(bufferedX - inactiveLimit, trackHeightPx),
                            topLeftRadius = smallCornerRadius,
                            bottomLeftRadius = smallCornerRadius,
                            topRightRadius = bufRightRadius,
                            bottomRightRadius = bufRightRadius,
                        )
                    }
                }

                // Active track (left of thumb gap)
                if (activeLimit > 0f) {
                    drawCustomRoundRect(
                        color = primaryColor,
                        topLeft = Offset(0f, yOffset - trackHeightPx / 2f),
                        size = Size(activeLimit, trackHeightPx),
                        topLeftRadius = fullCornerRadius,
                        bottomLeftRadius = fullCornerRadius,
                        topRightRadius = smallCornerRadius,
                        bottomRightRadius = smallCornerRadius,
                    )
                }
            }
        }

        // Draw Thumb cursor bar (hidden during buffering)
        if (thumbAlpha > 0.01f) {
            val activeSlice = slices.firstOrNull { currentPlayMs in it.startMs..it.endMs }
            val activeSegmentColor = activeSlice?.color
            val thumbColor = activeSegmentColor ?: primaryColor
            drawRoundRect(
                color = thumbColor.copy(alpha = thumbAlpha),
                topLeft = Offset(thumbLeft, yOffset - thumbHeight / 2f),
                size = Size(thumbWidthPx, thumbHeight),
                cornerRadius = CornerRadius(thumbWidthPx / 2f, thumbWidthPx / 2f),
            )
        }
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
