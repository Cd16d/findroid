package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.components.ClockText
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun VideoPlayerControlsLayout(
    mediaTitle: @Composable () -> Unit,
    seeker: @Composable () -> Unit,
    mediaActions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    episodesShelf: (@Composable () -> Unit)? = null,
    shelfOffset: Dp = 0.dp,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // TOP: Movie / Media title on Top-Left, Current Time on Top-Right
        Row(
            modifier =
                Modifier.align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f, fill = false)) { mediaTitle() }
            ClockText(
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }

        // BOTTOM: Seeker, Media Actions, Episodes Shelf
        Column(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .offset(y = shelfOffset)
                    .padding(bottom = 20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                seeker()
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                mediaActions()
            }
            if (episodesShelf != null) {
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
                episodesShelf()
            }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun VideoPlayerControlsLayoutPreview() {
    FindroidTheme {
        VideoPlayerControlsLayout(
            mediaTitle = {
                Text(text = "Sample Movie (2024)", style = MaterialTheme.typography.titleLarge)
            },
            seeker = {
                VideoPlayerSeekBar(
                    progress = 0.35f,
                    onSeek = {},
                    state = rememberVideoPlayerState(),
                )
            },
            mediaActions = {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    VideoPlayerMediaButton(icon = painterResource(id = CoreR.drawable.ic_play))
                }
            },
        )
    }
}
