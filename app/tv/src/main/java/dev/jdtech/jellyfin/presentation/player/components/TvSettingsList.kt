package dev.jdtech.jellyfin.presentation.player.components

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import dev.jdtech.jellyfin.player.core.domain.models.PlayerSidePanelType
import dev.jdtech.jellyfin.player.core.domain.utils.SleepTimerFormatter
import dev.jdtech.jellyfin.player.core.domain.utils.TrackFormatter
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.settings.domain.Constants.PlayerResizeMode
import org.jellyfin.sdk.model.api.MediaStreamType

/**
 * Header title displaying the currently active panel page title.
 *
 * @param panelType The currently active [PlayerSidePanelType] page.
 */
@Composable
internal fun PanelHeader(panelType: PlayerSidePanelType) {
    if (panelType == PlayerSidePanelType.NONE) return

    val titleRes =
        when (panelType) {
            PlayerSidePanelType.AUDIO -> PlayerR.string.audio
            PlayerSidePanelType.SUBTITLES -> PlayerR.string.subtitles
            PlayerSidePanelType.SETTINGS -> CoreR.string.title_settings
            PlayerSidePanelType.SPEED -> PlayerR.string.playback_speed
            PlayerSidePanelType.ASPECT_RATIO -> PlayerR.string.aspect_ratio
            PlayerSidePanelType.CHAPTERS -> PlayerR.string.chapters
            PlayerSidePanelType.SLEEP_TIMER -> PlayerR.string.sleep_timer
            PlayerSidePanelType.QUALITY -> PlayerR.string.video_quality
            PlayerSidePanelType.PLAYBACK_INFO -> PlayerR.string.playback_info
            PlayerSidePanelType.NONE -> error("Unreachable")
        }

    Text(
        text = stringResource(id = titleRes),
        style = MaterialTheme.typography.titleLarge,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

/**
 * Main quick settings menu displayed in the TV side panel drawer.
 *
 * @param viewModel Active [PlayerViewModel].
 * @param tracks Current media [Tracks].
 * @param currentResizeMode Currently active [PlayerResizeMode].
 * @param onNavigate Callback to navigate to a sub-panel page.
 * @param qualityFocusRequester [FocusRequester] for the video quality menu item.
 * @param subtitlesFocusRequester [FocusRequester] for the subtitles menu item.
 * @param audioFocusRequester [FocusRequester] for the audio menu item.
 * @param speedFocusRequester [FocusRequester] for the playback speed menu item.
 * @param aspectRatioFocusRequester [FocusRequester] for the aspect ratio menu item.
 * @param sleepTimerFocusRequester [FocusRequester] for the sleep timer menu item.
 * @param playbackInfoFocusRequester [FocusRequester] for the playback info menu item.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun SettingsList(
    viewModel: PlayerViewModel,
    tracks: Tracks,
    currentResizeMode: PlayerResizeMode,
    onNavigate: (PlayerSidePanelType) -> Unit,
    qualityFocusRequester: FocusRequester,
    subtitlesFocusRequester: FocusRequester,
    audioFocusRequester: FocusRequester,
    speedFocusRequester: FocusRequester,
    aspectRatioFocusRequester: FocusRequester,
    sleepTimerFocusRequester: FocusRequester,
    playbackInfoFocusRequester: FocusRequester,
) {
    val context = LocalContext.current
    val currentLocale = LocalConfiguration.current.locales[0]
    val displayExtraInfo = viewModel.displayExtraInfo
    val uiState by viewModel.uiState.collectAsState()
    val isMovie = uiState.seriesId == null
    val sleepTimerStatus = uiState.sleepTimerStatus

    val sleepTimerSubtitle =
        SleepTimerFormatter.formatSleepTimerSubtitle(
            context = context,
            sleepTimerStatus = sleepTimerStatus,
            isMovie = isMovie,
        )

    val currentSpeed = viewModel.playbackSpeed
    val speedLabel =
        if (currentSpeed == 1.0f) stringResource(id = PlayerR.string.normal_speed)
        else "${currentSpeed}×"

    val currentAudio =
        remember(
            uiState.availableAudioTracks,
            currentLocale,
            displayExtraInfo,
        ) {
            val selectedTrack = uiState.availableAudioTracks.firstOrNull { it.selected }
            selectedTrack?.let {
                TrackFormatter.getTrackDisplayInfo(
                        track = it,
                        context = context,
                        displayExtraInfo = displayExtraInfo,
                        currentLocale = currentLocale,
                    )
                    .name
            }
        }

    val textGroups =
        remember(tracks) { tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported } }
    val currentSubtitle =
        remember(textGroups, currentLocale, displayExtraInfo, uiState.mediaStreams) {
            val selectedIndex = textGroups.indexOfFirst { it.isSelected }
            if (selectedIndex >= 0) {
                val matchedStream =
                    uiState.mediaStreams
                        .filter { it.type == MediaStreamType.SUBTITLE }
                        .getOrNull(selectedIndex)
                val track =
                    TrackFormatter.mergeWithStream(
                        TrackFormatter.toTrack(textGroups[selectedIndex], 1),
                        matchedStream,
                    )
                TrackFormatter.getTrackDisplayInfo(
                        track = track,
                        context = context,
                        displayExtraInfo = displayExtraInfo,
                        currentLocale = currentLocale,
                        fallbackIndex = selectedIndex + 1,
                    )
                    .name
            } else null
        }

    val resizeModeLabel =
        when (currentResizeMode) {
            PlayerResizeMode.ZOOM -> stringResource(id = PlayerR.string.aspect_ratio_zoom)
            PlayerResizeMode.FILL -> stringResource(id = PlayerR.string.aspect_ratio_fill)
            else -> stringResource(id = PlayerR.string.aspect_ratio_fit)
        }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        // Video Quality
        item {
            NavigationItem(
                title = stringResource(id = PlayerR.string.video_quality),
                subtitle = uiState.selectedQuality.label,
                iconRes = PlayerR.drawable.ic_video_quality,
                onClick = { onNavigate(PlayerSidePanelType.QUALITY) },
                modifier = Modifier.focusRequester(qualityFocusRequester),
            )
        }

        // Subtitles
        item {
            NavigationItem(
                title = stringResource(id = PlayerR.string.subtitles),
                subtitle = currentSubtitle ?: stringResource(id = PlayerR.string.none),
                iconRes = CoreR.drawable.ic_closed_caption,
                onClick = { onNavigate(PlayerSidePanelType.SUBTITLES) },
                modifier = Modifier.focusRequester(subtitlesFocusRequester),
            )
        }

        // Audio
        item {
            NavigationItem(
                title = stringResource(id = PlayerR.string.audio),
                subtitle = currentAudio ?: stringResource(id = PlayerR.string.none),
                iconRes = CoreR.drawable.ic_audio,
                onClick = { onNavigate(PlayerSidePanelType.AUDIO) },
                modifier = Modifier.focusRequester(audioFocusRequester),
            )
        }

        // Playback Speed
        item {
            NavigationItem(
                title = stringResource(id = PlayerR.string.playback_speed),
                subtitle = speedLabel,
                iconRes = PlayerR.drawable.ic_playback_speed,
                onClick = { onNavigate(PlayerSidePanelType.SPEED) },
                modifier = Modifier.focusRequester(speedFocusRequester),
            )
        }

        // Aspect Ratio
        item {
            NavigationItem(
                title = stringResource(id = PlayerR.string.aspect_ratio),
                subtitle = resizeModeLabel,
                iconRes = PlayerR.drawable.ic_aspect_ratio,
                onClick = { onNavigate(PlayerSidePanelType.ASPECT_RATIO) },
                modifier = Modifier.focusRequester(aspectRatioFocusRequester),
            )
        }

        // Sleep Timer
        item {
            NavigationItem(
                title = stringResource(id = PlayerR.string.sleep_timer),
                subtitle = sleepTimerSubtitle,
                iconRes = PlayerR.drawable.ic_sleep_timer,
                onClick = { onNavigate(PlayerSidePanelType.SLEEP_TIMER) },
                modifier = Modifier.focusRequester(sleepTimerFocusRequester),
            )
        }

        // Playback Info (visible only when displayExtraInfo is enabled)
        if (displayExtraInfo) {
            item {
                NavigationItem(
                    title = stringResource(id = PlayerR.string.playback_info),
                    subtitle = "",
                    iconRes = CoreR.drawable.ic_chart_line,
                    onClick = { onNavigate(PlayerSidePanelType.PLAYBACK_INFO) },
                    modifier = Modifier.focusRequester(playbackInfoFocusRequester),
                )
            }
        }
    }
}

/**
 * Focusable navigation item row for TV settings menu.
 *
 * @param title Primary item label.
 * @param iconRes Drawable resource ID for the row icon.
 * @param onClick Callback when the row is pressed.
 * @param modifier Optional [Modifier] applied to the item surface.
 * @param subtitle Secondary or currently selected value label.
 * @param shape Clickable surface shape configuration.
 * @param colors Clickable surface colors configuration.
 */
@Composable
internal fun NavigationItem(
    title: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalContentColor.current.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Icon(
                painter = painterResource(id = CoreR.drawable.ic_arrow_right),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Preview
@Composable
internal fun NavigationItemPreview() {
    FindroidTheme {
        NavigationItem(
            title = "Audio",
            subtitle = "English (5.1)",
            iconRes = CoreR.drawable.ic_audio,
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun SettingsListPreview() {
    FindroidTheme {
        Column(
            modifier = Modifier.width(320.dp).background(Color.Black).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NavigationItem(
                title = stringResource(id = PlayerR.string.subtitles),
                subtitle = "English",
                iconRes = CoreR.drawable.ic_closed_caption,
                onClick = {},
            )
            NavigationItem(
                title = stringResource(id = PlayerR.string.audio),
                subtitle = "Japanese (5.1)",
                iconRes = CoreR.drawable.ic_audio,
                onClick = {},
            )
            NavigationItem(
                title = stringResource(id = PlayerR.string.playback_speed),
                subtitle = stringResource(id = PlayerR.string.normal_speed),
                iconRes = PlayerR.drawable.ic_playback_speed,
                onClick = {},
            )
            NavigationItem(
                title = stringResource(id = PlayerR.string.aspect_ratio),
                subtitle = stringResource(id = PlayerR.string.aspect_ratio_fit),
                iconRes = PlayerR.drawable.ic_aspect_ratio,
                onClick = {},
            )
            NavigationItem(
                title = stringResource(id = PlayerR.string.sleep_timer),
                subtitle = stringResource(id = PlayerR.string.sleep_timer_off),
                iconRes = PlayerR.drawable.ic_sleep_timer,
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun PanelHeaderPreview() {
    FindroidTheme {
        Column(
            modifier = Modifier.width(320.dp).background(Color.Black).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PanelHeader(panelType = PlayerSidePanelType.SETTINGS)
            PanelHeader(panelType = PlayerSidePanelType.AUDIO)
        }
    }
}
