package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.theme.CentralShapes
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.domain.models.PlayerSidePanelType
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.settings.domain.Constants.PlayerResizeMode

internal val PanelItemShape = CentralShapes.SmallCard
internal val ChipShape = CentralShapes.Tiny
internal val ChapterThumbShape = CentralShapes.ItemCard

/**
 * Side panel drawer for TV player settings, audio/subtitle selection, playback speed, aspect ratio
 * adjustments, chapters list, and sleep timer.
 *
 * @param panelType The currently active [PlayerSidePanelType] page.
 * @param onPanelTypeChange Callback to navigate between settings and sub-menus.
 * @param viewModel Active [PlayerViewModel] supplying media state and controls.
 * @param currentResizeMode Currently active [PlayerResizeMode].
 * @param onResizeModeChange Callback when a resize mode is selected.
 * @param modifier Optional [Modifier] applied to the side panel container.
 * @param onClose Callback to close the side panel.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerSidePanel(
    panelType: PlayerSidePanelType,
    onPanelTypeChange: (PlayerSidePanelType) -> Unit,
    viewModel: PlayerViewModel,
    currentResizeMode: PlayerResizeMode,
    onResizeModeChange: (PlayerResizeMode) -> Unit,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
) {
    val initialFocusRequester = remember { FocusRequester() }

    // Retain the last active panel type to prevent flickering during exit animation
    var displayedPanelType by remember { mutableStateOf(panelType) }
    if (panelType != PlayerSidePanelType.NONE) {
        displayedPanelType = panelType
    }

    // Remember the last opened setting category to restore focus when navigating back
    var lastOpenedSetting by remember { mutableStateOf<PlayerSidePanelType?>(null) }
    val qualityFocusRequester = remember { FocusRequester() }
    val subtitlesFocusRequester = remember { FocusRequester() }
    val audioFocusRequester = remember { FocusRequester() }
    val speedFocusRequester = remember { FocusRequester() }
    val aspectRatioFocusRequester = remember { FocusRequester() }
    val sleepTimerFocusRequester = remember { FocusRequester() }
    val playbackInfoFocusRequester = remember { FocusRequester() }

    var tracks by remember { mutableStateOf(viewModel.player.currentTracks) }
    DisposableEffect(viewModel.player) {
        val listener =
            object : Player.Listener {
                override fun onTracksChanged(newTracks: Tracks) {
                    tracks = newTracks
                }
            }
        viewModel.player.addListener(listener)
        onDispose { viewModel.player.removeListener(listener) }
    }

    LaunchedEffect(panelType) {
        if (panelType == PlayerSidePanelType.SETTINGS) {
            val target =
                when (lastOpenedSetting) {
                    PlayerSidePanelType.QUALITY -> qualityFocusRequester
                    PlayerSidePanelType.AUDIO -> audioFocusRequester
                    PlayerSidePanelType.SPEED -> speedFocusRequester
                    PlayerSidePanelType.ASPECT_RATIO -> aspectRatioFocusRequester
                    PlayerSidePanelType.SLEEP_TIMER -> sleepTimerFocusRequester
                    PlayerSidePanelType.PLAYBACK_INFO -> playbackInfoFocusRequester
                    else -> subtitlesFocusRequester
                }
            try {
                target.requestFocus()
            } catch (_: Exception) {}
        } else if (panelType != PlayerSidePanelType.NONE) {
            try {
                initialFocusRequester.requestFocus()
            } catch (_: Exception) {}
        } else {
            lastOpenedSetting = null
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .width(360.dp)
                .padding(end = 32.dp, top = 28.dp, bottom = 28.dp)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.key == Key.Back) {
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            onClose()
                        }
                        true
                    } else {
                        false
                    }
                }
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            PanelHeader(panelType = displayedPanelType)

            Spacer(modifier = Modifier.height(16.dp))

            when (displayedPanelType) {
                PlayerSidePanelType.AUDIO -> {
                    AudioTrackList(
                        viewModel = viewModel,
                        initialFocusRequester = initialFocusRequester,
                    )
                }

                PlayerSidePanelType.SUBTITLES -> {
                    SubtitleTrackList(
                        viewModel = viewModel,
                        tracks = tracks,
                        initialFocusRequester = initialFocusRequester,
                    )
                }

                PlayerSidePanelType.SETTINGS -> {
                    SettingsList(
                        viewModel = viewModel,
                        tracks = tracks,
                        currentResizeMode = currentResizeMode,
                        onNavigate = { target ->
                            lastOpenedSetting = target
                            onPanelTypeChange(target)
                        },
                        qualityFocusRequester = qualityFocusRequester,
                        subtitlesFocusRequester = subtitlesFocusRequester,
                        audioFocusRequester = audioFocusRequester,
                        speedFocusRequester = speedFocusRequester,
                        aspectRatioFocusRequester = aspectRatioFocusRequester,
                        sleepTimerFocusRequester = sleepTimerFocusRequester,
                        playbackInfoFocusRequester = playbackInfoFocusRequester,
                    )
                }

                PlayerSidePanelType.QUALITY -> {
                    QualityList(
                        viewModel = viewModel,
                        initialFocusRequester = initialFocusRequester,
                        onBack = { onPanelTypeChange(PlayerSidePanelType.SETTINGS) },
                    )
                }

                PlayerSidePanelType.PLAYBACK_INFO -> {
                    PlaybackInfoList(
                        viewModel = viewModel,
                        initialFocusRequester = initialFocusRequester,
                    )
                }

                PlayerSidePanelType.SPEED -> {
                    SpeedList(
                        viewModel = viewModel,
                        initialFocusRequester = initialFocusRequester,
                        onBack = { onPanelTypeChange(PlayerSidePanelType.SETTINGS) },
                    )
                }

                PlayerSidePanelType.ASPECT_RATIO -> {
                    AspectRatioList(
                        isMpv = viewModel.isMpv,
                        currentResizeMode = currentResizeMode,
                        onResizeModeChange = onResizeModeChange,
                        initialFocusRequester = initialFocusRequester,
                        onBack = { onPanelTypeChange(PlayerSidePanelType.SETTINGS) },
                    )
                }

                PlayerSidePanelType.CHAPTERS -> {
                    ChapterList(
                        viewModel = viewModel,
                        initialFocusRequester = initialFocusRequester,
                        onClose = onClose,
                    )
                }

                PlayerSidePanelType.SLEEP_TIMER -> {
                    SleepTimerList(
                        viewModel = viewModel,
                        initialFocusRequester = initialFocusRequester,
                        onBack = { onPanelTypeChange(PlayerSidePanelType.SETTINGS) },
                    )
                }

                PlayerSidePanelType.NONE -> Unit
            }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun PlayerSidePanelPreview() {
    FindroidTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Column(
                modifier =
                    Modifier.fillMaxHeight()
                        .width(360.dp)
                        .padding(end = 32.dp, top = 28.dp, bottom = 28.dp)
            ) {
                PanelHeader(panelType = PlayerSidePanelType.SETTINGS)
                Spacer(modifier = Modifier.height(16.dp))
                NavigationItem(
                    title = stringResource(id = PlayerR.string.subtitles),
                    subtitle = stringResource(id = PlayerR.string.none),
                    iconRes = CoreR.drawable.ic_closed_caption,
                    onClick = {},
                )
                Spacer(modifier = Modifier.height(8.dp))
                NavigationItem(
                    title = stringResource(id = PlayerR.string.audio),
                    subtitle = "English (Stereo)",
                    iconRes = CoreR.drawable.ic_audio,
                    onClick = {},
                )
                Spacer(modifier = Modifier.height(8.dp))
                NavigationItem(
                    title = stringResource(id = PlayerR.string.playback_speed),
                    subtitle = "1.0×",
                    iconRes = PlayerR.drawable.ic_playback_speed,
                    onClick = {},
                )
            }
        }
    }
}
