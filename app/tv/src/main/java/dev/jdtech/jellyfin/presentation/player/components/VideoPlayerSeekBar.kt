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
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.presentation.calculateBarSlices
import dev.jdtech.jellyfin.player.core.presentation.drawCustomRoundRect
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

private fun getM3SegmentFraction(phase: Float, startPhase: Float, endPhase: Float): Float {
    if (phase <= startPhase) return 0f
    if (phase >= endPhase) return 1f
    val fraction = (phase - startPhase) / (endPhase - startPhase)
    return FastOutSlowInEasing.transform(fraction)
}

@Composable
fun VideoPlayerSeekBar(
    progress: Float,
    onSeek: (seekProgress: Float) -> Unit,
    modifier: Modifier = Modifier,
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
            targetValue = if (isHighlight) 2.dp else 3.dp,
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
        val thumbWidth = animatedThumbWidth.toPx()
        val fullCornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
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

        val clampedThumbX = thumbX.coerceIn(thumbWidth / 2f, size.width - thumbWidth / 2f)
        val thumbLeft = clampedThumbX - thumbWidth / 2f
        val thumbRight = clampedThumbX + thumbWidth / 2f

        val activeLimit = (thumbLeft - thumbGap).coerceAtLeast(0f)
        val inactiveLimit = (thumbRight + thumbGap).coerceAtMost(size.width)

        if (slices.isNotEmpty() && safeDurationMs > 0L) {
            if (isBuffering) {
                // Background track for all slices
                var currentX = 0f
                for ((startMs, endMs, _, _, color1) in slices) {
                    val sliceDuration = (endMs - startMs).coerceAtLeast(1L)
                    val sliceFraction = sliceDuration.toFloat() / safeDurationMs
                    val sliceWidth = sliceFraction * trackAvailableWidth
                    val sliceStartX = currentX
                    val sliceEndX = sliceStartX + sliceWidth

                    val segmentColor = color1
                    val trackBgColor =
                        segmentColor?.copy(alpha = 0.45f) ?: Color.White.copy(alpha = 0.28f)

                    drawCustomRoundRect(
                        color = trackBgColor,
                        topLeft = Offset(sliceStartX, yOffset - trackHeight / 2f),
                        size = Size(sliceWidth, trackHeight),
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
                    for ((startMs, endMs, _, _, color1) in slices) {
                        val sliceDuration = (endMs - startMs).coerceAtLeast(1L)
                        val sliceFraction = sliceDuration.toFloat() / safeDurationMs
                        val sliceWidth = sliceFraction * trackAvailableWidth
                        val sliceStartX = sliceX
                        val sliceEndX = sliceStartX + sliceWidth
                        val segmentColor = color1
                        val trackFgColor = segmentColor ?: color

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
                                topLeft = Offset(activeStart, yOffset - trackHeight / 2f),
                                size = Size(activeEnd - activeStart, trackHeight),
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
                for ((startMs, endMs, _, _, color1) in slices) {
                    val sliceDuration = (endMs - startMs).coerceAtLeast(1L)
                    val sliceFraction = sliceDuration.toFloat() / safeDurationMs
                    val sliceWidth = sliceFraction * trackAvailableWidth
                    val sliceStartX = sliceX
                    val sliceEndX = sliceStartX + sliceWidth

                    val segmentColor = color1
                    val trackBgColor =
                        segmentColor?.copy(alpha = 0.45f) ?: Color.White.copy(alpha = 0.28f)
                    val trackFgColor = segmentColor ?: color

                    // Inactive track portion (right of thumb gap)
                    val inactiveStart = maxOf(sliceStartX, inactiveLimit)
                    if (sliceEndX > inactiveStart) {
                        val leftRadius =
                            if (inactiveStart > sliceStartX + 1f) smallCornerRadius
                            else fullCornerRadius
                        drawCustomRoundRect(
                            color = trackBgColor,
                            topLeft = Offset(inactiveStart, yOffset - trackHeight / 2f),
                            size = Size(sliceEndX - inactiveStart, trackHeight),
                            topLeftRadius = leftRadius,
                            bottomLeftRadius = leftRadius,
                            topRightRadius = fullCornerRadius,
                            bottomRightRadius = fullCornerRadius,
                        )
                    }

                    // Active track portion (left of thumb gap)
                    val activeStart = sliceStartX
                    val activeEnd = minOf(sliceEndX, activeLimit)
                    if (activeEnd > activeStart) {
                        val rightRadius =
                            if (activeEnd < sliceEndX) smallCornerRadius else fullCornerRadius
                        drawCustomRoundRect(
                            color = trackFgColor,
                            topLeft = Offset(activeStart, yOffset - trackHeight / 2f),
                            size = Size(activeEnd - activeStart, trackHeight),
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
                    color = Color.White.copy(alpha = 0.28f),
                    topLeft = Offset(0f, yOffset - trackHeight / 2f),
                    size = Size(size.width, trackHeight),
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
                            color = color,
                            topLeft = Offset(segStartX, yOffset - trackHeight / 2f),
                            size = Size(segEndX - segStartX, trackHeight),
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
                        color = Color.White.copy(alpha = 0.28f),
                        topLeft = Offset(inactiveLimit, yOffset - trackHeight / 2f),
                        size = Size(size.width - inactiveLimit, trackHeight),
                        topLeftRadius = smallCornerRadius,
                        bottomLeftRadius = smallCornerRadius,
                        topRightRadius = fullCornerRadius,
                        bottomRightRadius = fullCornerRadius,
                    )
                }

                // Active track (left of thumb)
                if (activeLimit > 0f) {
                    drawCustomRoundRect(
                        color = color,
                        topLeft = Offset(0f, yOffset - trackHeight / 2f),
                        size = Size(activeLimit, trackHeight),
                        topLeftRadius = fullCornerRadius,
                        bottomLeftRadius = fullCornerRadius,
                        topRightRadius = smallCornerRadius,
                        bottomRightRadius = smallCornerRadius,
                    )
                }
            }
        }

        // Draw Material 3 Expressive vertical bar thumb (hidden during buffering)
        if (thumbAlpha > 0.01f) {
            val activeSlice = slices.firstOrNull { currentPlayMs in it.startMs..it.endMs }
            val activeSegmentColor = activeSlice?.color
            val thumbColor = activeSegmentColor ?: color
            val thumbCornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f)
            drawRoundRect(
                color = thumbColor.copy(alpha = thumbAlpha),
                topLeft = Offset(clampedThumbX - thumbWidth / 2f, yOffset - thumbHeight / 2f),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = thumbCornerRadius,
            )
        }
    }
}

@Preview
@Composable
private fun VideoPlayerSeekBarPreview() {
    FindroidTheme { VideoPlayerSeekBar(progress = 0.4f, onSeek = {}) }
}
