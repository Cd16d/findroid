package dev.jdtech.jellyfin.presentation.player.components

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.theme.PlayerOverlayBackground
import dev.jdtech.jellyfin.core.presentation.theme.PlayerSheetBackground
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Vertical pill overlay displaying current volume or screen brightness level. Features a liquid
 * fill animation indicating level percentage, a top numeric indicator, and a bottom context icon
 * representing volume or brightness level.
 *
 * @param value Current volume or brightness level normalized between 0.0 and 1.0.
 * @param isVolume True for volume adjustment overlay; false for screen brightness.
 * @param visible Whether the overlay is currently visible.
 * @param modifier Optional [Modifier] applied to the overlay container.
 * @param animate Whether transitions between values should be animated with spring physics.
 */
@Composable
fun VolumeBrightnessOverlay(
    value: Float,
    isVolume: Boolean,
    visible: Boolean,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val targetValue = value.coerceIn(0f, 1f)
    val animatable = remember { Animatable(targetValue) }
    var wasAnimate by remember { mutableStateOf(animate) }

    LaunchedEffect(targetValue, animate) {
        val justReleasedDrag = !wasAnimate && animate
        wasAnimate = animate

        if (!animate || justReleasedDrag) {
            animatable.snapTo(targetValue)
        } else if (animatable.value != targetValue) {
            animatable.animateTo(
                targetValue = targetValue,
                animationSpec =
                    spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy,
                    ),
            )
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.9f, animationSpec = tween(150)),
        exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.9f, animationSpec = tween(200)),
        modifier = modifier,
    ) {
        val fillPercent = animatable.value.coerceIn(0f, 1f)
        val level = (fillPercent * 100).roundToInt()
        Box(
            modifier =
                Modifier.size(width = 46.dp, height = 180.dp)
                    .clip(CircleShape)
                    .background(PlayerOverlayBackground.copy(alpha = 0.85f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Liquid fill from bottom to top
            Box(
                modifier =
                    Modifier.fillMaxWidth().fillMaxHeight(fillPercent).background(Color.White)
            )

            // Numeric indicator at top (0 to 100, without percentage sign)
            val isTopCoveredByFill = fillPercent > 0.88f
            Text(
                text = level.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isTopCoveredByFill) Color.Black else Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
            )

            // Animated icon at bottom
            val iconRes =
                if (isVolume) {
                    when {
                        fillPercent <= 0.01f -> CoreR.drawable.ic_volume_off
                        fillPercent <= 0.33f -> CoreR.drawable.ic_volume_33
                        fillPercent <= 0.66f -> CoreR.drawable.ic_volume_66
                        else -> CoreR.drawable.ic_volume_100
                    }
                } else {
                    when {
                        level <= 0 -> CoreR.drawable.ic_sun_0
                        level <= 12 -> CoreR.drawable.ic_sun_12
                        level <= 25 -> CoreR.drawable.ic_sun_25
                        level <= 37 -> CoreR.drawable.ic_sun_37
                        level <= 50 -> CoreR.drawable.ic_sun_50
                        level <= 62 -> CoreR.drawable.ic_sun_62
                        level <= 75 -> CoreR.drawable.ic_sun_75
                        level <= 87 -> CoreR.drawable.ic_sun_87
                        else -> CoreR.drawable.ic_sun
                    }
                }

            val isBottomCoveredByFill = fillPercent > 0.15f
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier =
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).size(24.dp),
                tint = if (isBottomCoveredByFill) Color.Black else Color.White,
            )
        }
    }
}

/**
 * Floating seek overlay displayed upon double-tapping the left or right sides of the screen. Shows
 * an animated rewind or fast-forward icon and the accumulated seek seconds (e.g. -10s, +20s).
 *
 * @param isForward True for fast-forward, false for rewind.
 * @param visible Whether the overlay is currently visible.
 * @param seekSeconds Total accumulated seek duration in seconds.
 * @param modifier Optional [Modifier] applied to the overlay container.
 */
