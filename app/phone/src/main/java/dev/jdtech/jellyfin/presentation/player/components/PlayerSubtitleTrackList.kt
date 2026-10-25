package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.jellyfin.sdk.model.api.MediaStreamType

/**
 * Subtitle track selection list for phone bottom sheet.
 *
 * @param viewModel Active [PlayerViewModel].
 * @param tracks Current media [Tracks].
 */
@Composable
internal fun PhoneSubtitleTrackList(viewModel: PlayerViewModel, tracks: Tracks) {
    val context = LocalContext.current
    val currentLocale = LocalConfiguration.current.locales[0]
    val displayExtraInfo = viewModel.displayExtraInfo
    val uiState by viewModel.uiState.collectAsState()

    val subtitleStreams =
        remember(uiState.mediaStreams) {
            uiState.mediaStreams.filter { it.type == MediaStreamType.SUBTITLE }
        }

    val subtitleGroups =
        remember(tracks) { tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported } }

    val selectedTrackIndex = remember(tracks) { subtitleGroups.indexOfFirst { it.isSelected } }

    var optimisticSelectedIndex by remember(tracks) { mutableIntStateOf(selectedTrackIndex) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SelectableTrackItem(
                title = stringResource(id = PlayerR.string.none),
                chips = emptyList(),
                isSelected = optimisticSelectedIndex == -1,
                onClick = {
                    optimisticSelectedIndex = -1
                    viewModel.switchToTrack(C.TRACK_TYPE_TEXT, -1)
                },
            )
        }

        itemsIndexed(items = subtitleGroups) { index, group ->
            val isSelected = optimisticSelectedIndex == index
            val matchedStream = subtitleStreams.getOrNull(index)
            val displayInfo =
                remember(group, currentLocale, displayExtraInfo, matchedStream) {
                    val track =
                        TrackFormatter.mergeWithStream(
                            TrackFormatter.toTrack(group, index + 1),
                            matchedStream,
                        )
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
                    optimisticSelectedIndex = index
                    viewModel.switchToTrack(C.TRACK_TYPE_TEXT, index)
                },
            )
        }
    }
}

/**
 * Subtitle track selection list for tablet side panel.
 *
 * @param viewModel Active [PlayerViewModel].
 * @param tracks Current media [Tracks].
 */
@Composable
internal fun TabletSubtitleTrackList(viewModel: PlayerViewModel, tracks: Tracks) {
    PhoneSubtitleTrackList(viewModel = viewModel, tracks = tracks)
}

@Preview(showBackground = true, backgroundColor = 0xFF1E1E1E)
@Composable
private fun PhoneSubtitleTrackListPreview() {
    FindroidTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectableTrackItem(
                title = stringResource(id = PlayerR.string.none),
                chips = emptyList(),
                isSelected = false,
                onClick = {},
            )
            SelectableTrackItem(
                title = "English [CC]",
                chips = listOf("SDH", "Default"),
                isSelected = true,
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141416, widthDp = 360)
@Composable
private fun TabletSubtitleTrackListPreview() {
    FindroidTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectableTrackItem(
                title = stringResource(id = PlayerR.string.none),
                chips = emptyList(),
                isSelected = false,
                onClick = {},
            )
            SelectableTrackItem(
                title = "English [SDH]",
                chips = listOf("SDH", "Default"),
                isSelected = true,
                onClick = {},
            )
            SelectableTrackItem(
                title = "Full Subtitles",
                chips = listOf("FORCED"),
                isSelected = false,
                onClick = {},
            )
        }
    }
}
