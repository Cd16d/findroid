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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidSegmentType
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.presentation.calculateProgressFromX
import dev.jdtech.jellyfin.player.core.presentation.drawCustomRoundRect
import dev.jdtech.jellyfin.player.core.presentation.getSegmentColor
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import kotlin.math.abs

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

/**
 * Interactive timeline progress bar with chapter slicing, segment color accents, dynamic touch
 * expansion, and scrub position tracking based on Material 3 Expressive guidelines.
 *
 * @param progress Current playback completion ratio between 0.0f and 1.0f.
 * @param onSeek Callback invoked when the user commits a seek gesture, passing target progress
 *   fraction.
 * @param state Active [VideoPlayerState] instance.
 * @param modifier Optional [Modifier] applied to the canvas.
 * @param bufferedProgress Fraction of media buffered between 0.0f and 1.0f.
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
    val slicePath = remember { Path() }

    val safeDurationMs = if (durationMs > 0L) durationMs else 0L

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

    Canvas(
        modifier =
            modifier.fillMaxWidth().height(36.dp).padding(horizontal = 4.dp).pointerInput(
                safeDurationMs
            ) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    val startY = down.position.y
                    var currentFraction = calculateProgressFromX(startX, size.width.toFloat())
                    var scrubPos = (currentFraction * safeDurationMs).toLong()

                    fun getChapterIndex(posMs: Long): Int {
                        val chs = currentChapters
                        if (chs.isEmpty()) return -1
                        for (i in chs.indices.reversed()) {
                            if (posMs >= chs[i].startPosition) return i
                        }
                        return 0
                    }

                    var lastChapterIdx = getChapterIndex(scrubPos)
                    var isScrubbingEngaged = false
                    var isAborted = false
                    var releasedNormally = false

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.pressed) {
                                val totalDx = change.position.x - startX
                                val totalDy = change.position.y - startY

                                if (!isScrubbingEngaged) {
                                    // If moving upward or primarily vertical -> swipe up (e.g.
                                    // system Home/PiP gesture), abort seek immediately
                                    if (abs(totalDy) > touchSlop && abs(totalDy) > abs(totalDx)) {
                                        isAborted = true
                                        break
                                    } else if (
                                        abs(totalDx) > touchSlop && abs(totalDx) > abs(totalDy)
                                    ) {
                                        // Intentional horizontal drag -> engage scrubbing
                                        isScrubbingEngaged = true
                                        isDragging = true
                                        haptics.gestureStart()
                                        currentState?.resetTimeout()
                                    }
                                }

                                if (isScrubbingEngaged) {
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
                                }
                            } else {
                                // Pointer lifted (UP)
                                if (!isAborted) {
                                    val totalDx = change.position.x - startX
                                    val totalDy = change.position.y - startY
                                    // Valid if dragging was engaged or if it was an intentional
                                    // stationary tap
                                    if (
                                        isScrubbingEngaged ||
                                            (abs(totalDx) <= touchSlop && abs(totalDy) <= touchSlop)
                                    ) {
                                        change.consume()
                                        releasedNormally = true
                                        currentState?.resetTimeout()
                                        if (isScrubbingEngaged) {
                                            haptics.gestureEnd()
                                        }
                                        currentFraction =
                                            calculateProgressFromX(
                                                change.position.x,
                                                size.width.toFloat(),
                                            )
                                        currentOnSeek(currentFraction)
                                        scrubPos = (currentFraction * safeDurationMs).toLong()
                                        currentOnScrubbingChanged(
                                            false,
                                            scrubPos,
                                            change.position.x,
                                        )
                                    }
                                }
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

        val currentProgressFraction = (if (isDragging) dragProgress else progress).coerceIn(0f, 1f)
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
                0L -> getSegmentColor(seg.type) ?: primaryColor
                null -> getSegmentColor(seg.type) ?: primaryColor
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
                                    color = primaryColor,
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
                                color = primaryColor,
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
                                color = primaryColor,
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
                            color = primaryColor,
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

        // Draw Thumb cursor bar (hidden during buffering)
        if (thumbAlpha > 0.01f) {
            drawRoundRect(
                color = primaryColor.copy(alpha = thumbAlpha),
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
