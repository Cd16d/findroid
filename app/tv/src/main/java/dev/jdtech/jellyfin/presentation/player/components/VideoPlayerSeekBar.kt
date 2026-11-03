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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import dev.jdtech.jellyfin.player.core.presentation.drawCustomRoundRect
import dev.jdtech.jellyfin.player.core.presentation.getSegmentColor
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

private fun getM3SegmentFraction(phase: Float, startPhase: Float, endPhase: Float): Float {
    if (phase <= startPhase) return 0f
    if (phase >= endPhase) return 1f
    val fraction = (phase - startPhase) / (endPhase - startPhase)
    return FastOutSlowInEasing.transform(fraction)
}

/** Represents a chapter interval slice along the timeline. */
private data class ChapterSlice(
    val startMs: Long,
    val endMs: Long,
    val title: String? = null,
)

@Composable
fun VideoPlayerSeekBar(
    progress: Float,
    onSeek: (seekProgress: Float) -> Unit,
    modifier: Modifier = Modifier,
    bufferedProgress: Float = 0f,
    durationMs: Long = 0L,
    chapters: List<PlayerChapter> = emptyList(),
    segments: List<FindroidSegment> = emptyList(),
    isScrubbing: Boolean = false,
    onScrubbingChanged: (isScrubbing: Boolean, scrubPositionMs: Long) -> Unit = { _, _ -> },
    onDown: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    isBuffering: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var isSelected by remember { mutableStateOf(false) }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val color by rememberUpdatedState(newValue = MaterialTheme.colorScheme.primary)
    val isHighlight = isFocused || isSelected
    val animatedTrackHeight by
        animateDpAsState(
            targetValue = 10.dp,
            animationSpec = tween(150),
            label = "trackHeight",
        )
    val animatedThumbHeight by
        animateDpAsState(
            targetValue = if (isHighlight) 22.dp else 16.dp,
            animationSpec = tween(150),
            label = "thumbHeight",
        )
    val animatedThumbWidth by
        animateDpAsState(
            targetValue = if (isHighlight) 1.5.dp else 3.dp,
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
    var seekProgress by remember { mutableFloatStateOf(0f) }
    val focusManager = LocalFocusManager.current

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

    val safeDurationMs = if (durationMs > 0L) durationMs else 0L
    val slicePath = remember { Path() }

    val chapterSlices =
        remember(safeDurationMs, chapters) {
            if (safeDurationMs <= 0L) {
                emptyList()
            } else {
                val validChapters =
                    chapters
                        .filter { it.startPosition in 0..safeDurationMs }
                        .sortedBy { it.startPosition }
                        .distinctBy { it.startPosition }

                if (validChapters.isEmpty()) {
                    listOf(ChapterSlice(0L, safeDurationMs))
                } else {
                    val cuts = mutableListOf<Long>()
                    if (validChapters.first().startPosition > 0L) {
                        cuts.add(0L)
                    }
                    for (ch in validChapters) {
                        cuts.add(ch.startPosition)
                    }
                    if (cuts.last() < safeDurationMs) {
                        cuts.add(safeDurationMs)
                    }
                    val chapterMap = validChapters.associateBy { it.startPosition }
                    val result = mutableListOf<ChapterSlice>()
                    for (i in 0 until cuts.size - 1) {
                        val start = cuts[i]
                        val end = cuts[i + 1]
                        if (end > start) {
                            val chName = chapterMap[start]?.name
                            result.add(ChapterSlice(start, end, chName))
                        }
                    }
                    if (result.isEmpty()) listOf(ChapterSlice(0L, safeDurationMs)) else result
                }
            }
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
        val trackHeightPx = animatedTrackHeight.toPx()
        val thumbHeight = animatedThumbHeight.toPx()
        val thumbGap = animatedThumbGap.toPx()
        val thumbWidthPx = animatedThumbWidth.toPx()
        val fullCornerRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)
        val innerCornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        val gapPx = 2.dp.toPx()
        val numSlices = chapterSlices.size
        val totalGapsPx = if (numSlices > 1) (numSlices - 1) * gapPx else 0f
        val trackAvailableWidth = (size.width - totalGapsPx).coerceAtLeast(0f)

        val head1 = getM3SegmentFraction(bufferPhase, 0.0f, 0.65f)
        val tail1 = getM3SegmentFraction(bufferPhase, 0.20f, 0.85f)
        val head2 = getM3SegmentFraction(bufferPhase, 0.35f, 1.0f)
        val tail2 = getM3SegmentFraction(bufferPhase, 0.55f, 1.0f)
        val indeterminateRanges = listOf(tail1 to head1, tail2 to head2)

        val currentProgressFraction = (if (isSelected) seekProgress else progress).coerceIn(0f, 1f)
        val currentPlayMs =
            if (safeDurationMs > 0L) {
                (currentProgressFraction * safeDurationMs).toLong().coerceIn(0L, safeDurationMs)
            } else {
                0L
            }

        // Compute thumb X position mapped across chapter gaps
        var thumbX = size.width * currentProgressFraction
        if (chapterSlices.isNotEmpty() && safeDurationMs > 0L) {
            var runningX = 0f
            for ((startMs, endMs) in chapterSlices) {
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

        val bufferedMs = (bufferedProgress.coerceIn(0f, 1f) * safeDurationMs).toLong()

        // Helper to resolve segment color for a segment object
        fun getSegmentColorFor(seg: FindroidSegment): Color {
            return when (val colorVal = seg.color) {
                0L -> getSegmentColor(seg.type) ?: color
                null -> getSegmentColor(seg.type) ?: color
                else -> Color(colorVal)
            }
        }

        if (chapterSlices.isNotEmpty() && safeDurationMs > 0L) {
            var sliceX = 0f
            for (i in chapterSlices.indices) {
                val slice = chapterSlices[i]
                val sliceDuration = (slice.endMs - slice.startMs).coerceAtLeast(1L)
                val sliceFraction = sliceDuration.toFloat() / safeDurationMs
                val sliceWidth = sliceFraction * trackAvailableWidth
                val sliceStartX = sliceX
                val sliceEndX = sliceStartX + sliceWidth

                val isFirstSlice = i == 0
                val isLastSlice = i == chapterSlices.size - 1

                val leftRadius = if (isFirstSlice) fullCornerRadius else innerCornerRadius
                val rightRadius = if (isLastSlice) fullCornerRadius else innerCornerRadius

                slicePath.reset()
                slicePath.addRoundRect(
                    RoundRect(
                        rect =
                            Rect(
                                left = sliceStartX,
                                top = yOffset - trackHeightPx / 2f,
                                right = sliceEndX,
                                bottom = yOffset + trackHeightPx / 2f,
                            ),
                        topLeft = leftRadius,
                        bottomLeft = leftRadius,
                        topRight = rightRadius,
                        bottomRight = rightRadius,
                    )
                )

                clipPath(slicePath) {
                    if (isBuffering) {
                        // 1. Base unplayed chapter track background
                        drawCustomRoundRect(
                            color = Color.White.copy(alpha = 0.20f),
                            topLeft = Offset(sliceStartX, yOffset - trackHeightPx / 2f),
                            size = Size(sliceWidth, trackHeightPx),
                            topLeftRadius = leftRadius,
                            bottomLeftRadius = leftRadius,
                            topRightRadius = rightRadius,
                            bottomRightRadius = rightRadius,
                        )

                        // 2. Unplayed Segment overlays on top
                        for (seg in segments) {
                            val segStartMs = maxOf(slice.startMs, seg.startTicks)
                            val segEndMs = minOf(slice.endMs, seg.endTicks)
                            if (segEndMs > segStartMs) {
                                val startRatio =
                                    ((segStartMs - slice.startMs).toFloat() / sliceDuration)
                                        .coerceIn(0f, 1f)
                                val endRatio =
                                    ((segEndMs - slice.startMs).toFloat() / sliceDuration).coerceIn(
                                        0f,
                                        1f,
                                    )

                                val segStartX = sliceStartX + startRatio * sliceWidth
                                val segEndX = sliceStartX + endRatio * sliceWidth
                                val segColor = getSegmentColorFor(seg)

                                val segLeftRad =
                                    if (segStartMs == 0L && isFirstSlice) fullCornerRadius
                                    else innerCornerRadius
                                val segRightRad =
                                    if (segEndMs == safeDurationMs && isLastSlice) fullCornerRadius
                                    else innerCornerRadius

                                drawCustomRoundRect(
                                    color = segColor.copy(alpha = 0.35f),
                                    topLeft = Offset(segStartX, yOffset - trackHeightPx / 2f),
                                    size = Size(segEndX - segStartX, trackHeightPx),
                                    topLeftRadius = segLeftRad,
                                    bottomLeftRadius = segLeftRad,
                                    topRightRadius = segRightRad,
                                    bottomRightRadius = segRightRad,
                                )
                            }
                        }

                        // 3. Material 3 Indeterminate loading shimmer
                        for ((tail, head) in indeterminateRanges) {
                            if (head <= tail) continue
                            val segStartX = tail * size.width
                            val segEndX = head * size.width

                            val activeStart = maxOf(sliceStartX, segStartX)
                            val activeEnd = minOf(sliceEndX, segEndX)
                            if (activeEnd > activeStart) {
                                drawCustomRoundRect(
                                    color = color,
                                    topLeft = Offset(activeStart, yOffset - trackHeightPx / 2f),
                                    size = Size(activeEnd - activeStart, trackHeightPx),
                                    topLeftRadius = innerCornerRadius,
                                    bottomLeftRadius = innerCornerRadius,
                                    topRightRadius = innerCornerRadius,
                                    bottomRightRadius = innerCornerRadius,
                                )
                            }
                        }
                    } else {
                        // --- NORMAL PLAYBACK / SCRUBBING ---

                        // 1. Base unplayed chapter track (inactive portion right of thumb gap)
                        val inactiveStart = maxOf(sliceStartX, inactiveLimit)
                        if (sliceEndX > inactiveStart) {
                            val inactLeftRad =
                                if (inactiveStart <= sliceStartX + 1f && isFirstSlice)
                                    fullCornerRadius
                                else innerCornerRadius

                            drawCustomRoundRect(
                                color = Color.White.copy(alpha = 0.20f),
                                topLeft = Offset(inactiveStart, yOffset - trackHeightPx / 2f),
                                size = Size(sliceEndX - inactiveStart, trackHeightPx),
                                topLeftRadius = inactLeftRad,
                                bottomLeftRadius = inactLeftRad,
                                topRightRadius = rightRadius,
                                bottomRightRadius = rightRadius,
                            )

                            // 2. Unplayed Segment overlays on top of unplayed track
                            for (seg in segments) {
                                val segStartMs = maxOf(slice.startMs, seg.startTicks)
                                val segEndMs = minOf(slice.endMs, seg.endTicks)
                                if (segEndMs > segStartMs) {
                                    val startRatio =
                                        ((segStartMs - slice.startMs).toFloat() / sliceDuration)
                                            .coerceIn(0f, 1f)
                                    val endRatio =
                                        ((segEndMs - slice.startMs).toFloat() / sliceDuration)
                                            .coerceIn(0f, 1f)

                                    val segStartX = sliceStartX + startRatio * sliceWidth
                                    val segEndX = sliceStartX + endRatio * sliceWidth
                                    val segColor = getSegmentColorFor(seg)

                                    val inactSegStart = maxOf(segStartX, inactiveLimit)
                                    if (segEndX > inactSegStart) {
                                        val segLeftRad =
                                            if (
                                                inactSegStart <= sliceStartX + 1f &&
                                                    segStartMs == 0L &&
                                                    isFirstSlice
                                            )
                                                fullCornerRadius
                                            else innerCornerRadius
                                        val segRightRad =
                                            if (
                                                segEndX >= sliceEndX - 1f &&
                                                    segEndMs == safeDurationMs &&
                                                    isLastSlice
                                            )
                                                fullCornerRadius
                                            else innerCornerRadius

                                        drawCustomRoundRect(
                                            color = segColor.copy(alpha = 0.35f),
                                            topLeft =
                                                Offset(inactSegStart, yOffset - trackHeightPx / 2f),
                                            size = Size(segEndX - inactSegStart, trackHeightPx),
                                            topLeftRadius = segLeftRad,
                                            bottomLeftRadius = segLeftRad,
                                            topRightRadius = segRightRad,
                                            bottomRightRadius = segRightRad,
                                        )
                                    }
                                }
                            }

                            // 3. Buffered progress overlay on inactive track
                            if (bufferedMs > slice.startMs) {
                                val bufRatio =
                                    ((minOf(slice.endMs, bufferedMs) - slice.startMs).toFloat() /
                                            sliceDuration)
                                        .coerceIn(0f, 1f)
                                val bufX = sliceStartX + bufRatio * sliceWidth
                                val bufEnd = minOf(sliceEndX, bufX)
                                if (bufEnd > inactiveStart) {
                                    val bufRightRad =
                                        if (bufEnd >= sliceEndX - 1f && isLastSlice)
                                            fullCornerRadius
                                        else innerCornerRadius

                                    drawCustomRoundRect(
                                        color = Color.White.copy(alpha = 0.35f),
                                        topLeft =
                                            Offset(inactiveStart, yOffset - trackHeightPx / 2f),
                                        size = Size(bufEnd - inactiveStart, trackHeightPx),
                                        topLeftRadius = inactLeftRad,
                                        bottomLeftRadius = inactLeftRad,
                                        topRightRadius = bufRightRad,
                                        bottomRightRadius = bufRightRad,
                                    )

                                    // Buffered Segment overlay
                                    for (seg in segments) {
                                        val segStartMs = maxOf(slice.startMs, seg.startTicks)
                                        val segEndMs =
                                            minOf(minOf(slice.endMs, bufferedMs), seg.endTicks)
                                        if (segEndMs > segStartMs) {
                                            val startRatio =
                                                ((segStartMs - slice.startMs).toFloat() /
                                                        sliceDuration)
                                                    .coerceIn(0f, 1f)
                                            val endRatio =
                                                ((segEndMs - slice.startMs).toFloat() /
                                                        sliceDuration)
                                                    .coerceIn(0f, 1f)

                                            val segStartX = sliceStartX + startRatio * sliceWidth
                                            val segEndX = sliceStartX + endRatio * sliceWidth
                                            val segColor = getSegmentColorFor(seg)

                                            val inactSegBufStart = maxOf(segStartX, inactiveLimit)
                                            val inactSegBufEnd = minOf(segEndX, bufEnd)
                                            if (inactSegBufEnd > inactSegBufStart) {
                                                val segLeftRad =
                                                    if (
                                                        inactSegBufStart <= sliceStartX + 1f &&
                                                            segStartMs == 0L &&
                                                            isFirstSlice
                                                    )
                                                        fullCornerRadius
                                                    else innerCornerRadius
                                                val segRightRad =
                                                    if (
                                                        inactSegBufEnd >= sliceEndX - 1f &&
                                                            segEndMs == safeDurationMs &&
                                                            isLastSlice
                                                    )
                                                        fullCornerRadius
                                                    else innerCornerRadius

                                                drawCustomRoundRect(
                                                    color = segColor.copy(alpha = 0.55f),
                                                    topLeft =
                                                        Offset(
                                                            inactSegBufStart,
                                                            yOffset - trackHeightPx / 2f,
                                                        ),
                                                    size =
                                                        Size(
                                                            inactSegBufEnd - inactSegBufStart,
                                                            trackHeightPx,
                                                        ),
                                                    topLeftRadius = segLeftRad,
                                                    bottomLeftRadius = segLeftRad,
                                                    topRightRadius = segRightRad,
                                                    bottomRightRadius = segRightRad,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Played active chapter track (left of thumb gap)
                        val activeStart = sliceStartX
                        val activeEnd = minOf(sliceEndX, activeLimit)
                        if (activeEnd > activeStart) {
                            val actRightRad =
                                if (activeEnd >= sliceEndX - 1f && isLastSlice) fullCornerRadius
                                else innerCornerRadius

                            drawCustomRoundRect(
                                color = color,
                                topLeft = Offset(activeStart, yOffset - trackHeightPx / 2f),
                                size = Size(activeEnd - activeStart, trackHeightPx),
                                topLeftRadius = leftRadius,
                                bottomLeftRadius = leftRadius,
                                topRightRadius = actRightRad,
                                bottomRightRadius = actRightRad,
                            )

                            // 5. Played Segment overlays on top of played active track (full 1.0f
                            // opacity)
                            for (seg in segments) {
                                val segStartMs = maxOf(slice.startMs, seg.startTicks)
                                val segEndMs = minOf(slice.endMs, seg.endTicks)
                                if (segEndMs > segStartMs) {
                                    val startRatio =
                                        ((segStartMs - slice.startMs).toFloat() / sliceDuration)
                                            .coerceIn(0f, 1f)
                                    val endRatio =
                                        ((segEndMs - slice.startMs).toFloat() / sliceDuration)
                                            .coerceIn(0f, 1f)

                                    val segStartX = sliceStartX + startRatio * sliceWidth
                                    val segEndX = sliceStartX + endRatio * sliceWidth
                                    val segColor = getSegmentColorFor(seg)

                                    val actSegStart = maxOf(segStartX, activeStart)
                                    val actSegEnd = minOf(segEndX, activeEnd)
                                    if (actSegEnd > actSegStart) {
                                        val segLeftRad =
                                            if (
                                                actSegStart <= sliceStartX + 1f &&
                                                    segStartMs == 0L &&
                                                    isFirstSlice
                                            )
                                                fullCornerRadius
                                            else innerCornerRadius
                                        val segRightRad =
                                            if (
                                                actSegEnd >= sliceEndX - 1f &&
                                                    segEndMs == safeDurationMs &&
                                                    isLastSlice
                                            )
                                                fullCornerRadius
                                            else innerCornerRadius

                                        drawCustomRoundRect(
                                            color = segColor,
                                            topLeft =
                                                Offset(actSegStart, yOffset - trackHeightPx / 2f),
                                            size = Size(actSegEnd - actSegStart, trackHeightPx),
                                            topLeftRadius = segLeftRad,
                                            bottomLeftRadius = segLeftRad,
                                            topRightRadius = segRightRad,
                                            bottomRightRadius = segRightRad,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                sliceX = sliceEndX + gapPx
            }
        } else {
            // Fallback continuous track when duration is zero or unknown
            slicePath.reset()
            slicePath.addRoundRect(
                RoundRect(
                    rect =
                        Rect(
                            left = 0f,
                            top = yOffset - trackHeightPx / 2f,
                            right = size.width,
                            bottom = yOffset + trackHeightPx / 2f,
                        ),
                    cornerRadius = fullCornerRadius,
                )
            )

            clipPath(slicePath) {
                if (isBuffering) {
                    drawCustomRoundRect(
                        color = Color.White.copy(alpha = 0.20f),
                        topLeft = Offset(0f, yOffset - trackHeightPx / 2f),
                        size = Size(size.width, trackHeightPx),
                        topLeftRadius = fullCornerRadius,
                        bottomLeftRadius = fullCornerRadius,
                        topRightRadius = fullCornerRadius,
                        bottomRightRadius = fullCornerRadius,
                    )

                    for ((tail, head) in indeterminateRanges) {
                        if (head <= tail) continue
                        val segStartX = tail * size.width
                        val segEndX = head * size.width
                        if (segEndX > segStartX) {
                            drawCustomRoundRect(
                                color = color,
                                topLeft = Offset(segStartX, yOffset - trackHeightPx / 2f),
                                size = Size(segEndX - segStartX, trackHeightPx),
                                topLeftRadius = innerCornerRadius,
                                bottomLeftRadius = innerCornerRadius,
                                topRightRadius = innerCornerRadius,
                                bottomRightRadius = innerCornerRadius,
                            )
                        }
                    }
                } else {
                    if (inactiveLimit < size.width) {
                        drawCustomRoundRect(
                            color = Color.White.copy(alpha = 0.20f),
                            topLeft = Offset(inactiveLimit, yOffset - trackHeightPx / 2f),
                            size = Size(size.width - inactiveLimit, trackHeightPx),
                            topLeftRadius = innerCornerRadius,
                            bottomLeftRadius = innerCornerRadius,
                            topRightRadius = fullCornerRadius,
                            bottomRightRadius = fullCornerRadius,
                        )

                        val bufferedX =
                            (bufferedProgress.coerceIn(0f, 1f) * size.width).coerceAtMost(
                                size.width
                            )
                        if (bufferedX > inactiveLimit) {
                            val bufRightRadius =
                                if (bufferedX < size.width - 1f) innerCornerRadius
                                else fullCornerRadius
                            drawCustomRoundRect(
                                color = Color.White.copy(alpha = 0.40f),
                                topLeft = Offset(inactiveLimit, yOffset - trackHeightPx / 2f),
                                size = Size(bufferedX - inactiveLimit, trackHeightPx),
                                topLeftRadius = innerCornerRadius,
                                bottomLeftRadius = innerCornerRadius,
                                topRightRadius = bufRightRadius,
                                bottomRightRadius = bufRightRadius,
                            )
                        }
                    }

                    if (activeLimit > 0f) {
                        drawCustomRoundRect(
                            color = color,
                            topLeft = Offset(0f, yOffset - trackHeightPx / 2f),
                            size = Size(activeLimit, trackHeightPx),
                            topLeftRadius = fullCornerRadius,
                            bottomLeftRadius = fullCornerRadius,
                            topRightRadius = innerCornerRadius,
                            bottomRightRadius = innerCornerRadius,
                        )
                    }
                }
            }
        }

        // Draw Material 3 Expressive vertical bar thumb (hidden during buffering)
        if (thumbAlpha > 0.01f) {
            val thumbCornerRadius = CornerRadius(thumbWidthPx / 2f, thumbWidthPx / 2f)
            drawRoundRect(
                color = color.copy(alpha = thumbAlpha),
                topLeft = Offset(clampedThumbX - thumbWidthPx / 2f, yOffset - thumbHeight / 2f),
                size = Size(thumbWidthPx, thumbHeight),
                cornerRadius = thumbCornerRadius,
            )
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun VideoPlayerSeekBarPreview() {
    FindroidTheme { VideoPlayerSeekBar(progress = 0.4f, onSeek = {}) }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun VideoPlayerSeekBarWithSegmentsPreview() {
    FindroidTheme {
        VideoPlayerSeekBar(
            progress = 0.20f,
            onSeek = {},
            durationMs = 1_800_000L,
            bufferedProgress = 0.5f,
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
                        startTicks = 90_000L,
                        endTicks = 250_000L,
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
