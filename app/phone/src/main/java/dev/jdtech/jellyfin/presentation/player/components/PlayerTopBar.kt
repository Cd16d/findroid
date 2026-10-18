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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR

private val GlassPillShape = RoundedCornerShape(24.dp)

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
    isPortrait: Boolean = false,
) {
    val buttonSize = if (isPortrait) 36.dp else 40.dp
    val iconSize = if (isPortrait) 20.dp else 22.dp
    val backButtonSize = if (isPortrait) 40.dp else 44.dp
    val backIconSize = if (isPortrait) 22.dp else 24.dp
    val spacing = if (isPortrait) 8.dp else 12.dp

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Left: Back button + Title & Subtitle
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            Box(
                modifier =
                    Modifier.size(backButtonSize)
                        .clip(CircleShape)
                        .background(Color(0xFF262626).copy(alpha = 0.6f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, radius = backButtonSize / 2),
                            onClick = onBack,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_arrow_left),
                    contentDescription = null,
                    modifier = Modifier.size(backIconSize),
                    tint = Color.White,
                )
            }

            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Right Actions: Unified translucent glass pill containing:
        // Subtitles, Audio, Screen Lock, PiP (if supported), Settings
        Row(
            modifier =
                Modifier.clip(GlassPillShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .border(
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        GlassPillShape,
                    )
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
                                    CoreR.drawable.ic_screen_rotation_lock
                                } else {
                                    CoreR.drawable.ic_screen_rotation
                                }
                        ),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint =
                        if (isOrientationLocked) MaterialTheme.colorScheme.primary else Color.White,
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
                    painter = painterResource(id = CoreR.drawable.ic_speaker),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = Color.White,
                )
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
}
