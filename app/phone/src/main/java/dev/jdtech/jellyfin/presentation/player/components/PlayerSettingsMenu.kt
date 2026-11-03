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
 * Main quick settings menu displayed in the phone bottom sheet or tablet side panel.
 *
 * @param viewModel Active [PlayerViewModel].
 * @param tracks Current media [Tracks].
 * @param currentResizeMode Video resize mode.
 * @param currentSpeed Current playback speed multiplier.
 * @param isPlaying Whether media is currently actively playing.
 * @param isAmbientModeEnabled Whether ambient lighting mode is enabled.
 * @param onAmbientModeToggle Callback to toggle ambient mode.
 * @param onNavigate Callback to navigate to a sub-menu page.
 * @param onLock Callback to lock the player controls.
 * @param isTablet Whether rendering for tablet side panel layout bounds.
 */
@Composable
internal fun PlayerSettingsMenu(
    viewModel: PlayerViewModel,
    tracks: Tracks,
    currentResizeMode: PlayerResizeMode,
    currentSpeed: Float,
    isPlaying: Boolean,
    isAmbientModeEnabled: Boolean,
    onAmbientModeToggle: (Boolean) -> Unit,
    onNavigate: (PlayerSidePanelType) -> Unit,
    onLock: () -> Unit,
    isTablet: Boolean = false,
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
            PlayerNavigationItem(
                title = stringResource(id = PlayerR.string.video_quality),
                subtitle = uiState.selectedQuality.label,
                iconRes = PlayerR.drawable.ic_video_quality,
                isTablet = isTablet,
                onClick = { onNavigate(PlayerSidePanelType.QUALITY) },
            )
        }
        item {
            PlayerNavigationItem(
                title = stringResource(id = PlayerR.string.audio),
                subtitle = audioTitle,
                iconRes = CoreR.drawable.ic_audio,
                isTablet = isTablet,
                onClick = { onNavigate(PlayerSidePanelType.AUDIO) },
            )
        }
        item {
            PlayerNavigationItem(
                title = stringResource(id = PlayerR.string.subtitles),
                subtitle = subTitle,
                iconRes = CoreR.drawable.ic_closed_caption,
                isTablet = isTablet,
                onClick = { onNavigate(PlayerSidePanelType.SUBTITLES) },
            )
        }
        item {
            PlayerNavigationItem(
                title = stringResource(id = PlayerR.string.playback_speed),
                subtitle = currentSpeedText,
                iconRes = PlayerR.drawable.ic_playback_speed,
                isTablet = isTablet,
                onClick = { onNavigate(PlayerSidePanelType.SPEED) },
            )
        }
        item {
            PlayerNavigationItem(
                title = stringResource(id = PlayerR.string.aspect_ratio),
                subtitle = currentAspectText,
                iconRes = PlayerR.drawable.ic_aspect_ratio,
                isTablet = isTablet,
                onClick = { onNavigate(PlayerSidePanelType.ASPECT_RATIO) },
            )
        }
        item {
            PlayerNavigationItem(
                title = stringResource(id = PlayerR.string.player_ambient_mode),
                iconRes = CoreR.drawable.ic_ambient_mode,
                showChevron = false,
                isTablet = isTablet,
                trailingContent = { PlayerSwitch(checked = isAmbientModeEnabled) },
                onClick = { onAmbientModeToggle(!isAmbientModeEnabled) },
            )
        }
        item {
            PlayerNavigationItem(
                title = stringResource(id = PlayerR.string.sleep_timer),
                subtitle = sleepTimerSubtitle,
                iconRes = PlayerR.drawable.ic_sleep_timer,
                isTablet = isTablet,
                onClick = { onNavigate(PlayerSidePanelType.SLEEP_TIMER) },
            )
        }
        item {
            PlayerNavigationItem(
                title = stringResource(id = PlayerR.string.lock_screen),
                iconRes = CoreR.drawable.ic_lock,
                showChevron = false,
                enabled = isPlaying,
                isTablet = isTablet,
                onClick = onLock,
            )
        }
        if (viewModel.displayExtraInfo) {
            item {
                PlayerNavigationItem(
                    title = stringResource(id = PlayerR.string.playback_info),
                    iconRes = CoreR.drawable.ic_chart_line,
                    showChevron = true,
                    isTablet = isTablet,
                    onClick = { onNavigate(PlayerSidePanelType.PLAYBACK_INFO) },
                )
            }
        }
    }
}

@Composable
internal fun PhoneSettingsMenu(
    viewModel: PlayerViewModel,
    tracks: Tracks,
    currentResizeMode: PlayerResizeMode,
    currentSpeed: Float,
    isPlaying: Boolean,
    isAmbientModeEnabled: Boolean,
    onAmbientModeToggle: (Boolean) -> Unit,
    onNavigate: (PlayerSidePanelType) -> Unit,
    onLock: () -> Unit,
) =
    PlayerSettingsMenu(
        viewModel = viewModel,
        tracks = tracks,
        currentResizeMode = currentResizeMode,
        currentSpeed = currentSpeed,
        isPlaying = isPlaying,
        isAmbientModeEnabled = isAmbientModeEnabled,
        onAmbientModeToggle = onAmbientModeToggle,
        onNavigate = onNavigate,
        onLock = onLock,
        isTablet = false,
    )

@Composable
internal fun TabletSettingsMenu(
    viewModel: PlayerViewModel,
    tracks: Tracks,
    currentResizeMode: PlayerResizeMode,
    currentSpeed: Float,
    isPlaying: Boolean,
    isAmbientModeEnabled: Boolean,
    onAmbientModeToggle: (Boolean) -> Unit,
    onNavigate: (PlayerSidePanelType) -> Unit,
    onLock: () -> Unit,
) =
    PlayerSettingsMenu(
        viewModel = viewModel,
        tracks = tracks,
        currentResizeMode = currentResizeMode,
        currentSpeed = currentSpeed,
        isPlaying = isPlaying,
        isAmbientModeEnabled = isAmbientModeEnabled,
        onAmbientModeToggle = onAmbientModeToggle,
        onNavigate = onNavigate,
        onLock = onLock,
        isTablet = true,
    )

@Preview(showBackground = true, backgroundColor = 0xFF141416, widthDp = 360)
@Composable
private fun TabletSettingsMenuPreview() {
    FindroidTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlayerNavigationItem(
                title = "Video Quality",
                subtitle = "Auto (1080p)",
                iconRes = PlayerR.drawable.ic_video_quality,
                isTablet = true,
                onClick = {},
            )
            PlayerNavigationItem(
                title = "Audio",
                subtitle = "English - 5.1",
                iconRes = CoreR.drawable.ic_audio,
                isTablet = true,
                onClick = {},
            )
            PlayerNavigationItem(
                title = "Subtitles",
                subtitle = "English [CC]",
                iconRes = CoreR.drawable.ic_closed_caption,
                isTablet = true,
                onClick = {},
            )
            PlayerNavigationItem(
                title = "Playback Speed",
                subtitle = "1.0×",
                iconRes = PlayerR.drawable.ic_playback_speed,
                isTablet = true,
                onClick = {},
            )
            PlayerNavigationItem(
                title = "Aspect Ratio",
                subtitle = "Fit",
                iconRes = PlayerR.drawable.ic_aspect_ratio,
                isTablet = true,
                onClick = {},
            )
            PlayerNavigationItem(
                title = "Sleep Timer",
                subtitle = "Off",
                iconRes = PlayerR.drawable.ic_sleep_timer,
                isTablet = true,
                onClick = {},
            )
        }
    }
}
