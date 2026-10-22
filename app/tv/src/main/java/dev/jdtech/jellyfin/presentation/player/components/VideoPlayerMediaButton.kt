package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

/**
 * TV-styled media control icon button with focus scaling, border highlights, and primary action
 * accent colors.
 */
@Composable
fun VideoPlayerMediaButton(
    icon: Painter,
    modifier: Modifier = Modifier,
    isPrimaryAction: Boolean = false,
    onClick: () -> Unit = {},
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
        scale = IconButtonDefaults.scale(focusedScale = 1.15f),
        colors =
            if (isPrimaryAction) {
                IconButtonDefaults.colors(
                    containerColor = Color(0xFF262626).copy(alpha = 0.70f),
                    focusedContainerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    focusedContentColor = Color.Black,
                )
            } else {
                IconButtonDefaults.colors(
                    containerColor = Color(0xFF262626).copy(alpha = 0.70f),
                    focusedContainerColor = Color.White,
                    contentColor = Color.White,
                    focusedContentColor = Color.Black,
                )
            },
    ) {
        Icon(painter = icon, contentDescription = null)
    }
}

@Preview
@Composable
private fun VideoPlayerMediaButtonPreview() {
    FindroidTheme { VideoPlayerMediaButton(icon = painterResource(id = CoreR.drawable.ic_play)) }
}