@Composable
fun DoubleTapSeekOverlay(
    isForward: Boolean,
    visible: Boolean,
    modifier: Modifier = Modifier,
    seekSeconds: Int = 10,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier.size(80.dp)
                    .clip(CircleShape)
                    .background(PlayerOverlayBackground.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter =
                        painterResource(
                            id =
                                if (isForward) CoreR.drawable.ic_fast_forward
                                else CoreR.drawable.ic_rewind
                        ),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = Color.White,
                )
                Text(
                    text = if (isForward) "+${seekSeconds}s" else "-${seekSeconds}s",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

/**
 * Floating skip button for skipping segments like intros, credits, or recaps.
 *
 * @param stringRes String resource ID for the skip action label (e.g. Skip Intro).
 * @param onClick Callback triggered when the button is clicked.
 * @param visible Whether the skip button is displayed.
 * @param modifier Optional [Modifier] applied to the skip button container.
 */
@Composable
fun FloatingSkipButton(
    stringRes: Int,
    onClick: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.9f, animationSpec = tween(200)),
        exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.9f, animationSpec = tween(200)),
        modifier = modifier,
    ) {
        Button(
            onClick = onClick,
            shape = CircleShape,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
            contentPadding =
                PaddingValues(
                    horizontal = 20.dp,
                    vertical = 10.dp,
                ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(id = stringRes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_skip_forward),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Screen lock indicator and slide-to-unlock widget shown when the screen controls are locked.
 *
 * @param isLocked Whether touch controls are currently locked.
 * @param showUnlockButton Whether the temporary unlock widget should be shown.
 * @param onUnlock Callback triggered when sliding to unlock.
 * @param modifier Optional [Modifier] applied to the lock overlay root.
 * @param onKeepAlive Optional callback to refresh the auto-hide timer during interactions.
 */
@Composable
fun ScreenLockOverlay(
    isLocked: Boolean,
    showUnlockButton: Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    onKeepAlive: () -> Unit = {},
) {
    if (!isLocked) return

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    var isDragging by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showUnlockButton || isDragging,
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.9f, animationSpec = tween(200)),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.9f, animationSpec = tween(180)),
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = insets.calculateBottomPadding() + 32.dp),
        ) {
            SlideToUnlock(
                onUnlock = onUnlock,
                onDraggingChanged = { isDragging = it },
                onKeepAlive = onKeepAlive,
            )
        }
    }
}

/**
 * Slide-to-unlock pill widget with a draggable thumb.
 *
 * @param onUnlock Callback triggered when slider reaches completion threshold.
 * @param modifier Optional [Modifier].
 * @param onDraggingChanged Callback invoked when drag begins or ends.
 * @param onKeepAlive Callback invoked periodically during drag to keep overlay alive.
 */
