package dev.jdtech.jellyfin.ui.components.player

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

@Composable
fun VideoPlayerMediaButton(
    icon: Painter,
    state: VideoPlayerState,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    isPrimaryAction: Boolean = false,
    onClick: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused && isPlaying) {
        if (isFocused && isPlaying) {
            state.showControls()
        }
    }

    IconButton(
        onClick = onClick,
        modifier = modifier,
        interactionSource = interactionSource,
        scale = IconButtonDefaults.scale(focusedScale = 1.15f),
        colors =
            if (isPrimaryAction) {
                IconButtonDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = MaterialTheme.colorScheme.primary,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    focusedContentColor = androidx.compose.ui.graphics.Color.Black,
                )
            } else {
                IconButtonDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f),
                    focusedContainerColor =
                        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.30f),
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    focusedContentColor = MaterialTheme.colorScheme.primary,
                )
            },
    ) {
        Icon(painter = icon, contentDescription = null)
    }
}

@Preview
@Composable
private fun VideoPlayerMediaButtonPreview() {
    FindroidTheme {
        VideoPlayerMediaButton(
            icon = painterResource(id = CoreR.drawable.ic_play),
            state = rememberVideoPlayerState(),
            isPlaying = false,
        )
    }
}
