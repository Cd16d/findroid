package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.ui.AspectRatioFrameLayout
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.theme.CentralShapes
import dev.jdtech.jellyfin.core.presentation.theme.PlayerSheetBackground
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.domain.models.SleepTimerMode
import dev.jdtech.jellyfin.player.core.domain.utils.TrackFormatter
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.components.ChapterCard
import dev.jdtech.jellyfin.presentation.components.SelectableTrackItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import kotlinx.coroutines.delay

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
    currentResizeMode: Int,
    onResizeModeChange: (Int) -> Unit,
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
                PlayerSidePanelType.AUDIO -> {
                    TabletAudioTrackList(viewModel = viewModel, tracks = tracks)
                }
                PlayerSidePanelType.SUBTITLES -> {
                    TabletSubtitleTrackList(viewModel = viewModel, tracks = tracks)
                }
                PlayerSidePanelType.SPEED -> {
                    PlaybackSpeedPicker(
                        currentSpeed = uiState.playbackSpeed,
                        onSpeedChange = { viewModel.selectSpeed(it) },
                        isTabletLayout = true,
                    )
                }
                PlayerSidePanelType.ASPECT_RATIO -> {
                    TabletAspectRatioList(
                        currentResizeMode = currentResizeMode,
                        onResizeModeSelected = onResizeModeChange,
                    )
                }
                PlayerSidePanelType.CHAPTERS -> {
                    TabletChaptersList(viewModel = viewModel)
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
private fun TabletSettingsMenu(
    viewModel: PlayerViewModel,
    tracks: Tracks,
    currentSpeed: Float,
    currentResizeMode: Int,
    isPlaying: Boolean,
    onNavigate: (PlayerSidePanelType) -> Unit,
    onLock: () -> Unit,
) {
    val context = LocalContext.current
    val currentLocale = LocalConfiguration.current.locales[0]

    val selectedAudioGroup =
        tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
    val audioTitle =
        if (selectedAudioGroup != null) {
            TrackFormatter.getTrackDisplayInfo(
                    group = selectedAudioGroup,
                    context = context,
                    displayExtraInfo = viewModel.displayExtraInfo,
                    currentLocale = currentLocale,
                )
                .name
        } else stringResource(id = PlayerR.string.none)

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

    val currentSpeedText = "${currentSpeed}x"
    val currentAspectText =
        when (currentResizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM ->
                stringResource(id = PlayerR.string.aspect_ratio_zoom)
            AspectRatioFrameLayout.RESIZE_MODE_FILL ->
                stringResource(id = PlayerR.string.aspect_ratio_fill)
            else -> stringResource(id = PlayerR.string.aspect_ratio_fit)
        }

    val uiState by viewModel.uiState.collectAsState()
    val isMovie = uiState.seriesId == null
    val sleepTimerSubtitle =
        if (uiState.sleepTimerStatus.isActive) {
            when (val mode = uiState.sleepTimerStatus.mode) {
                is SleepTimerMode.EndOfMedia -> {
                    if (isMovie) stringResource(id = PlayerR.string.sleep_timer_end_of_video)
                    else stringResource(id = PlayerR.string.sleep_timer_end_of_episode)
                }
                is SleepTimerMode.Duration -> {
                    val remainingMins = (uiState.sleepTimerStatus.remainingSeconds + 59L) / 60L
                    stringResource(id = PlayerR.string.sleep_timer_remaining, remainingMins.toInt())
                }
                else -> stringResource(id = PlayerR.string.sleep_timer_off)
            }
        } else {
            stringResource(id = PlayerR.string.sleep_timer_off)
        }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            TabletNavigationItem(
                title = stringResource(id = PlayerR.string.video_quality),
                subtitle = stringResource(id = PlayerR.string.quality_original_direct_play),
                iconRes = CoreR.drawable.ic_video_quality,
                shape = MenuSingleShape,
                badge = stringResource(id = PlayerR.string.coming_soon),
                enabled = false,
                onClick = {},
            )
        }
        item {
            TabletNavigationItem(
                title = stringResource(id = PlayerR.string.audio),
                subtitle = audioTitle,
                iconRes = CoreR.drawable.ic_audio,
                shape = MenuSingleShape,
                onClick = { onNavigate(PlayerSidePanelType.AUDIO) },
            )
        }
        item {
            TabletNavigationItem(
                title = stringResource(id = PlayerR.string.subtitles),
                subtitle = subTitle,
                iconRes = CoreR.drawable.ic_closed_caption,
                shape = MenuSingleShape,
                onClick = { onNavigate(PlayerSidePanelType.SUBTITLES) },
            )
        }
        item {
            TabletNavigationItem(
                title = stringResource(id = PlayerR.string.playback_speed),
                subtitle = currentSpeedText,
                iconRes = CoreR.drawable.ic_playback_speed,
                shape = MenuSingleShape,
                onClick = { onNavigate(PlayerSidePanelType.SPEED) },
            )
        }
        item {
            TabletNavigationItem(
                title = stringResource(id = PlayerR.string.aspect_ratio),
                subtitle = currentAspectText,
                iconRes = CoreR.drawable.ic_aspect_ratio,
                shape = MenuSingleShape,
                onClick = { onNavigate(PlayerSidePanelType.ASPECT_RATIO) },
            )
        }
        item {
            TabletNavigationItem(
                title = stringResource(id = PlayerR.string.sleep_timer),
                subtitle = sleepTimerSubtitle,
                iconRes = CoreR.drawable.ic_sleep_timer,
                shape = MenuSingleShape,
                onClick = { onNavigate(PlayerSidePanelType.SLEEP_TIMER) },
            )
        }
        item {
            TabletNavigationItem(
                title = stringResource(id = PlayerR.string.lock_screen),
                subtitle = "",
                iconRes = CoreR.drawable.ic_lock,
                shape = MenuSingleShape,
                showChevron = false,
                enabled = isPlaying,
                onClick = onLock,
            )
        }
    }
}

