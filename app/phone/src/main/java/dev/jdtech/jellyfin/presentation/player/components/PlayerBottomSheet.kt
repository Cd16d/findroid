package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
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
    isAmbientModeEnabled: Boolean = false,
    onAmbientModeToggle: (Boolean) -> Unit = {},
) {
    if (panelType == PlayerSidePanelType.NONE) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val uiState by viewModel.uiState.collectAsState()
    var tracks by remember { mutableStateOf(viewModel.player.currentTracks) }
    var isPlaying by remember { mutableStateOf(viewModel.player.isPlaying) }

    val density = LocalDensity.current
    val transitionDistancePx = with(density) { 48.dp.toPx() }

    val cornerRadius by
        remember(sheetState, transitionDistancePx) {
            derivedStateOf {
                val offset = runCatching { sheetState.requireOffset() }.getOrNull()
                if (offset == null) {
                    28.dp
                } else {
                    val progress = (offset / transitionDistancePx).coerceIn(0f, 1f)
                    28.dp * progress
                }
            }
        }

    val handleTopPadding by
        remember(sheetState, transitionDistancePx) {
            derivedStateOf {
                val offset = runCatching { sheetState.requireOffset() }.getOrNull()
                if (offset == null) {
                    22.dp
                } else {
                    val progress = (offset / transitionDistancePx).coerceIn(0f, 1f)
                    22.dp * progress
                }
            }
        }

    LaunchedEffect(panelType) {
        if (panelType != PlayerSidePanelType.SETTINGS && panelType != PlayerSidePanelType.NONE) {
            runCatching { sheetState.expand() }
        }
    }

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

    val handleLock =
        remember(onDismiss, onLock) {
            {
                onDismiss()
                onLock()
            }
        }
    val handleNavigateToSettings =
        remember(onPanelTypeChange) { { onPanelTypeChange(PlayerSidePanelType.SETTINGS) } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
        containerColor = PlayerSheetContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = handleTopPadding, bottom = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.size(width = 32.dp, height = 4.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                ) {}
            }
        },
        contentWindowInsets = { WindowInsets.statusBars },
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .widthIn(max = 540.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 20.dp)
        ) {
            // Header with Back navigation & Title
            val showBackButton = (panelType != PlayerSidePanelType.SETTINGS) && !openedDirectly
            val isSettings =
                panelType == PlayerSidePanelType.SETTINGS || panelType == PlayerSidePanelType.NONE
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .padding(start = if (isSettings) 12.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showBackButton) {
                    Box(
                        modifier =
                            Modifier.size(36.dp)
                                .clip(CircleShape)
                                .background(PlayerSheetBackground)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true, radius = 18.dp),
                                    onClick = handleNavigateToSettings,
                                ),
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

            AnimatedContent(
                targetState = panelType,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220, delayMillis = 60)) +
                            scaleIn(
                                initialScale = 0.98f,
                                animationSpec = tween(220, delayMillis = 60),
                            ))
                        .togetherWith(fadeOut(animationSpec = tween(90)))
                        .using(
                            SizeTransform(
                                clip = false,
                                sizeAnimationSpec = { _, _ ->
                                    tween(durationMillis = 300, easing = FastOutSlowInEasing)
                                },
                            )
                        )
                },
                label = "PlayerBottomSheetPanelTransition",
                modifier = Modifier.fillMaxWidth().heightIn(max = maxContentHeight),
            ) { targetPanel ->
                when (targetPanel) {
                    PlayerSidePanelType.SETTINGS -> {
                        PhoneSettingsMenu(
                            viewModel = viewModel,
                            tracks = tracks,
                            currentResizeMode = currentResizeMode,
                            currentSpeed = uiState.playbackSpeed,
                            isPlaying = isPlaying,
                            isAmbientModeEnabled = isAmbientModeEnabled,
                            onAmbientModeToggle = onAmbientModeToggle,
                            onNavigate = onPanelTypeChange,
                            onLock = handleLock,
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
    FindroidTheme(darkTheme = true) {
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
            PlayerNavigationItem(
                title = stringResource(id = PlayerR.string.audio),
                subtitle = "English",
                iconRes = CoreR.drawable.ic_audio,
                onClick = {},
            )
            Spacer(modifier = Modifier.height(8.dp))
            PlayerNavigationItem(
                title = stringResource(id = PlayerR.string.subtitles),
                subtitle = "English [CC]",
                iconRes = CoreR.drawable.ic_closed_caption,
                onClick = {},
            )
            Spacer(modifier = Modifier.height(8.dp))
            PlayerNavigationItem(
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
    FindroidTheme(darkTheme = true) {
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
    FindroidTheme(darkTheme = true) {
        PlaybackSpeedPicker(
            currentSpeed = 1f,
            onSpeedChange = {},
        )
    }
}
