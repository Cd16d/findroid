package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Tracks
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.domain.models.PlayerSidePanelType
import dev.jdtech.jellyfin.player.core.domain.utils.SleepTimerFormatter
import dev.jdtech.jellyfin.player.core.domain.utils.TrackFormatter
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.settings.domain.Constants.PlayerResizeMode
import org.jellyfin.sdk.model.api.MediaStreamType

/**
 * Main quick settings menu displayed in the phone bottom sheet.
 *
 * @param viewModel Active [PlayerViewModel].
 * @param tracks Current media [Tracks].
 * @param currentResizeMode Video resize mode.
 * @param currentSpeed Current playback speed multiplier.
 * @param isPlaying Whether media is currently actively playing.
 * @param onNavigate Callback to navigate to a sub-menu page.
 * @param onLock Callback to lock the player controls.
 */
@Composable
internal fun PhoneSettingsMenu(
    viewModel: PlayerViewModel,
    tracks: Tracks,
    currentResizeMode: PlayerResizeMode,
    currentSpeed: Float,
    isPlaying: Boolean,
    onNavigate: (PlayerSidePanelType) -> Unit,
    onLock: () -> Unit,
) {
    val context = LocalContext.current
    val currentLocale = LocalConfiguration.current.locales[0]
    val uiState by viewModel.uiState.collectAsState()

    val selectedAudioTrack = uiState.availableAudioTracks.firstOrNull { it.selected }
    val selectedAudioGroup =
        tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
    val audioTitle =
        selectedAudioTrack?.let {
            TrackFormatter.getTrackDisplayInfo(
                    track = it,
                    context = context,
                    displayExtraInfo = viewModel.displayExtraInfo,
                    currentLocale = currentLocale,
                )
                .name
        }
            ?: selectedAudioGroup?.let {
                TrackFormatter.getTrackDisplayInfo(
                        group = it,
                        context = context,
                        displayExtraInfo = viewModel.displayExtraInfo,
                        currentLocale = currentLocale,
                    )
                    .name
            }
            ?: stringResource(id = PlayerR.string.none)

    val selectedSubGroup =
        tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
    val subTitle =
        if (selectedSubGroup != null) {
            val subIndex =
                tracks.groups
                    .filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
                    .indexOf(selectedSubGroup)
            val matchedStream =
                if (subIndex >= 0) {
                    uiState.mediaStreams
                        .filter { it.type == MediaStreamType.SUBTITLE }
                        .getOrNull(subIndex)
                } else null
            val track =
                TrackFormatter.mergeWithStream(
                    TrackFormatter.toTrack(selectedSubGroup, 1),
                    matchedStream,
                )
            TrackFormatter.getTrackDisplayInfo(
                    track = track,
                    context = context,
                    displayExtraInfo = viewModel.displayExtraInfo,
                    currentLocale = currentLocale,
                )
                .name
        } else stringResource(id = PlayerR.string.none)

    val currentSpeedText = "${currentSpeed}×"
    val currentAspectText =
        when (currentResizeMode) {
            PlayerResizeMode.ZOOM -> stringResource(id = PlayerR.string.aspect_ratio_zoom)
            PlayerResizeMode.FILL -> stringResource(id = PlayerR.string.aspect_ratio_fill)
            else -> stringResource(id = PlayerR.string.aspect_ratio_fit)
        }

    val isMovie = uiState.seriesId == null
    val sleepTimerSubtitle =
        SleepTimerFormatter.formatSleepTimerSubtitle(
            context = context,
            sleepTimerStatus = uiState.sleepTimerStatus,
            isMovie = isMovie,
        )

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.video_quality),
                subtitle = uiState.selectedQuality.label,
                iconRes = PlayerR.drawable.ic_video_quality,
                onClick = { onNavigate(PlayerSidePanelType.QUALITY) },
            )
        }
        item {
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.audio),
                subtitle = audioTitle,
                iconRes = CoreR.drawable.ic_audio,
                onClick = { onNavigate(PlayerSidePanelType.AUDIO) },
            )
        }
        item {
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.subtitles),
                subtitle = subTitle,
                iconRes = CoreR.drawable.ic_closed_caption,
                onClick = { onNavigate(PlayerSidePanelType.SUBTITLES) },
            )
        }
        item {
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.playback_speed),
                subtitle = currentSpeedText,
                iconRes = PlayerR.drawable.ic_playback_speed,
                onClick = { onNavigate(PlayerSidePanelType.SPEED) },
            )
        }
        item {
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.aspect_ratio),
                subtitle = currentAspectText,
                iconRes = PlayerR.drawable.ic_aspect_ratio,
                onClick = { onNavigate(PlayerSidePanelType.ASPECT_RATIO) },
            )
        }
        item {
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.sleep_timer),
                subtitle = sleepTimerSubtitle,
                iconRes = PlayerR.drawable.ic_sleep_timer,
                onClick = { onNavigate(PlayerSidePanelType.SLEEP_TIMER) },
            )
        }
        item {
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.lock_screen),
                subtitle = "",
                iconRes = CoreR.drawable.ic_lock,
                showChevron = false,
                enabled = isPlaying,
                onClick = onLock,
            )
        }
        if (viewModel.displayExtraInfo) {
            item {
                PhoneNavigationRow(
                    title = stringResource(id = PlayerR.string.playback_info),
                    subtitle = "",
                    iconRes = CoreR.drawable.ic_chart_line,
                    showChevron = true,
                    onClick = { onNavigate(PlayerSidePanelType.PLAYBACK_INFO) },
                )
            }
        }
    }
}

