package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR

@Composable
fun PlayerCenterControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPause: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onPreviousItem: (() -> Unit)? = null,
    onNextItem: (() -> Unit)? = null,
    hasPreviousItem: Boolean = false,
    hasNextItem: Boolean = false,
    isPortrait: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isPortrait) 16.dp else 28.dp),
    ) {
        // Previous Item (if applicable)
        if (onPreviousItem != null) {
            Box(
                modifier =
                    Modifier.size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Color(0xFF262626).copy(alpha = if (hasPreviousItem) 0.6f else 0.25f)
                        )
                        .then(
                            if (hasPreviousItem) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true, radius = 24.dp),
                                    onClick = onPreviousItem,
                                )
                            } else {
                                Modifier
                            }
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_skip_back),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White.copy(alpha = if (hasPreviousItem) 1f else 0.38f),
                )
            }
        }

        // Rewind 10s
        Box(
            modifier =
                Modifier.size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF262626).copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, radius = 26.dp),
                        onClick = { onSeekRelative(-10_000L) },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = CoreR.drawable.ic_rewind),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = Color.White,
            )
        }

        // Center Play / Pause or Buffering
        Box(
            modifier =
                Modifier.size(68.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, radius = 34.dp),
                        onClick = onPlayPause,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (isBuffering) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp),
                )
            } else {
                Icon(
                    painter =
                        painterResource(
                            id = if (isPlaying) CoreR.drawable.ic_pause else CoreR.drawable.ic_play
                        ),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        // Fast Forward 10s
        Box(
            modifier =
                Modifier.size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF262626).copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, radius = 26.dp),
                        onClick = { onSeekRelative(10_000L) },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = CoreR.drawable.ic_fast_forward),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = Color.White,
            )
        }

        // Next Item (if applicable)
        if (onNextItem != null) {
            Box(
                modifier =
                    Modifier.size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Color(0xFF262626).copy(alpha = if (hasNextItem) 0.6f else 0.25f)
                        )
                        .then(
                            if (hasNextItem) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true, radius = 24.dp),
                                    onClick = onNextItem,
                                )
                            } else {
                                Modifier
                            }
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_skip_forward),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White.copy(alpha = if (hasNextItem) 1f else 0.38f),
                )
            }
        }
    }
}