/**
 * Single navigation item row in the tablet side panel menu.
 *
 * @param title Primary item label.
 * @param subtitle Secondary or currently selected value label.
 * @param iconRes Drawable resource ID for the row icon.
 * @param onClick Callback when the row is pressed.
 * @param shape Corner shape defining the visual card appearance in the grouped list.
 * @param badge Optional text badge (e.g. "Coming Soon").
 * @param enabled Whether this menu row is interactable.
 * @param showChevron Whether to show the chevron right indicator.
 */
@Composable
private fun TabletNavigationItem(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit,
    shape: Shape = MenuSingleShape,
    badge: String? = null,
    enabled: Boolean = true,
    showChevron: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.38f
    Surface(
        modifier = Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha },
        shape = shape,
        color = Color(0xFF141416),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = Color.White.copy(alpha = 0.85f),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            if (badge != null) {
                Box(
                    modifier =
                        Modifier.clip(CentralShapes.Tiny)
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showChevron && enabled) {
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_chevron_right),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White.copy(alpha = 0.6f),
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
private fun TabletAudioTrackList(viewModel: PlayerViewModel, tracks: Tracks) {
    val context = LocalContext.current
    val currentLocale = LocalConfiguration.current.locales[0]
    val displayExtraInfo = viewModel.displayExtraInfo

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
                shape = MenuSingleShape,
                onClick = {
                    optimisticSelectedIndex = index
                    viewModel.switchToTrack(C.TRACK_TYPE_AUDIO, index)
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
private fun TabletSubtitleTrackList(viewModel: PlayerViewModel, tracks: Tracks) {
    val context = LocalContext.current
    val currentLocale = LocalConfiguration.current.locales[0]
    val displayExtraInfo = viewModel.displayExtraInfo

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
                shape = MenuSingleShape,
                onClick = {
                    optimisticSelectedIndex = -1
                    viewModel.switchToTrack(C.TRACK_TYPE_TEXT, -1)
                },
            )
        }
        itemsIndexed(items = subtitleGroups) { index, group ->
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
                shape = MenuSingleShape,
                onClick = {
                    optimisticSelectedIndex = index
                    viewModel.switchToTrack(C.TRACK_TYPE_TEXT, index)
                },
            )
        }
    }
}

/**
 * Aspect ratio / zoom mode picker list for tablet side panel.
 *
 * @param currentResizeMode Currently selected [AspectRatioFrameLayout] resize mode.
 * @param onResizeModeSelected Callback when a resize mode is picked.
 */
