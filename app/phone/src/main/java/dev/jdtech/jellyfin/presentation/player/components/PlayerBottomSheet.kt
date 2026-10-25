package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.theme.PlayerSheetBackground
import dev.jdtech.jellyfin.core.presentation.theme.PlayerSheetContainer
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.domain.models.PlayerSidePanelType
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.components.SelectableTrackItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.settings.domain.Constants.PlayerResizeMode

/**
 * Modal bottom sheet presenting player settings, track pickers, playback speed, aspect ratio, and
 * chapters on phone devices.
 *
 * @param panelType The currently active [PlayerSidePanelType] page.
 * @param onPanelTypeChange Callback to navigate between settings and sub-menus.
 * @param viewModel Active [PlayerViewModel] supplying media state and controls.
 * @param currentResizeMode Currently active [PlayerResizeMode] resize mode.
 * @param onResizeModeChange Callback when a resize mode is selected.
 * @param onDismiss Callback to close and dismiss the bottom sheet.
 * @param onLock Callback to lock the player screen.
 * @param modifier Optional [Modifier] applied to the bottom sheet.
 * @param openedDirectly Whether the sheet was launched directly to a specific sub-panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerBottomSheet(
    panelType: PlayerSidePanelType,
    onPanelTypeChange: (PlayerSidePanelType) -> Unit,
    viewModel: PlayerViewModel,
    currentResizeMode: PlayerResizeMode,
    onResizeModeChange: (PlayerResizeMode) -> Unit,
    onDismiss: () -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
    openedDirectly: Boolean = false,
) {
    if (panelType == PlayerSidePanelType.NONE) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val uiState by viewModel.uiState.collectAsState()
    var tracks by remember { mutableStateOf(viewModel.player.currentTracks) }
    var isPlaying by remember { mutableStateOf(viewModel.player.isPlaying) }

    DisposableEffect(viewModel.player) {
        val listener =
            object : Player.Listener {
                override fun onTracksChanged(newTracks: Tracks) {
                    tracks = newTracks
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            }
        viewModel.player.addListener(listener)
        onDispose { viewModel.player.removeListener(listener) }
    }

    val windowInfo = LocalWindowInfo.current
    val maxContentHeight = (windowInfo.containerDpSize.height - 48.dp).coerceAtLeast(200.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PlayerSheetContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .widthIn(max = 540.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp)
        ) {
            // Header with Back navigation & Title
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if ((panelType != PlayerSidePanelType.SETTINGS) && !openedDirectly) {
                    Box(
                        modifier =
                            Modifier.size(36.dp)
                                .clip(CircleShape)
                                .background(PlayerSheetBackground)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true, radius = 18.dp),
                                ) {
                                    onPanelTypeChange(PlayerSidePanelType.SETTINGS)
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = CoreR.drawable.ic_arrow_left),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Text(
                    text =
                        stringResource(
                            id =
                                when (panelType) {
                                    PlayerSidePanelType.AUDIO -> PlayerR.string.select_audio_track
                                    PlayerSidePanelType.SUBTITLES ->
                                        PlayerR.string.select_subtitle_track
                                    PlayerSidePanelType.SPEED ->
                                        PlayerR.string.select_playback_speed
                                    PlayerSidePanelType.ASPECT_RATIO -> PlayerR.string.aspect_ratio
                                    PlayerSidePanelType.CHAPTERS -> PlayerR.string.chapters
                                    PlayerSidePanelType.SLEEP_TIMER -> PlayerR.string.sleep_timer
                                    PlayerSidePanelType.QUALITY -> PlayerR.string.video_quality
                                    PlayerSidePanelType.PLAYBACK_INFO ->
                                        PlayerR.string.playback_info
                                    PlayerSidePanelType.SETTINGS,
                                    PlayerSidePanelType.NONE -> CoreR.string.title_settings
                                }
                        ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(modifier = Modifier.fillMaxWidth().heightIn(max = maxContentHeight)) {
                when (panelType) {
                    PlayerSidePanelType.SETTINGS -> {
                        PhoneSettingsMenu(
                            viewModel = viewModel,
                            tracks = tracks,
                            currentResizeMode = currentResizeMode,
                            currentSpeed = uiState.playbackSpeed,
                            isPlaying = isPlaying,
                            onNavigate = onPanelTypeChange,
                            onLock = {
                                onDismiss()
                                onLock()
                            },
                        )
                    }
                    PlayerSidePanelType.QUALITY -> {
                        PhoneQualityList(
                            viewModel = viewModel,
                            onQualitySelected = { viewModel.selectVideoQuality(it) },
                        )
                    }
                    PlayerSidePanelType.PLAYBACK_INFO -> {
                        PlaybackInfoView(
                            infoData = uiState.playbackInfoData,
                            onOpened = { viewModel.onPlaybackInfoOpened() },
                            onRefresh = { viewModel.refreshPlaybackStats() },
                        )
                    }
                    PlayerSidePanelType.AUDIO -> {
                        PhoneAudioTrackList(viewModel = viewModel, tracks = tracks)
                    }
                    PlayerSidePanelType.SUBTITLES -> {
                        PhoneSubtitleTrackList(viewModel = viewModel, tracks = tracks)
                    }
                    PlayerSidePanelType.SPEED -> {
                        PlaybackSpeedPicker(
                            currentSpeed = uiState.playbackSpeed,
                            onSpeedChange = { viewModel.selectSpeed(it) },
                            isTabletLayout = false,
                        )
                    }
                    PlayerSidePanelType.ASPECT_RATIO -> {
                        PhoneAspectRatioList(
                            currentResizeMode = currentResizeMode,
                            isMpv = viewModel.isMpv,
                            onResizeModeSelected = {
                                onResizeModeChange(it)
                                onDismiss()
                            },
                        )
                    }
                    PlayerSidePanelType.CHAPTERS -> {
                        PhoneChaptersList(viewModel = viewModel)
                    }
                    PlayerSidePanelType.SLEEP_TIMER -> {
                        SleepTimerPicker(
                            sleepTimerStatus = uiState.sleepTimerStatus,
                            onSelectMode = { viewModel.setSleepTimer(it) },
                            isMovie = uiState.seriesId == null,
                        )
                    }
                    PlayerSidePanelType.NONE -> Unit
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun PlayerBottomSheetPreview() {
    FindroidTheme {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(id = CoreR.string.title_settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.audio),
                subtitle = "English",
                iconRes = CoreR.drawable.ic_audio,
                onClick = {},
            )
            Spacer(modifier = Modifier.height(8.dp))
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.subtitles),
                subtitle = "English [CC]",
                iconRes = CoreR.drawable.ic_closed_caption,
                onClick = {},
            )
            Spacer(modifier = Modifier.height(8.dp))
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.playback_speed),
                subtitle = "1.0×",
                iconRes = PlayerR.drawable.ic_playback_speed,
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1E1E1E)
@Composable
private fun PhoneSelectableItemPreview() {
    FindroidTheme {
        SelectableTrackItem(
            title = "English [CC]",
            chips = listOf("SDH", "Default"),
            isSelected = true,
            onClick = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1E1E1E)
@Composable
private fun PhoneSpeedListPreview() {
    FindroidTheme {
        PlaybackSpeedPicker(
            currentSpeed = 1f,
            onSpeedChange = {},
        )
    }
}
