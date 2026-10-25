package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.theme.PlayerOverlayBackground
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.domain.models.SleepTimerStatus
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

private val GlassPillShape = CircleShape
private val GlassBackgroundColor = PlayerOverlayBackground.copy(alpha = 0.6f)
private val GlassBorderStroke = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))

/**
 * Top control bar for the player screen containing title, subtitle, back navigation, and
 * quick-action icons (orientation lock, PiP, subtitles, audio, settings).
 *
 * @param title Primary title of the media item (e.g. episode or movie name).
 * @param subtitle Optional secondary title (e.g. series or season info).
 * @param onBack Callback triggered when tapping the back arrow.
 * @param onOpenSubtitles Callback to open the subtitles selection sheet/panel.
 * @param onOpenAudio Callback to open the audio track selection sheet/panel.
 * @param onEnterPip Callback to request entering Picture-in-Picture mode.
 * @param onOpenSettings Callback to open the player quick settings menu.
 * @param onToggleOrientation Callback to toggle or lock orientation.
 * @param modifier Optional [Modifier] applied to the top bar row.
 * @param isOrientationLocked Whether screen rotation is currently locked.
 * @param isPipSupported Whether picture-in-picture is supported on the current device.
 * @param isCompactWidth Whether the layout width is compact (< 600dp).
 * @param sleepTimerStatus Current status of the sleep timer.
 * @param onOpenSleepTimer Callback to open the sleep timer sheet/panel.
 */
@Composable
fun PlayerTopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onOpenAudio: () -> Unit,
    onEnterPip: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleOrientation: () -> Unit,
    modifier: Modifier = Modifier,
    isOrientationLocked: Boolean = false,
    isPipSupported: Boolean = true,
    isCompactWidth: Boolean = false,
    sleepTimerStatus: SleepTimerStatus = SleepTimerStatus(),
    onOpenSleepTimer: (() -> Unit)? = null,
) {
    val buttonSize = 44.dp
    val iconSize = 24.dp
    val spacing = if (isCompactWidth) 8.dp else 12.dp

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left: Back button + Title & Subtitle (inline on non-compact width)
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                Box(
                    modifier =
                        Modifier.size(buttonSize)
                            .clip(CircleShape)
                            .background(GlassBackgroundColor)
                            .border(GlassBorderStroke, CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, radius = buttonSize / 2),
                                onClick = onBack,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = CoreR.drawable.ic_arrow_left),
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = Color.White,
                    )
                }

                if (!isCompactWidth) {
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // Right Actions: Unified translucent glass pill containing:
            // Subtitles, Audio, Screen Lock, PiP (if supported), Settings
            Row(
                modifier =
                    Modifier.clip(GlassPillShape)
                        .background(GlassBackgroundColor)
                        .border(GlassBorderStroke, GlassPillShape)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Screen Orientation Lock / Unlock Button
                Box(
                    modifier =
                        Modifier.size(buttonSize)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, radius = buttonSize / 2),
                                onClick = onToggleOrientation,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter =
                            painterResource(
                                id =
                                    if (isOrientationLocked) {
                                        CoreR.drawable.ic_rotate_off
                                    } else {
                                        CoreR.drawable.ic_rotate
                                    }
                            ),
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = Color.White,
                    )
                }

                // PiP Button (if supported)
                if (isPipSupported) {
                    Box(
                        modifier =
                            Modifier.size(buttonSize)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true, radius = buttonSize / 2),
                                    onClick = onEnterPip,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = CoreR.drawable.ic_picture_in_picture),
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = Color.White,
                        )
                    }
                }

                // Subtitles & Audio Buttons (hidden on compact width to avoid crowding)
                if (!isCompactWidth) {
                    // Subtitles Button
                    Box(
                        modifier =
                            Modifier.size(buttonSize)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true, radius = buttonSize / 2),
                                    onClick = onOpenSubtitles,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = CoreR.drawable.ic_closed_caption),
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = Color.White,
                        )
                    }

                    // Audio Button
                    Box(
                        modifier =
                            Modifier.size(buttonSize)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true, radius = buttonSize / 2),
                                    onClick = onOpenAudio,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = CoreR.drawable.ic_audio),
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = Color.White,
                        )
                    }
                }

                // Sleep Timer Active Chip
                if (sleepTimerStatus.isActive) {
                    val remainingMins =
                        maxOf(1, ((sleepTimerStatus.remainingSeconds + 59L) / 60L).toInt())
                    Box(
                        modifier =
                            Modifier.height(buttonSize)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true, radius = buttonSize / 2),
                                    onClick = { onOpenSleepTimer?.invoke() ?: onOpenSettings() },
                                )
                                .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                painter = painterResource(id = PlayerR.drawable.ic_sleep_timer),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "${remainingMins}m",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                // Settings Button
                Box(
                    modifier =
                        Modifier.size(buttonSize)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, radius = buttonSize / 2),
                                onClick = onOpenSettings,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = CoreR.drawable.ic_settings),
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = Color.White,
                    )
                }
            }
        }

        if (isCompactWidth) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PlayerTopBarLandscapePreview() {
    FindroidTheme {
        PlayerTopBar(
            title = "S01E01 - Pilot",
            subtitle = "Breaking Bad",
            onBack = {},
            onOpenSubtitles = {},
            onOpenAudio = {},
            onEnterPip = {},
            onOpenSettings = {},
            onToggleOrientation = {},
            isCompactWidth = false,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PlayerTopBarPortraitPreview() {
    FindroidTheme {
        PlayerTopBar(
            title = "Inception (2010)",
            subtitle = "test subtitle",
            onBack = {},
            onOpenSubtitles = {},
            onOpenAudio = {},
            onEnterPip = {},
            onOpenSettings = {},
            onToggleOrientation = {},
            isOrientationLocked = true,
            isCompactWidth = true,
        )
    }
}
