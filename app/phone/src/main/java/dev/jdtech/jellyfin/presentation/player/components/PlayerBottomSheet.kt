package dev.jdtech.jellyfin.presentation.player.components

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
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
import androidx.compose.runtime.getValue
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
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.domain.models.SleepTimerMode
import dev.jdtech.jellyfin.player.core.domain.utils.TrackFormatter
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.components.ChapterCard
import dev.jdtech.jellyfin.presentation.components.SelectableTrackItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import kotlinx.coroutines.delay

private val DialogShape = RoundedCornerShape(24.dp)
private val ChipShape = RoundedCornerShape(4.dp)
private val ChapterThumbShape = RoundedCornerShape(12.dp)

private val MenuTopShape =
    RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = 4.dp,
        bottomEnd = 4.dp,
    )
private val MenuBottomShape =
    RoundedCornerShape(
        topStart = 4.dp,
        topEnd = 4.dp,
        bottomStart = 24.dp,
        bottomEnd = 24.dp,
    )
private val MenuMiddleShape = RoundedCornerShape(4.dp)
private val MenuSingleShape = RoundedCornerShape(24.dp)

private fun getItemShape(index: Int, totalCount: Int): Shape {
    return when {
        totalCount <= 1 -> MenuSingleShape
        index == 0 -> MenuTopShape
        index == totalCount - 1 -> MenuBottomShape
        else -> MenuMiddleShape
    }
}

/**
 * Modal bottom sheet presenting player settings, track pickers, playback speed, aspect ratio, and
 * chapters on phone devices.
 *
 * @param panelType The currently active [PlayerSidePanelType] page.
 * @param onPanelTypeChange Callback to navigate between settings and sub-menus.
 * @param viewModel Active [PlayerViewModel] supplying media state and controls.
 * @param currentResizeMode Currently active [AspectRatioFrameLayout] resize mode.
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
    currentResizeMode: Int,
    onResizeModeChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
    openedDirectly: Boolean = false,
) {
    if (panelType == PlayerSidePanelType.NONE) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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

    val config = LocalConfiguration.current
    val maxContentHeight =
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) 220.dp else 400.dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0E0E10),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        },
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
                if (panelType != PlayerSidePanelType.SETTINGS && !openedDirectly) {
                    Box(
                        modifier =
                            Modifier.size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1C1C1E))
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
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
                            onResizeModeSelected = {
                                onResizeModeChange(it)
                                onDismiss()
                            },
                        )
                    }
                    PlayerSidePanelType.CHAPTERS -> {
                        PhoneChaptersList(
                            viewModel = viewModel,
                            onChapterSelected = {},
                        )
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

/**
 * Main quick settings menu displayed in the phone bottom sheet.
 *
 * @param viewModel Active [PlayerViewModel].
 * @param tracks Current media [Tracks].
 * @param currentResizeMode Video resize mode.
 * @param onNavigate Callback to navigate to a sub-menu page.
 * @param onLock Callback to lock the player controls.
 */
@Composable
private fun PhoneSettingsMenu(
    viewModel: PlayerViewModel,
    tracks: Tracks,
    currentResizeMode: Int,
    currentSpeed: Float,
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
            val track = TrackFormatter.toTrack(selectedAudioGroup, 1)
            TrackFormatter.getTrackDisplayInfo(
                    track = track,
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
            val track = TrackFormatter.toTrack(selectedSubGroup, 1)
            TrackFormatter.getTrackDisplayInfo(
                    track = track,
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
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.video_quality),
                subtitle = stringResource(id = PlayerR.string.quality_original_direct_play),
                iconRes = CoreR.drawable.ic_video_quality,
                badge = stringResource(id = PlayerR.string.coming_soon),
                enabled = false,
                shape = MenuSingleShape,
                onClick = {},
            )
        }
        item {
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.audio),
                subtitle = audioTitle,
                iconRes = CoreR.drawable.ic_audio,
                shape = MenuSingleShape,
                onClick = { onNavigate(PlayerSidePanelType.AUDIO) },
            )
        }
        item {
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.subtitles),
                subtitle = subTitle,
                iconRes = CoreR.drawable.ic_closed_caption,
                shape = MenuSingleShape,
                onClick = { onNavigate(PlayerSidePanelType.SUBTITLES) },
            )
        }
        item {
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.playback_speed),
                subtitle = currentSpeedText,
                iconRes = CoreR.drawable.ic_playback_speed,
                shape = MenuSingleShape,
                onClick = { onNavigate(PlayerSidePanelType.SPEED) },
            )
        }
        item {
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.aspect_ratio),
                subtitle = currentAspectText,
                iconRes = CoreR.drawable.ic_aspect_ratio,
                shape = MenuSingleShape,
                onClick = { onNavigate(PlayerSidePanelType.ASPECT_RATIO) },
            )
        }
        item {
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.sleep_timer),
                subtitle = sleepTimerSubtitle,
                iconRes = CoreR.drawable.ic_sleep_timer,
                shape = MenuSingleShape,
                onClick = { onNavigate(PlayerSidePanelType.SLEEP_TIMER) },
            )
        }
        item {
            PhoneNavigationRow(
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
 * Single navigation item row in the phone settings bottom sheet.
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
private fun PhoneNavigationRow(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit,
    shape: Shape = MenuSingleShape,
    badge: String? = null,
    enabled: Boolean = true,
    showChevron: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.4f
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
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color.White.copy(alpha = 0.85f),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            if (badge != null) {
                Box(
                    modifier =
                        Modifier.clip(RoundedCornerShape(4.dp))
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
 * Audio track selection list for phone bottom sheet.
 *
 * @param viewModel Active [PlayerViewModel].
 * @param tracks Current media [Tracks].
 */
@Composable
private fun PhoneAudioTrackList(viewModel: PlayerViewModel, tracks: Tracks) {
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
 * Subtitle track selection list for phone bottom sheet.
 *
 * @param viewModel Active [PlayerViewModel].
 * @param tracks Current media [Tracks].
 */
@Composable
private fun PhoneSubtitleTrackList(viewModel: PlayerViewModel, tracks: Tracks) {
    val context = LocalContext.current
    val currentLocale = LocalConfiguration.current.locales[0]
    val displayExtraInfo = viewModel.displayExtraInfo

    val subtitleGroups =
        remember(tracks) { tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported } }

    val selectedTrackIndex = remember(tracks) { subtitleGroups.indexOfFirst { it.isSelected } }

    var optimisticSelectedIndex by remember(tracks) { mutableStateOf(selectedTrackIndex) }

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
 * Aspect ratio / zoom mode picker list for phone bottom sheet.
 *
 * @param currentResizeMode Currently selected [AspectRatioFrameLayout] resize mode.
 * @param onResizeModeSelected Callback when a resize mode is picked.
 */
@Composable
private fun PhoneAspectRatioList(currentResizeMode: Int, onResizeModeSelected: (Int) -> Unit) {
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
 * Chapters navigation list with thumbnail previews and segment badges for phone bottom sheet.
 *
 * @param viewModel Active [PlayerViewModel].
 * @param onChapterSelected Callback triggered after seeking to the selected chapter.
 */
@Composable
private fun PhoneChaptersList(
    viewModel: PlayerViewModel,
    onChapterSelected: () -> Unit = {},
) {
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
                    onChapterSelected()
                },
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

@Preview(showBackground = true, backgroundColor = 0xFF1E1E1E)
@Composable
private fun PhoneAspectRatioListPreview() {
    FindroidTheme {
        PhoneAspectRatioList(
            currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
            onResizeModeSelected = {},
        )
    }
}
