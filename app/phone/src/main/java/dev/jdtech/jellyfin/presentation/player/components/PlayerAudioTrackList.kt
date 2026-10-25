package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Tracks
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.domain.utils.TrackFormatter
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.components.SelectableTrackItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

/**
 * Audio track selection list for phone bottom sheet.
 *
 * @param viewModel Active [PlayerViewModel].
 * @param tracks Current media [Tracks].
 */
@Composable
internal fun PhoneAudioTrackList(viewModel: PlayerViewModel, tracks: Tracks) {
    val context = LocalContext.current
    val currentLocale = LocalConfiguration.current.locales[0]
    val displayExtraInfo = viewModel.displayExtraInfo
    val uiState by viewModel.uiState.collectAsState()

    val audioTracks = uiState.availableAudioTracks
    var optimisticSelectedId by
        remember(audioTracks) { mutableStateOf(audioTracks.firstOrNull { it.selected }?.id) }

    if (audioTracks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(id = PlayerR.string.none),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(items = audioTracks, key = { _, track -> track.id }) { index, track ->
                val isSelected = optimisticSelectedId == track.id
                val displayInfo =
                    remember(track, currentLocale, displayExtraInfo) {
                        TrackFormatter.getTrackDisplayInfo(
                            track = track,
                            context = context,
                            displayExtraInfo = displayExtraInfo,
                            currentLocale = currentLocale,
                            fallbackIndex = index + 1,
                        )
                    }

                SelectableTrackItem(
                    title = displayInfo.name,
                    chips = displayInfo.chips,
                    isSelected = isSelected,
                    onClick = {
                        optimisticSelectedId = track.id
                        viewModel.selectAudioTrack(track.id)
                    },
                )
            }
        }
    }
}

/**
 * Audio track selection list for tablet side panel.
 *
 * @param viewModel Active [PlayerViewModel].
 * @param tracks Current media [Tracks].
 */
@Composable
internal fun TabletAudioTrackList(viewModel: PlayerViewModel, tracks: Tracks) {
    val context = LocalContext.current
    val currentLocale = LocalConfiguration.current.locales[0]
    val displayExtraInfo = viewModel.displayExtraInfo
    val uiState by viewModel.uiState.collectAsState()
    val availableTracks = uiState.availableAudioTracks

    if (availableTracks.isNotEmpty()) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(items = availableTracks) { _, track ->
                val displayInfo =
                    remember(track, currentLocale, displayExtraInfo) {
                        TrackFormatter.getTrackDisplayInfo(
                            track = track,
                            context = context,
                            displayExtraInfo = displayExtraInfo,
                            currentLocale = currentLocale,
                        )
                    }

                SelectableTrackItem(
                    title = displayInfo.name,
                    chips = displayInfo.chips,
                    isSelected = track.selected,
                    onClick = { viewModel.selectAudioTrack(track.id) },
                )
            }
        }
    } else {
        val audioGroups =
            remember(tracks) {
                tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
            }

        val selectedTrackIndex =
            remember(tracks) { audioGroups.indexOfFirst { it.isSelected }.takeIf { it >= 0 } }

        var optimisticSelectedIndex by remember(tracks) { mutableStateOf(selectedTrackIndex) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(items = audioGroups) { index, group ->
                val isSelected = optimisticSelectedIndex == index
                val displayInfo =
                    remember(group, currentLocale, displayExtraInfo) {
                        TrackFormatter.getTrackDisplayInfo(
                            group = group,
                            context = context,
                            displayExtraInfo = displayExtraInfo,
                            currentLocale = currentLocale,
                            fallbackIndex = index + 1,
                        )
                    }

                SelectableTrackItem(
                    title = displayInfo.name,
                    chips = displayInfo.chips,
                    isSelected = isSelected,
                    onClick = {
                        optimisticSelectedIndex = index
                        viewModel.switchToTrack(C.TRACK_TYPE_AUDIO, index)
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1E1E1E)
@Composable
private fun PhoneAudioTrackListPreview() {
    FindroidTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectableTrackItem(
                title = "English - AAC Stereo",
                chips = listOf("Default"),
                isSelected = true,
                onClick = {},
            )
            SelectableTrackItem(
                title = "Spanish - AC3 5.1",
                chips = emptyList(),
                isSelected = false,
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141416, widthDp = 360)
@Composable
private fun TabletAudioTrackListPreview() {
    FindroidTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectableTrackItem(
                title = "English - TrueHD 7.1",
                chips = listOf("Default", "7.1"),
                isSelected = true,
                onClick = {},
            )
            SelectableTrackItem(
                title = "Japanese - AC3 5.1",
                chips = listOf("5.1"),
                isSelected = false,
                onClick = {},
            )
        }
    }
}