@Composable
fun SlideToUnlock(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    onDraggingChanged: (Boolean) -> Unit = {},
    onKeepAlive: () -> Unit = {},
) {
    val trackWidthDp = 260.dp
    val trackHeightDp = 58.dp
    val thumbSizeDp = 48.dp
    val thumbPaddingDp = 5.dp

    val density = LocalDensity.current
    val trackWidthPx = with(density) { trackWidthDp.toPx() }
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }
    val thumbPaddingPx = with(density) { thumbPaddingDp.toPx() }
    val maxDragPx = (trackWidthPx - thumbSizePx - (thumbPaddingPx * 2)).coerceAtLeast(1f)

    val coroutineScope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val haptics = rememberPlayerHaptics()

    val currentOffset = dragOffset.value
    val progress = (currentOffset / maxDragPx).coerceIn(0f, 1f)
    val currentOnUnlock by rememberUpdatedState(onUnlock)
    val currentOnKeepAlive by rememberUpdatedState(onKeepAlive)
    val currentOnDraggingChanged by rememberUpdatedState(onDraggingChanged)

    Box(
        modifier =
            modifier
                .width(trackWidthDp)
                .height(trackHeightDp)
                .clip(CircleShape)
                .background(PlayerSheetBackground.copy(alpha = 0.85f))
                .border(
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Label text centered in track with chevron direction indicator, fading out as dragged
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(start = thumbSizeDp + 8.dp, end = 16.dp)
                    .graphicsLayer { alpha = (1f - progress * 1.6f).coerceIn(0f, 0.9f) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(id = PlayerR.string.slide_to_unlock),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                painter = painterResource(id = CoreR.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.White.copy(alpha = 0.7f),
            )
        }

        // Unlock confirmation label appearing as thumb reaches the end
        if (progress >= 0.6f) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(start = 24.dp, end = thumbSizeDp + 8.dp)
                        .graphicsLayer { alpha = ((progress - 0.6f) / 0.35f).coerceIn(0f, 1f) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(id = PlayerR.string.unlock),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Draggable thumb
        Box(
            modifier =
                Modifier.offset {
                        IntOffset(x = (thumbPaddingPx + currentOffset).roundToInt(), y = 0)
                    }
                    .size(thumbSizeDp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .pointerInput(maxDragPx) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                haptics.gestureStart()
                                currentOnDraggingChanged(true)
                                currentOnKeepAlive()
                            },
                            onDragEnd = {
                                currentOnDraggingChanged(false)
                                if (dragOffset.value >= maxDragPx * 0.95f) {
                                    haptics.confirm()
                                    currentOnUnlock()
                                    coroutineScope.launch { dragOffset.snapTo(0f) }
                                } else {
                                    if (dragOffset.value > 12f) {
                                        haptics.reject()
                                    }
                                    currentOnKeepAlive()
                                    coroutineScope.launch {
                                        dragOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec =
                                                spring(
                                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                                    stiffness = Spring.StiffnessMediumLow,
                                                ),
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                currentOnDraggingChanged(false)
                                if (dragOffset.value > 12f) {
                                    haptics.reject()
                                }
                                currentOnKeepAlive()
                                coroutineScope.launch {
                                    dragOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec =
                                            spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessMediumLow,
                                            ),
                                    )
                                }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                coroutineScope.launch {
                                    dragOffset.snapTo(
                                        (dragOffset.value + dragAmount).coerceIn(0f, maxDragPx)
                                    )
                                }
                                currentOnKeepAlive()
                            },
                        )
                    },
            contentAlignment = Alignment.Center,
        ) {
            val iconRes =
                if (progress >= 0.95f) CoreR.drawable.ic_unlock else CoreR.drawable.ic_lock
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/**
 * Visual feedback overlay indicating video resize mode changes (e.g. pinch-to-zoom). Displays a
 * glowing edge flash around the screen borders (similar to YouTube) and a top pill badge indicating
 * the active aspect ratio mode.
 *
 * @param visible Whether the feedback overlay is currently visible.
 * @param resizeMode Current [AspectRatioFrameLayout] resize mode (Fit, Zoom, Fill).
 * @param cornerRadius Optional corner radius applied to the edge glow border.
 * @param modifier Optional [Modifier] applied to the root container.
 */
@OptIn(UnstableApi::class)
@Composable
fun ZoomFeedbackOverlay(
    visible: Boolean,
    resizeMode: Int,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 0.dp,
) {
    val isZoomMode = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    val flashAlpha = remember { Animatable(0f) }

    LaunchedEffect(visible, resizeMode) {
        if (visible && isZoomMode) {
            flashAlpha.snapTo(1f)
            flashAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
            )
        } else {
            flashAlpha.snapTo(0f)
        }
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()

    Box(modifier = modifier.fillMaxSize().clip(RoundedCornerShape(cornerRadius))) {
        // Glowing Edge Flash around the video bounds (only active in Zoom mode)
        if (isZoomMode && flashAlpha.value > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val glowDepth = minOf(32.dp.toPx(), size.width / 2f, size.height / 2f)
                val alpha = flashAlpha.value

                val glowColors =
                    listOf(
                        Color.White.copy(alpha = alpha * 0.55f),
                        Color.White.copy(alpha = alpha * 0.22f),
                        Color.Transparent,
                    )
                val glowColorsReversed =
                    listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = alpha * 0.22f),
                        Color.White.copy(alpha = alpha * 0.55f),
                    )

                // Top Edge trapezoid
                val topPath =
                    Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width - glowDepth, glowDepth)
                        lineTo(glowDepth, glowDepth)
                        close()
                    }
                drawPath(
                    path = topPath,
                    brush =
                        Brush.verticalGradient(
                            colors = glowColors,
                            startY = 0f,
                            endY = glowDepth,
                        ),
                )

                // Bottom Edge trapezoid
                val bottomPath =
                    Path().apply {
                        moveTo(0f, size.height)
                        lineTo(size.width, size.height)
                        lineTo(size.width - glowDepth, size.height - glowDepth)
                        lineTo(glowDepth, size.height - glowDepth)
                        close()
                    }
                drawPath(
                    path = bottomPath,
                    brush =
                        Brush.verticalGradient(
                            colors = glowColorsReversed,
                            startY = size.height - glowDepth,
                            endY = size.height,
                        ),
                )

                // Left Edge trapezoid
                val leftPath =
                    Path().apply {
                        moveTo(0f, 0f)
                        lineTo(0f, size.height)
                        lineTo(glowDepth, size.height - glowDepth)
                        lineTo(glowDepth, glowDepth)
                        close()
                    }
                drawPath(
                    path = leftPath,
                    brush =
                        Brush.horizontalGradient(
                            colors = glowColors,
                            startX = 0f,
                            endX = glowDepth,
                        ),
                )

                // Right Edge trapezoid
                val rightPath =
                    Path().apply {
                        moveTo(size.width, 0f)
                        lineTo(size.width, size.height)
                        lineTo(size.width - glowDepth, size.height - glowDepth)
                        lineTo(size.width - glowDepth, glowDepth)
                        close()
                    }
                drawPath(
                    path = rightPath,
                    brush =
                        Brush.horizontalGradient(
                            colors = glowColorsReversed,
                            startX = size.width - glowDepth,
                            endX = size.width,
                        ),
                )
            }
        }

        // Floating pill badge at top center (YouTube-style)
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.85f, animationSpec = tween(150)),
            exit = fadeOut(tween(250)) + scaleOut(targetScale = 0.9f, animationSpec = tween(250)),
            modifier =
                Modifier.align(Alignment.TopCenter)
                    .padding(top = insets.calculateTopPadding() + 20.dp),
        ) {
            Box(
                modifier =
                    Modifier.clip(CircleShape)
                        .background(PlayerSheetBackground.copy(alpha = 0.85f))
                        .border(
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            shape = CircleShape,
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(id = CoreR.drawable.ic_aspect_ratio),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White,
                    )
                    val labelRes =
                        when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM ->
                                PlayerR.string.aspect_ratio_zoom

                            AspectRatioFrameLayout.RESIZE_MODE_FILL ->
                                PlayerR.string.aspect_ratio_fill

                            else -> PlayerR.string.aspect_ratio_fit
                        }
                    Text(
                        text = stringResource(id = labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun VolumeOverlayPreview() {
    FindroidTheme {
        VolumeBrightnessOverlay(
            value = 0.65f,
            isVolume = true,
            visible = true,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun BrightnessOverlayPreview() {
    FindroidTheme {
        VolumeBrightnessOverlay(
            value = 0.45f,
            isVolume = false,
            visible = true,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun DoubleTapSeekForwardPreview() {
    FindroidTheme {
        DoubleTapSeekOverlay(
            isForward = true,
            visible = true,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun DoubleTapSeekRewindPreview() {
    FindroidTheme {
        DoubleTapSeekOverlay(
            isForward = false,
            visible = true,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FloatingSkipButtonPreview() {
    FindroidTheme {
        FloatingSkipButton(
            stringRes = PlayerR.string.player_controls_skip_intro,
            onClick = {},
            visible = true,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ScreenLockOverlayPreview() {
    FindroidTheme {
        ScreenLockOverlay(
            isLocked = true,
            showUnlockButton = true,
            onUnlock = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun SlideToUnlockPreview() {
    FindroidTheme {
        Box(
            modifier = Modifier.size(width = 300.dp, height = 100.dp),
            contentAlignment = Alignment.Center,
        ) {
            SlideToUnlock(onUnlock = {})
        }
    }
}

@OptIn(UnstableApi::class)
@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 400, heightDp = 200)
@Composable
private fun ZoomFeedbackOverlayPreview() {
    FindroidTheme {
        ZoomFeedbackOverlay(
            visible = true,
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        )
    }
}
