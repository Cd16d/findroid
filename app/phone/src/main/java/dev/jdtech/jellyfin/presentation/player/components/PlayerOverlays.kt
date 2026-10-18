package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import kotlin.math.roundToInt

/**
 * Vertical liquid-fill HUD overlay displaying real-time volume or screen brightness levels.
 *
 * @param value Current level normalized between 0.0f and 1.0f.
 * @param isVolume True if representing media volume, false for screen brightness.
 * @param visible Whether the overlay is currently animated into view.
 * @param modifier Optional [Modifier] applied to the animated HUD container.
 */
@Composable
fun VolumeBrightnessOverlay(
    value: Float,
    isVolume: Boolean,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.9f, animationSpec = tween(150)),
        exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.9f, animationSpec = tween(200)),
        modifier = modifier,
    ) {
        val fillPercent = value.coerceIn(0f, 1f)
        Box(
            modifier =
                Modifier.size(width = 46.dp, height = 180.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF262626).copy(alpha = 0.85f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Liquid fill from bottom to top
            Box(
                modifier =
                    Modifier.fillMaxWidth().fillMaxHeight(fillPercent).background(Color.White)
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
                    val level = (fillPercent * 100).roundToInt()
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

            val isBottomCoveredByFill = fillPercent > 0.18f
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
 * Ripple feedback overlay indicating a +/- 10-second double-tap seek gesture.
 *
 * @param isForward True for fast-forward (+10s), false for rewind (-10s).
 * @param visible Whether the seek animation ripple is visible.
 * @param modifier Optional [Modifier] applied to the ripple container.
 */
@Composable
fun DoubleTapSeekOverlay(
    isForward: Boolean,
    visible: Boolean,
    modifier: Modifier = Modifier,
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
                    .background(Color(0xFF262626).copy(alpha = 0.75f)),
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
                    text = if (isForward) "+10s" else "-10s",
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
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
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
                androidx.compose.foundation.layout.PaddingValues(
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
                    painter = painterResource(id = CoreR.drawable.ic_arrow_right),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * Screen lock indicator and temporary unlock button shown when the screen controls are locked.
 *
 * @param isLocked Whether touch controls are currently locked.
 * @param showUnlockButton Whether the temporary unlock button should be shown.
 * @param onUnlock Callback triggered when tapping the unlock button.
 * @param modifier Optional [Modifier] applied to the lock overlay root.
 */
@Composable
fun ScreenLockOverlay(
    isLocked: Boolean,
    showUnlockButton: Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isLocked) return

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showUnlockButton,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier =
                Modifier.align(Alignment.TopStart)
                    .padding(
                        start =
                            insets.calculateLeftPadding(
                                androidx.compose.ui.unit.LayoutDirection.Ltr
                            ) + 24.dp,
                        top = insets.calculateTopPadding() + 24.dp,
                    ),
        ) {
            Box(
                modifier =
                    Modifier.clip(CircleShape)
                        .background(Color(0xFF262626).copy(alpha = 0.85f))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.3f),
                            shape = CircleShape,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, radius = 24.dp),
                            onClick = onUnlock,
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(id = CoreR.drawable.ic_unlock),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White,
                    )
                    Text(
                        text = stringResource(id = PlayerR.string.unlock),
                        style = MaterialTheme.typography.labelLarge,
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
