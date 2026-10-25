package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/** Unified navigation item for player settings menus (Phone & Tablet). */
@Composable
fun PlayerNavigationItem(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit,
    shape: Shape = CentralShapes.GroupSingle,
    badge: String? = null,
    enabled: Boolean = true,
    showChevron: Boolean = true,
    isTablet: Boolean = false,
) {
    val alpha = if (enabled) 1f else if (isTablet) 0.38f else 0.4f
    val backgroundColor = PlayerCardBackground
    val arrangementSpacing = if (isTablet) 12.dp else 10.dp
    val iconSize = if (isTablet) 22.dp else 20.dp
    val titleStyle =
        if (isTablet) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
    val onSurface = MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha },
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(1.dp, onSurface.copy(alpha = 0.08f)),
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
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
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurface.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showChevron && enabled) {
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

@Composable
internal fun PhoneNavigationRow(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit,
    shape: Shape = CentralShapes.GroupSingle,
    enabled: Boolean = true,
    showChevron: Boolean = true,
) {
    PlayerNavigationItem(
        title = title,
        subtitle = subtitle,
        iconRes = iconRes,
        onClick = onClick,
        shape = shape,
        enabled = enabled,
        showChevron = showChevron,
        isTablet = false,
    )
}

@Composable
internal fun TabletNavigationItem(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit,
    shape: Shape = CentralShapes.GroupSingle,
    enabled: Boolean = true,
    showChevron: Boolean = true,
) {
    PlayerNavigationItem(
        title = title,
        subtitle = subtitle,
        iconRes = iconRes,
        onClick = onClick,
        shape = shape,
        enabled = enabled,
        showChevron = showChevron,
        isTablet = true,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF1E1E1E)
@Composable
private fun PhoneNavigationRowPreview() {
    FindroidTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.audio),
                subtitle = "English (5.1)",
                iconRes = CoreR.drawable.ic_audio,
                onClick = {},
            )
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.subtitles),
                subtitle = "English [CC]",
                iconRes = CoreR.drawable.ic_closed_caption,
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141416)
@Composable
private fun TabletNavigationItemPreview() {
    FindroidTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TabletNavigationItem(
                title = "Video Quality",
                subtitle = "Auto (1080p)",
                iconRes = PlayerR.drawable.ic_video_quality,
                onClick = {},
            )
            TabletNavigationItem(
                title = "Audio",
                subtitle = "English - Stereo",
                iconRes = CoreR.drawable.ic_audio,
                onClick = {},
            )
        }
    }
}
