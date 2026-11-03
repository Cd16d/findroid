package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.theme.CentralShapes
import dev.jdtech.jellyfin.core.presentation.theme.PlayerCardBackground
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

/** Scaled switch component for player navigation items with fixed 39dp x 24dp layout bounds. */
@Composable
fun PlayerSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(width = 39.dp, height = 24.dp).scale(0.75f),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Switch(
                checked = checked,
                onCheckedChange = null,
            )
        }
    }
}

/** Unified navigation item for player settings menus (Phone & Tablet). */
@Composable
fun PlayerNavigationItem(
    title: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    shape: Shape = CentralShapes.GroupSingle,
    badge: String? = null,
    enabled: Boolean = true,
    showChevron: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.38f
    val backgroundColor = PlayerCardBackground
    val arrangementSpacing = 12.dp
    val iconSize = 20.dp
    val titleStyle = MaterialTheme.typography.bodyMedium
    val onSurface = MaterialTheme.colorScheme.onSurface

    val minHeight = 52.dp
    val verticalPadding = 14.dp

    Surface(
        modifier =
            modifier.fillMaxWidth().heightIn(min = minHeight).graphicsLayer { this.alpha = alpha },
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(1.dp, onSurface.copy(alpha = 0.08f)),
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(arrangementSpacing),
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = onSurface.copy(alpha = 0.85f),
            )
            Text(
                text = title,
                style = titleStyle,
                fontWeight = FontWeight.Bold,
                color = onSurface,
            )
            if (badge != null) {
                Box(
                    modifier =
                        Modifier.clip(CentralShapes.Tiny)
                            .background(onSurface.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurface.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurface.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailingContent != null) {
                trailingContent()
            } else if (showChevron && enabled) {
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_chevron_right),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Preview(
    name = "Phone Navigation Item",
    showBackground = true,
    backgroundColor = 0xFF141416,
    widthDp = 360,
)
@Composable
private fun PhoneNavigationItemPreview() {
    FindroidTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlayerNavigationItem(
                title = stringResource(id = PlayerR.string.audio),
                subtitle = "English (5.1)",
                iconRes = CoreR.drawable.ic_audio,
                onClick = {},
            )
            PlayerNavigationItem(
                title = stringResource(id = PlayerR.string.subtitles),
                subtitle = "English [CC]",
                iconRes = CoreR.drawable.ic_closed_caption,
                onClick = {},
            )
        }
    }
}

@Preview(
    name = "Tablet Navigation Item",
    showBackground = true,
    backgroundColor = 0xFF141416,
    widthDp = 432,
)
@Composable
private fun TabletNavigationItemPreview() {
    FindroidTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlayerNavigationItem(
                title = "Video Quality",
                subtitle = "Auto (1080p)",
                iconRes = PlayerR.drawable.ic_video_quality,
                onClick = {},
            )
            PlayerNavigationItem(
                title = "Audio",
                subtitle = "English - Stereo",
                iconRes = CoreR.drawable.ic_audio,
                onClick = {},
            )
        }
    }
}
