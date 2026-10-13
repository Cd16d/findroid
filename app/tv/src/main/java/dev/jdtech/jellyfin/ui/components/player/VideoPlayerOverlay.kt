package dev.jdtech.jellyfin.ui.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

@Composable
fun VideoPlayerOverlay(
    isPlaying: Boolean,
    isScrubbing: Boolean = false,
    isShelfFocused: Boolean = false,
    modifier: Modifier = Modifier,
    state: VideoPlayerState = rememberVideoPlayerState(),
    focusRequester: FocusRequester = remember { FocusRequester() },
    controls: @Composable () -> Unit = {},
) {
    LaunchedEffect(state.controlsVisible) {
        if (state.controlsVisible) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            state.showControls(seconds = Int.MAX_VALUE)
        } else {
            state.showControls()
        }
    }

    AnimatedVisibility(
        visible = state.controlsVisible || isScrubbing,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier =
                modifier.fillMaxSize().onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key != Key.Back) {
                        if (isPlaying && !isScrubbing) {
                            state.showControls()
                        }
                    }
                    false
                }
        ) {
            Spacer(
                modifier =
                    Modifier.fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.5f),
                                0.07f to Color.Transparent,
                                0.72f to Color.Transparent,
                                0.88f to Color.Black.copy(alpha = 0.55f),
                                1.0f to Color.Black.copy(alpha = 0.80f),
                            )
                        )
            )

            controls()
        }
    }
}

@Preview(device = "id:tv_4k")
@Composable
private fun VideoPlayerOverlayPreview() {
    FindroidTheme {
        Box(Modifier.fillMaxSize()) {
            VideoPlayerOverlay(
                modifier = Modifier.align(Alignment.BottomCenter),
                isPlaying = true,
                controls = { Box(Modifier.fillMaxWidth().height(120.dp).background(Color.Blue)) },
            )
        }
    }
}
