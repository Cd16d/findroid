package dev.jdtech.jellyfin.presentation.player.components

import android.content.res.Configuration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.presentation.drawCustomRoundRect
import dev.jdtech.jellyfin.presentation.components.SelectableTrackItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * Maps playback speed to a 0f...1f slider fraction, placing 1.0f precisely at the center (0.5f).
 */
private fun speedToFraction(speed: Float, minSpeed: Float, maxSpeed: Float): Float {
    return if (speed <= 1.0f) {
        val span = (1.0f - minSpeed).coerceAtLeast(0.01f)
        (0.5f * (speed - minSpeed) / span).coerceIn(0f, 0.5f)
    } else {
        val span = (maxSpeed - 1.0f).coerceAtLeast(0.01f)
        (0.5f + 0.5f * (speed - 1.0f) / span).coerceIn(0.5f, 1f)
    }
}

/** Maps a 0f...1f slider fraction to playback speed, where 0.5f maps to 1.0f. */
private fun fractionToSpeed(fraction: Float, minSpeed: Float, maxSpeed: Float): Float {
    val rawSpeed =
        if (fraction <= 0.5f) {
            val ratio = (fraction / 0.5f).coerceIn(0f, 1f)
            minSpeed + ratio * (1.0f - minSpeed)
        } else {
            val ratio = ((fraction - 0.5f) / 0.5f).coerceIn(0f, 1f)
            1.0f + ratio * (maxSpeed - 1.0f)
        }
    return (round(rawSpeed * 20f) / 20f).coerceIn(0.05f, 4.0f)
}