@Composable
private fun TabletAspectRatioList(currentResizeMode: Int, onResizeModeSelected: (Int) -> Unit) {
    val modes =
        listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT to
                stringResource(id = PlayerR.string.aspect_ratio_fit),
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM to
                stringResource(id = PlayerR.string.aspect_ratio_zoom),
            AspectRatioFrameLayout.RESIZE_MODE_FILL to
                stringResource(id = PlayerR.string.aspect_ratio_fill),
        )
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(modes) { _, (mode, label) ->
            val isSelected = currentResizeMode == mode
            SelectableTrackItem(
                title = label,
                chips = emptyList(),
                isSelected = isSelected,
                shape = MenuSingleShape,
                onClick = { onResizeModeSelected(mode) },
            )
        }
    }
}

/**
 * Chapters navigation list with thumbnail previews and segment badges for tablet side panel.
 *
 * @param viewModel Active [PlayerViewModel].
 */
@Composable
private fun TabletChaptersList(viewModel: PlayerViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val chapters = uiState.currentChapters
    val trickplay = uiState.currentTrickplay
    var currentPosition by remember { mutableLongStateOf(viewModel.player.currentPosition) }
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel.player) {
        while (true) {
            currentPosition = viewModel.player.currentPosition
            delay(400)
        }
    }

    DisposableEffect(viewModel.player) {
        val listener =
            object : Player.Listener {
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    currentPosition = newPosition.positionMs
                }
            }
        viewModel.player.addListener(listener)
        onDispose { viewModel.player.removeListener(listener) }
    }

    val currentChapterIndex =
        remember(currentPosition, chapters) {
            chapters.indexOfLast { it.startPosition <= currentPosition }
        }

    LaunchedEffect(currentChapterIndex) {
        if (currentChapterIndex >= 0) {
            listState.animateScrollToItem(currentChapterIndex)
        }
    }

    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(chapters) { index, chapter ->
            val isCurrent = index == currentChapterIndex
            val nextChapterStart = chapters.getOrNull(index + 1)?.startPosition ?: Long.MAX_VALUE
            ChapterCard(
                chapter = chapter,
                index = index,
                isCurrent = isCurrent,
                nextChapterStart = nextChapterStart,
                segments = uiState.segments,
                durationMs = viewModel.player.duration.coerceAtLeast(0L),
                trickplay = trickplay,
                onClick = {
                    currentPosition = chapter.startPosition
                    viewModel.player.seekTo(chapter.startPosition)
                },
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141414, widthDp = 400, heightDp = 600)
@Composable
private fun PlayerSidePanelPreview() {
    FindroidTheme {
        Surface(color = Color(0xFF141414)) {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .padding(top = 28.dp, bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(id = CoreR.string.title_settings),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                TabletNavigationItem(
                    title = stringResource(id = PlayerR.string.audio),
                    subtitle = "English (Stereo)",
                    iconRes = CoreR.drawable.ic_audio,
                    onClick = {},
                )
                Spacer(modifier = Modifier.height(8.dp))
                TabletNavigationItem(
                    title = stringResource(id = PlayerR.string.subtitles),
                    subtitle = stringResource(id = PlayerR.string.none),
                    iconRes = CoreR.drawable.ic_closed_caption,
                    onClick = {},
                )
                Spacer(modifier = Modifier.height(8.dp))
                TabletNavigationItem(
                    title = stringResource(id = PlayerR.string.playback_speed),
                    subtitle = "1.0x",
                    iconRes = CoreR.drawable.ic_playback_speed,
                    onClick = {},
                )
                Spacer(modifier = Modifier.height(8.dp))
                TabletNavigationItem(
                    title = stringResource(id = PlayerR.string.aspect_ratio),
                    subtitle = stringResource(id = PlayerR.string.aspect_ratio_fit),
                    iconRes = CoreR.drawable.ic_aspect_ratio,
                    onClick = {},
                )
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

@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
private fun TabletAspectRatioListPreview() {
    FindroidTheme {
        TabletAspectRatioList(
            currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            onResizeModeSelected = {},
        )
    }
}
