package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
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
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.domain.models.PlayerSidePanelType
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.components.SelectableTrackItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.settings.domain.Constants.PlayerResizeMode

/**
 * Sliding side panel for large screen / tablet devices hosting settings, tracks, speed, and
 * chapters.
 *
 * @param panelType The currently active [PlayerSidePanelType] page.
 * @param onPanelTypeChange Callback to switch between pages.
 * @param viewModel Active [PlayerViewModel].
 * @param currentResizeMode Currently active video resize mode.
 * @param onResizeModeChange Callback when a new resize mode is selected.
 * @param onClose Callback to close the side panel.
 * @param onLock Callback to lock the player controls.
 * @param modifier Optional [Modifier] applied to the side panel container.
 * @param openedDirectly Whether the panel was opened directly to a sub-page.
 */
@Composable
fun PlayerSidePanel(
    panelType: PlayerSidePanelType,
    onPanelTypeChange: (PlayerSidePanelType) -> Unit,
    viewModel: PlayerViewModel,
    currentResizeMode: PlayerResizeMode,
    onResizeModeChange: (PlayerResizeMode) -> Unit,
    onClose: () -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
    openedDirectly: Boolean = false,
) {
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

    Box(modifier = modifier.width(400.dp).fillMaxHeight()) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(top = 28.dp, bottom = 24.dp)
        ) {
            // Header with Back navigation & Title
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (panelType != PlayerSidePanelType.SETTINGS && !openedDirectly) {
                    Box(
                        modifier =
                            Modifier.size(36.dp)
                                .clip(CircleShape)
                                .background(PlayerSheetBackground)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true, radius = 18.dp),
                                    onClick = { onPanelTypeChange(PlayerSidePanelType.SETTINGS) },
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = CoreR.drawable.ic_arrow_left),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color.White,
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
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            when (panelType) {
                PlayerSidePanelType.SETTINGS -> {
                    TabletSettingsMenu(
                        viewModel = viewModel,
                        tracks = tracks,
                        currentSpeed = uiState.playbackSpeed,
                        currentResizeMode = currentResizeMode,
                        isPlaying = isPlaying,
                        onNavigate = onPanelTypeChange,
                        onLock = {
                            onClose()
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
                    TabletAudioTrackList(viewModel = viewModel, tracks = tracks)
                }

                PlayerSidePanelType.SUBTITLES -> {
                    PhoneSubtitleTrackList(viewModel = viewModel, tracks = tracks)
                }

                PlayerSidePanelType.SPEED -> {
                    PlaybackSpeedPicker(
                        currentSpeed = uiState.playbackSpeed,
                        onSpeedChange = { viewModel.selectSpeed(it) },
                        isTabletLayout = true,
                    )
                }

                PlayerSidePanelType.ASPECT_RATIO -> {
                    PhoneAspectRatioList(
                        isMpv = viewModel.isMpv,
                        currentResizeMode = currentResizeMode,
                        onResizeModeSelected = onResizeModeChange,
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

@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
private fun TabletSelectableItemPreview() {
    FindroidTheme {
        SelectableTrackItem(
            title = "English - Stereo",
            chips = listOf("Default"),
            isSelected = true,
            onClick = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
private fun TabletSpeedListPreview() {
    FindroidTheme {
        PlaybackSpeedPicker(
            currentSpeed = 1.25f,
            onSpeedChange = {},
        )
    }
}
