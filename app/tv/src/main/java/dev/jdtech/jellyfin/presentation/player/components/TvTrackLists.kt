package dev.jdtech.jellyfin.presentation.player.components

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.ClickableSurfaceShape
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.theme.PlayerTvSidePanelContainer
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.domain.utils.TrackFormatter
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import org.jellyfin.sdk.model.api.MediaStreamType

/**
 * Audio track selection list for TV player side panel.
 *
 * @param viewModel Active [PlayerViewModel].
 * @param initialFocusRequester [FocusRequester] for setting initial focus on the list.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun AudioTrackList(
    viewModel: PlayerViewModel,
    initialFocusRequester: FocusRequester,
) {
    val context = LocalContext.current
    val currentLocale = LocalConfiguration.current.locales[0]
    val displayExtraInfo = viewModel.displayExtraInfo
    val uiState by viewModel.uiState.collectAsState()
    val audioTracks = uiState.availableAudioTracks

    if (audioTracks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(id = PlayerR.string.none),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            itemsIndexed(
                items = audioTracks,
                key = { _, track -> track.id },
            ) { index, track ->
                val isSelected = track.selected
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
                val isFirst = index == 0

                SelectableItem(
                    title = displayInfo.name,
                    chips = displayInfo.chips,
                    isSelected = isSelected,
                    onClick = { viewModel.selectAudioTrack(track.id) },
                    modifier =
                        if (isFirst) Modifier.focusRequester(initialFocusRequester) else Modifier,
                )
            }
        }
    }
}

/**
 * Subtitle track selection list for TV player side panel.
 *
 * @param viewModel Active [PlayerViewModel].
 * @param tracks Current media [Tracks].
 * @param initialFocusRequester [FocusRequester] for setting initial focus on the list.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun SubtitleTrackList(
    viewModel: PlayerViewModel,
    tracks: Tracks,
    initialFocusRequester: FocusRequester,
) {
    val context = LocalContext.current
    val currentLocale = LocalConfiguration.current.locales[0]
    val displayExtraInfo = viewModel.displayExtraInfo
    val uiState by viewModel.uiState.collectAsState()
    val mediaStreams = uiState.mediaStreams
    val subtitleStreams =
        remember(mediaStreams) { mediaStreams.filter { it.type == MediaStreamType.SUBTITLE } }

    val textGroups =
        remember(tracks) { tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported } }
    val noneSelected = textGroups.none { it.isSelected }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        item(key = "subtitles_none") {
            SelectableItem(
                title = stringResource(id = PlayerR.string.none),
                isSelected = noneSelected,
                onClick = { viewModel.switchToTrack(C.TRACK_TYPE_TEXT, -1) },
                modifier = Modifier.focusRequester(initialFocusRequester),
            )
        }

        itemsIndexed(
            items = textGroups,
            key = { index, group -> group.mediaTrackGroup.id.takeIf { it.isNotBlank() } ?: index },
        ) { index, group ->
            val isSelected = group.isSelected
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

            SelectableItem(
                title = displayInfo.name,
                chips = displayInfo.chips,
                isSelected = isSelected,
                onClick = { viewModel.switchToTrack(C.TRACK_TYPE_TEXT, index) },
            )
        }
    }
}

/**
 * Badge chip component displaying metadata attributes (e.g. "Default", "Stereo", "5.1").
 *
 * @param text Label text displayed inside the chip.
 */
@Composable
internal fun TrackChip(text: String) {
    val contentColor = LocalContentColor.current
    Box(
        modifier =
            Modifier.clip(ChipShape)
                .background(contentColor.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.9f),
            maxLines = 1,
        )
    }
}

/**
 * Focusable selectable list item component for TV side panel lists.
 *
 * @param title Primary item title.
 * @param isSelected Whether this item is currently selected.
 * @param onClick Callback when the item is pressed.
 * @param modifier Optional [Modifier] applied to the item surface.
 * @param chips List of metadata chip labels to display on the right.
 * @param shape Clickable surface shape configuration.
 * @param colors Clickable surface colors configuration.
 */
@Composable
internal fun SelectableItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chips: List<String> = emptyList(),
    shape: ClickableSurfaceShape = ClickableSurfaceDefaults.shape(shape = PanelItemShape),
    colors: ClickableSurfaceColors =
        ClickableSurfaceDefaults.colors(
            containerColor = PlayerTvSidePanelContainer,
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color.Black,
        ),
) {
    Surface(
        onClick = onClick,
        shape = shape,
        colors = colors,
        scale = ClickableSurfaceScale.None,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isSelected) {
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Spacer(modifier = Modifier.size(20.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (chips.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    chips.forEach { chipText -> TrackChip(text = chipText) }
                }
            }
        }
    }
}

@Preview
@Composable
private fun AudioTrackListPreview() {
    FindroidTheme {
        Column(
            modifier = Modifier.width(320.dp).background(Color.Black).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectableItem(
                title = "English",
                chips = listOf("Default", "Stereo", "AAC"),
                isSelected = true,
                onClick = {},
            )
            SelectableItem(
                title = "Japanese",
                chips = listOf("5.1", "FLAC"),
                isSelected = false,
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun SubtitleTrackListPreview() {
    FindroidTheme {
        Column(
            modifier = Modifier.width(320.dp).background(Color.Black).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectableItem(
                title = stringResource(id = PlayerR.string.none),
                isSelected = false,
                onClick = {},
            )
            SelectableItem(
                title = "English [SDH]",
                chips = listOf("Full", "ASS"),
                isSelected = true,
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun TrackChipPreview() {
    FindroidTheme {
        Row(
            modifier = Modifier.background(Color.Black).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TrackChip(text = "Default")
            TrackChip(text = "Stereo")
            TrackChip(text = "5.1")
        }
    }
}
