package dev.jdtech.jellyfin.presentation.cast.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.player.cast.models.CastPlaybackStatus
import dev.jdtech.jellyfin.player.cast.presentation.CastPlayerViewModel

/**
 * Bottom control section of expanded Google Cast player.
 *
 * @param uiState Cast player UI state model.
 * @param isScrubbing Whether seeker bar is being dragged.
 * @param scrubPosition Current scrub drag position in ms.
 * @param onScrubStart Callback when scrub drag starts.
 * @param onScrubStop Callback when scrub drag stops.
 * @param onPlay Callback to resume playback.
 * @param onPause Callback to pause playback.
 * @param onPlayPreviousItem Callback to play previous item.
 * @param onPlayNextItem Callback to play next item.
 * @param onSkipSegment Callback to skip segment.
 * @param onVolumeChange Callback when volume slider moves.
 * @param onClickAudio Callback when audio track button is tapped.
 * @param onClickSubtitle Callback when subtitle track button is tapped.
 * @param modifier Modifier applied to column layout. Defaults to [Modifier].
 * @param onOpenChapters Optional callback to open chapter selector. Defaults to `null`.
 */
@Composable
fun PlayerBottomSection(
    uiState: CastPlayerViewModel.UiState,
    isScrubbing: Boolean,
    scrubPosition: Float,
    onScrubStart: (Float) -> Unit,
    onScrubStop: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onPlayPreviousItem: () -> Unit,
    onPlayNextItem: () -> Unit,
    onSkipSegment: (FindroidSegment) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onClickAudio: () -> Unit,
    onClickSubtitle: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenChapters: (() -> Unit)? = null,
) {
    val playerState = uiState.playerState
    val segment = uiState.currentSegment
    val chapters = uiState.currentChapters

    val currentProgress = if (isScrubbing) scrubPosition else playerState.currentPosition.toFloat()
    val duration = playerState.duration.toFloat()
    val volume = playerState.volume

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CastPlayerTitles(uiState = uiState)

        Spacer(modifier = Modifier.height(24.dp))

        CastScrubbingTimeline(
            playerStatus = playerState.status,
            currentProgress = currentProgress,
            duration = duration,
            chapters = chapters,
            onScrubStart = onScrubStart,
            onScrubStop = onScrubStop,
            segments = if (segment != null) listOf(segment) else emptyList(),
            onOpenChapters = onOpenChapters,
        )

        Spacer(modifier = Modifier.height(16.dp))

        val isPlaying =
            remember(playerState.status) { playerState.status == CastPlaybackStatus.PLAYING }
        PlaybackButtons(
            uiState = uiState,
            isPlaying = isPlaying,
            onPlay = onPlay,
            onPause = onPause,
            onSeekBack = onPlayPreviousItem,
            onSeekForward = onPlayNextItem,
            onSkipSegment = { if (segment != null) onSkipSegment(segment) },
        )

        Spacer(modifier = Modifier.height(24.dp))

        CastTrackVolumeControls(
            uiState = uiState,
            volume = volume,
            onVolumeChange = onVolumeChange,
            onClickAudio = onClickAudio,
            onClickSubtitle = onClickSubtitle,
        )
    }
}

@Composable
private fun CastPlayerTitles(uiState: CastPlayerViewModel.UiState) {
    val titleInfo = uiState.currentItemTitle

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (titleInfo.seriesName != null) {
            Text(
                text = titleInfo.seriesName!!,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = titleInfo.episodeInfo ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = titleInfo.title,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = titleInfo.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun PlayerBottomSectionPreview() {
    dev.jdtech.jellyfin.presentation.theme.FindroidTheme {
        PlayerBottomSection(
            uiState = CastPlayerViewModel.UiState(),
            isScrubbing = false,
            scrubPosition = 0f,
            onScrubStart = {},
            onScrubStop = {},
            onPlay = {},
            onPause = {},
            onPlayPreviousItem = {},
            onPlayNextItem = {},
            onSkipSegment = {},
            onVolumeChange = {},
            onClickAudio = {},
            onClickSubtitle = {},
        )
    }
}