/**
 * Custom playback speed picker featuring fine +/- 0.05-step buttons, a centered interactive slider
 * whose active track extends from 1.0x, and adaptive presets: a non-scrolling row of chips for
 * phone and a full selectable list for tablet.
 *
 * @param currentSpeed Active playback speed factor.
 * @param onSpeedChange Callback invoked when speed is changed.
 * @param modifier Optional [Modifier] applied to root container.
 * @param isTabletLayout True to display the full vertical list for tablet, false for phone chips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSpeedPicker(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isTabletLayout: Boolean = false,
) {
    var speedState by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }
    val minSpeed = minOf(0.25f, speedState)
    val maxSpeed = maxOf(2.0f, speedState)

    val configuration = LocalConfiguration.current
    val windowDpSize = LocalWindowInfo.current.containerDpSize
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isWide = windowDpSize.width >= 600.dp || isLandscape

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    vertical = if (isLandscape) 8.dp else 12.dp,
                    horizontal = 16.dp,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Current speed readout (e.g. "1.00x")
        Text(
            text = String.format(Locale.US, "%.2fx", speedState),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )

        Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 16.dp))

        // Minus Button, Centered Slider, Plus Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Minus Button (-0.05 step)
            Box(
                modifier =
                    Modifier.size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF262626))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, radius = 23.dp),
                            enabled = speedState > 0.05f,
                            onClick = {
                                val next =
                                    (round((speedState - 0.05f) * 100f) / 100f).coerceAtLeast(0.05f)
                                speedState = next
                                onSpeedChange(next)
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_minus),
                    contentDescription = "-",
                    modifier = Modifier.size(20.dp),
                    tint = if (speedState > 0.05f) Color.White else Color.White.copy(alpha = 0.3f),
                )
            }

            // Slider with Centered Track (active bar starts from 1.0x at 50%)
            val sliderFraction = speedToFraction(speedState, minSpeed, maxSpeed)
            val sliderInteractionSource = remember { MutableInteractionSource() }
            val isPressed by sliderInteractionSource.collectIsPressedAsState()
            val isDragged by sliderInteractionSource.collectIsDraggedAsState()
            val isInteracting = isPressed || isDragged

            val animatedTrackHeight by
                animateDpAsState(
                    targetValue = if (isInteracting) 18.dp else 16.dp,
                    animationSpec = tween(150),
                    label = "speedTrackHeight",
                )
            val animatedThumbWidth by
                animateDpAsState(
                    targetValue = if (isInteracting) 6.dp else 4.dp,
                    animationSpec = tween(150),
                    label = "speedThumbWidth",
                )
            val animatedThumbHeight by
                animateDpAsState(
                    targetValue = if (isInteracting) 36.dp else 32.dp,
                    animationSpec = tween(150),
                    label = "speedThumbHeight",
                )

            Slider(
                value = sliderFraction,
                onValueChange = { f ->
                    val snapped = fractionToSpeed(f, minSpeed, maxSpeed)
                    speedState = snapped
                    onSpeedChange(snapped)
                },
                valueRange = 0f..1f,
                interactionSource = sliderInteractionSource,
                colors =
                    SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                    ),
                thumb = {
                    Box(
                        modifier =
                            Modifier.size(width = animatedThumbWidth, height = animatedThumbHeight)
                                .clip(RoundedCornerShape(animatedThumbWidth / 2f))
                                .background(Color.White)
                    )
                },
                track = { sliderState ->
                    val fraction = sliderState.value
                    Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        val trackHeight = animatedTrackHeight.toPx()
                        val centerY = size.height / 2f
                        val centerX = size.width * 0.5f
                        val thumbX = size.width * fraction.coerceIn(0f, 1f)
                        val thumbWidthPx = animatedThumbWidth.toPx()
                        val thumbGapPx = 4.dp.toPx()
                        val halfTrack = trackHeight / 2f
                        val fullCornerRadius = CornerRadius(halfTrack, halfTrack)
                        val smallCornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())

                        val clampedThumbX =
                            thumbX.coerceIn(thumbWidthPx / 2f, size.width - thumbWidthPx / 2f)
                        val thumbLeft = clampedThumbX - thumbWidthPx / 2f
                        val thumbRight = clampedThumbX + thumbWidthPx / 2f
                        val gapStart = (thumbLeft - thumbGapPx).coerceAtLeast(0f)
                        val gapEnd = (thumbRight + thumbGapPx).coerceAtMost(size.width)

                        val inactiveColor = Color.White.copy(alpha = 0.20f)
                        val activeColor = Color.White

                        // Inactive track segments & Active track segment
                        if (thumbX >= centerX) {
                            // 1. Left inactive track (from 0 to minOf(centerX, gapStart))
                            val leftEnd = minOf(centerX, gapStart)
                            if (leftEnd > 0f) {
                                drawCustomRoundRect(
                                    color = inactiveColor,
                                    topLeft = Offset(0f, centerY - halfTrack),
                                    size = Size(leftEnd, trackHeight),
                                    topLeftRadius = fullCornerRadius,
                                    bottomLeftRadius = fullCornerRadius,
                                    topRightRadius = smallCornerRadius,
                                    bottomRightRadius = smallCornerRadius,
                                )
                            }
                            // 2. Active track (from centerX to gapStart, if gapStart > centerX)
                            if (gapStart > centerX) {
                                drawCustomRoundRect(
                                    color = activeColor,
                                    topLeft = Offset(centerX, centerY - halfTrack),
                                    size = Size(gapStart - centerX, trackHeight),
                                    topLeftRadius = smallCornerRadius,
                                    bottomLeftRadius = smallCornerRadius,
                                    topRightRadius = smallCornerRadius,
                                    bottomRightRadius = smallCornerRadius,
                                )
                            }
                            // 3. Right inactive track (from maxOf(centerX, gapEnd) to size.width)
                            val rightStart = maxOf(centerX, gapEnd)
                            if (size.width > rightStart) {
                                drawCustomRoundRect(
                                    color = inactiveColor,
                                    topLeft = Offset(rightStart, centerY - halfTrack),
                                    size = Size(size.width - rightStart, trackHeight),
                                    topLeftRadius = smallCornerRadius,
                                    bottomLeftRadius = smallCornerRadius,
                                    topRightRadius = fullCornerRadius,
                                    bottomRightRadius = fullCornerRadius,
                                )
                            }
                        } else {
                            // thumbX < centerX
                            // 1. Left inactive track (from 0 to minOf(centerX, gapStart))
                            val leftEnd = minOf(centerX, gapStart)
                            if (leftEnd > 0f) {
                                drawCustomRoundRect(
                                    color = inactiveColor,
                                    topLeft = Offset(0f, centerY - halfTrack),
                                    size = Size(leftEnd, trackHeight),
                                    topLeftRadius = fullCornerRadius,
                                    bottomLeftRadius = fullCornerRadius,
                                    topRightRadius = smallCornerRadius,
                                    bottomRightRadius = smallCornerRadius,
                                )
                            }
                            // 2. Active track (from gapEnd to centerX, if gapEnd < centerX)
                            if (gapEnd < centerX) {
                                drawCustomRoundRect(
                                    color = activeColor,
                                    topLeft = Offset(gapEnd, centerY - halfTrack),
                                    size = Size(centerX - gapEnd, trackHeight),
                                    topLeftRadius = smallCornerRadius,
                                    bottomLeftRadius = smallCornerRadius,
                                    topRightRadius = smallCornerRadius,
                                    bottomRightRadius = smallCornerRadius,
                                )
                            }
                            // 3. Right inactive track (from maxOf(centerX, gapEnd) to size.width)
                            val rightStart = maxOf(centerX, gapEnd)
                            if (size.width > rightStart) {
                                drawCustomRoundRect(
                                    color = inactiveColor,
                                    topLeft = Offset(rightStart, centerY - halfTrack),
                                    size = Size(size.width - rightStart, trackHeight),
                                    topLeftRadius = smallCornerRadius,
                                    bottomLeftRadius = smallCornerRadius,
                                    topRightRadius = fullCornerRadius,
                                    bottomRightRadius = fullCornerRadius,
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )

            // Plus Button (+0.05 step)
            Box(
                modifier =
                    Modifier.size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF262626))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, radius = 23.dp),
                            enabled = speedState < 4.0f,
                            onClick = {
                                val next =
                                    (round((speedState + 0.05f) * 100f) / 100f).coerceAtMost(4.0f)
                                speedState = next
                                onSpeedChange(next)
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_plus),
                    contentDescription = "+",
                    modifier = Modifier.size(20.dp),
                    tint = if (speedState < 4.0f) Color.White else Color.White.copy(alpha = 0.3f),
                )
            }
        }

        Spacer(modifier = Modifier.height(if (isLandscape) 12.dp else 20.dp))

        if (isTabletLayout) {
            // Tablet: Vertical List with SelectableTrackItem (like TV)
            val speedOptions = remember {
                listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(speedOptions, key = { it }) { preset ->
                    val isSelected = abs(speedState - preset) < 0.01f
                    val label =
                        if (preset == 1.0f) {
                            "1.0x"
                        } else {
                            "${preset}x"
                        }
                    val chips =
                        if (preset == 1.0f) {
                            listOf(stringResource(id = PlayerR.string.normal_speed))
                        } else {
                            emptyList()
                        }

                    SelectableTrackItem(
                        title = label,
                        chips = chips,
                        isSelected = isSelected,
                        onClick = {
                            speedState = preset
                            onSpeedChange(preset)
                        },
                    )
                }
            }
        } else {
            // Phone: One row of 8 chips in landscape/wide mode, or two rows of 4 chips in portrait
            val allPresets = remember { listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f) }

            val presetRows =
                remember(isWide) {
                    if (isWide) {
                        listOf(allPresets)
                    } else {
                        listOf(
                            listOf(0.25f, 0.5f, 0.75f, 1.0f),
                            listOf(1.25f, 1.5f, 1.75f, 2.0f),
                        )
                    }
                }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                presetRows.forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (isWide) 4.dp else 8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        rowPresets.forEach { preset ->
                            val isSelected = abs(speedState - preset) < 0.01f
                            val label =
                                if (preset % 1f == 0f) "${preset.toInt()}"
                                else preset.toString().replace('.', ',')

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else Color(0xFF262626)
                                            )
                                            .clickable(
                                                interactionSource =
                                                    remember { MutableInteractionSource() },
                                                indication = ripple(bounded = true, radius = 24.dp),
                                                onClick = {
                                                    speedState = preset
                                                    onSpeedChange(preset)
                                                },
                                            )
                                            .padding(
                                                horizontal = 4.dp,
                                                vertical = if (isLandscape) 4.dp else 6.dp,
                                            ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight =
                                            if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color =
                                            if (isSelected) MaterialTheme.colorScheme.onPrimary
                                            else Color.White,
                                        maxLines = 1,
                                    )
                                }

                                if (preset == 1.0f) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = stringResource(id = PlayerR.string.normal_speed),
                                        style = MaterialTheme.typography.labelSmall,
                                        color =
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else Color.White.copy(alpha = 0.7f),
                                        fontWeight =
                                            if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Portrait", showBackground = true, backgroundColor = 0xFF141416)
@Preview(
    name = "Landscape",
    showBackground = true,
    backgroundColor = 0xFF141416,
    widthDp = 800,
    heightDp = 220,
)
@Composable
private fun PlaybackSpeedPickerPreview() {
    FindroidTheme {
        PlaybackSpeedPicker(
            currentSpeed = 1.0f,
            onSpeedChange = {},
        )
    }
}