/**
 * Main quick settings menu displayed in the tablet side panel.
 *
 * @param viewModel Active [PlayerViewModel].
 * @param tracks Current media [Tracks].
 * @param currentSpeed Current playback speed.
 * @param currentResizeMode Video resize mode.
 * @param isPlaying Whether media is currently actively playing.
 * @param onNavigate Callback to navigate to a sub-page.
 * @param onLock Callback to lock the player controls.
 */
@Composable
internal fun TabletSettingsMenu(
    viewModel: PlayerViewModel,
    tracks: Tracks,
    currentSpeed: Float,
    currentResizeMode: PlayerResizeMode,
    isPlaying: Boolean,
    onNavigate: (PlayerSidePanelType) -> Unit,
    onLock: () -> Unit,
) {
    val context = LocalContext.current
    val currentLocale = LocalConfiguration.current.locales[0]
    val uiState by viewModel.uiState.collectAsState()

    val selectedAudioTrack = uiState.availableAudioTracks.firstOrNull { it.selected }
    val selectedAudioGroup =
        tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
    val audioTitle =
        selectedAudioTrack?.let {
            TrackFormatter.getTrackDisplayInfo(
                    track = it,
                    context = context,
                    displayExtraInfo = viewModel.displayExtraInfo,
                    currentLocale = currentLocale,
                )
                .name
        }
            ?: selectedAudioGroup?.let {
                TrackFormatter.getTrackDisplayInfo(
                        group = it,
                        context = context,
                        displayExtraInfo = viewModel.displayExtraInfo,
                        currentLocale = currentLocale,
                    )
                    .name
            }
            ?: stringResource(id = PlayerR.string.none)

    val selectedSubGroup =
        tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
    val subTitle =
        if (selectedSubGroup != null) {
            TrackFormatter.getTrackDisplayInfo(
                    group = selectedSubGroup,
                    context = context,
                    displayExtraInfo = viewModel.displayExtraInfo,
                    currentLocale = currentLocale,
                )
                .name
        } else stringResource(id = PlayerR.string.none)

    val currentSpeedText = "${currentSpeed}×"
    val currentAspectText =
        when (currentResizeMode) {
            PlayerResizeMode.ZOOM -> stringResource(id = PlayerR.string.aspect_ratio_zoom)
            PlayerResizeMode.FILL -> stringResource(id = PlayerR.string.aspect_ratio_fill)
            else -> stringResource(id = PlayerR.string.aspect_ratio_fit)
        }

    val isMovie = uiState.seriesId == null
    val sleepTimerSubtitle =
        SleepTimerFormatter.formatSleepTimerSubtitle(
            context = context,
            sleepTimerStatus = uiState.sleepTimerStatus,
            isMovie = isMovie,
        )

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            TabletNavigationItem(
                title = stringResource(id = PlayerR.string.video_quality),
                subtitle = uiState.selectedQuality.label,
                iconRes = PlayerR.drawable.ic_video_quality,
                onClick = { onNavigate(PlayerSidePanelType.QUALITY) },
            )
        }
        item {
            TabletNavigationItem(
                title = stringResource(id = PlayerR.string.audio),
                subtitle = audioTitle,
                iconRes = CoreR.drawable.ic_audio,
                onClick = { onNavigate(PlayerSidePanelType.AUDIO) },
            )
        }
        item {
            TabletNavigationItem(
                title = stringResource(id = PlayerR.string.subtitles),
                subtitle = subTitle,
                iconRes = CoreR.drawable.ic_closed_caption,
                onClick = { onNavigate(PlayerSidePanelType.SUBTITLES) },
            )
        }
        item {
            TabletNavigationItem(
                title = stringResource(id = PlayerR.string.playback_speed),
                subtitle = currentSpeedText,
                iconRes = PlayerR.drawable.ic_playback_speed,
                onClick = { onNavigate(PlayerSidePanelType.SPEED) },
            )
        }
        item {
            TabletNavigationItem(
                title = stringResource(id = PlayerR.string.aspect_ratio),
                subtitle = currentAspectText,
                iconRes = PlayerR.drawable.ic_aspect_ratio,
                onClick = { onNavigate(PlayerSidePanelType.ASPECT_RATIO) },
            )
        }
        item {
            TabletNavigationItem(
                title = stringResource(id = PlayerR.string.sleep_timer),
                subtitle = sleepTimerSubtitle,
                iconRes = PlayerR.drawable.ic_sleep_timer,
                onClick = { onNavigate(PlayerSidePanelType.SLEEP_TIMER) },
            )
        }
        item {
            TabletNavigationItem(
                title = stringResource(id = PlayerR.string.lock_screen),
                subtitle = "",
                iconRes = CoreR.drawable.ic_lock,
                showChevron = false,
                enabled = isPlaying,
                onClick = onLock,
            )
        }
        if (viewModel.displayExtraInfo) {
            item {
                TabletNavigationItem(
                    title = stringResource(id = PlayerR.string.playback_info),
                    subtitle = "",
                    iconRes = CoreR.drawable.ic_chart_line,
                    showChevron = true,
                    onClick = { onNavigate(PlayerSidePanelType.PLAYBACK_INFO) },
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141416, widthDp = 360)
@Composable
private fun TabletSettingsMenuPreview() {
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
                subtitle = "English - 5.1",
                iconRes = CoreR.drawable.ic_audio,
                onClick = {},
            )
            TabletNavigationItem(
                title = "Subtitles",
                subtitle = "English [CC]",
                iconRes = CoreR.drawable.ic_closed_caption,
                onClick = {},
            )
            TabletNavigationItem(
                title = "Playback Speed",
                subtitle = "1.0×",
                iconRes = PlayerR.drawable.ic_playback_speed,
                onClick = {},
            )
            TabletNavigationItem(
                title = "Aspect Ratio",
                subtitle = "Fit",
                iconRes = PlayerR.drawable.ic_aspect_ratio,
                onClick = {},
            )
            TabletNavigationItem(
                title = "Sleep Timer",
                subtitle = "Off",
                iconRes = PlayerR.drawable.ic_sleep_timer,
                onClick = {},
            )
        }
    }
}
