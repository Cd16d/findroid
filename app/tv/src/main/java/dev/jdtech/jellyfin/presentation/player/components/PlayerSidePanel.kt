package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
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
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.theme.CentralShapes
import dev.jdtech.jellyfin.core.presentation.theme.PlayerTvSidePanelContainer
import dev.jdtech.jellyfin.core.presentation.theme.PlayerTvSidePanelUnselected
import dev.jdtech.jellyfin.models.FindroidSegmentType
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.domain.models.SleepTimerMode
import dev.jdtech.jellyfin.player.core.domain.models.Trickplay
import dev.jdtech.jellyfin.player.core.domain.models.intervalMs
import dev.jdtech.jellyfin.player.core.domain.utils.ChapterUtils
import dev.jdtech.jellyfin.player.core.domain.utils.TrackFormatter
import dev.jdtech.jellyfin.player.core.presentation.findChapterSegment
import dev.jdtech.jellyfin.player.core.presentation.findChapterSegmentType
import dev.jdtech.jellyfin.player.core.presentation.getSegmentColor
import dev.jdtech.jellyfin.player.core.presentation.labelRes
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.settings.domain.Constants.PlayerResizeMode
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.MediaStreamType

private val PanelItemShape = CentralShapes.SmallCard
private val ChipShape = CentralShapes.Tiny
private val ChapterThumbShape = CentralShapes.ItemCard

enum class PlayerSidePanelType {
    NONE,
    AUDIO,
    SUBTITLES,
    SETTINGS,
    SPEED,
    ASPECT_RATIO,
    CHAPTERS,
    SLEEP_TIMER,
}

/**
 * Side panel drawer for TV player settings, audio/subtitle selection, playback speed, aspect ratio
 * adjustments, chapters list, and sleep timer.
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
    val subtitlesFocusRequester = remember { FocusRequester() }
    val audioFocusRequester = remember { FocusRequester() }
    val speedFocusRequester = remember { FocusRequester() }
    val aspectRatioFocusRequester = remember { FocusRequester() }
    val sleepTimerFocusRequester = remember { FocusRequester() }

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
                    PlayerSidePanelType.AUDIO -> audioFocusRequester
                    PlayerSidePanelType.SPEED -> speedFocusRequester
                    PlayerSidePanelType.ASPECT_RATIO -> aspectRatioFocusRequester
                    PlayerSidePanelType.SLEEP_TIMER -> sleepTimerFocusRequester
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
                        tracks = tracks,
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
                        subtitlesFocusRequester = subtitlesFocusRequester,
                        audioFocusRequester = audioFocusRequester,
                        speedFocusRequester = speedFocusRequester,
                        aspectRatioFocusRequester = aspectRatioFocusRequester,
                        sleepTimerFocusRequester = sleepTimerFocusRequester,
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

@Composable
private fun PanelHeader(panelType: PlayerSidePanelType) {
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

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun AudioTrackList(
    viewModel: PlayerViewModel,
    tracks: Tracks,
    initialFocusRequester: FocusRequester,
) {
    val context = LocalContext.current
    val currentLocale = LocalConfiguration.current.locales[0]
    val displayExtraInfo = viewModel.displayExtraInfo
    val uiState by viewModel.uiState.collectAsState()
    val mediaStreams = uiState.mediaStreams
    val audioStreams =
        remember(mediaStreams) { mediaStreams.filter { it.type == MediaStreamType.AUDIO } }

    val audioGroups =
        remember(tracks) {
            tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
        }

    if (audioGroups.isEmpty()) {
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
                items = audioGroups,
                key = { index, group ->
                    group.mediaTrackGroup.id.takeIf { it.isNotBlank() } ?: index
                },
            ) { index, group ->
                val isSelected = group.isSelected
                val matchedStream = audioStreams.getOrNull(index)
                val displayInfo =
                    remember(group, currentLocale, displayExtraInfo, matchedStream) {
                        val track =
                            TrackFormatter.toTrack(group, index + 1).let { t ->
                                if (matchedStream != null) {
                                    t.copy(
                                        codec =
                                            matchedStream.codec.takeIf { it.isNotBlank() }
                                                ?: t.codec,
                                        channelLayout =
                                            matchedStream.channelLayout ?: t.channelLayout,
                                        label =
                                            t.label
                                                ?: matchedStream.title.takeIf { it.isNotBlank() }
                                                ?: matchedStream.displayTitle,
                                    )
                                } else {
                                    t
                                }
                            }
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
                    onClick = { viewModel.switchToTrack(C.TRACK_TYPE_AUDIO, index) },
                    modifier =
                        if (isFirst) Modifier.focusRequester(initialFocusRequester) else Modifier,
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun SubtitleTrackList(
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
                        TrackFormatter.toTrack(group, index + 1).let { t ->
                            if (matchedStream != null) {
                                val streamTrack = TrackFormatter.toTrack(matchedStream)
                                t.copy(
                                    isForced = streamTrack.isForced == true || t.isForced == true,
                                    isHearingImpaired =
                                        streamTrack.isHearingImpaired == true ||
                                            t.isHearingImpaired == true,
                                    isExternal =
                                        streamTrack.isExternal == true || t.isExternal == true,
                                    codec =
                                        streamTrack.codec?.takeIf { it.isNotBlank() } ?: t.codec,
                                    label = t.label ?: streamTrack.label,
                                )
                            } else {
                                t
                            }
                        }
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

@Composable
private fun SpeedList(
    viewModel: PlayerViewModel,
    initialFocusRequester: FocusRequester,
    onBack: () -> Unit,
) {
    val speedOptions = remember { listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f) }
    val currentSpeed = viewModel.playbackSpeed

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        itemsIndexed(
            items = speedOptions,
            key = { _, speed -> speed },
        ) { index, speed ->
            val isSelected = currentSpeed == speed
            val label =
                if (speed == 1.0f) stringResource(id = PlayerR.string.normal_speed) else "${speed}x"
            val isFirst = index == 0

            SelectableItem(
                title = label,
                isSelected = isSelected,
                onClick = {
                    viewModel.selectSpeed(speed)
                    onBack()
                },
                modifier =
                    if (isFirst) Modifier.focusRequester(initialFocusRequester) else Modifier,
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun AspectRatioList(
    currentResizeMode: PlayerResizeMode,
    isMpv: Boolean = false,
    onResizeModeChange: (PlayerResizeMode) -> Unit,
    initialFocusRequester: FocusRequester,
    onBack: () -> Unit,
) {
    val resizeModes = remember {
        listOf(
                PlayerResizeMode.FIT to PlayerR.string.aspect_ratio_fit,
                PlayerResizeMode.ZOOM to PlayerR.string.aspect_ratio_zoom,
                PlayerResizeMode.FILL to PlayerR.string.aspect_ratio_fill,
            )
            .let { modes -> if (isMpv) modes.dropLast(1) else modes }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        itemsIndexed(
            items = resizeModes,
            key = { _, (mode, _) -> mode },
        ) { index, (mode, labelRes) ->
            val isSelected = currentResizeMode == mode
            val isFirst = index == 0

            SelectableItem(
                title = stringResource(id = labelRes),
                isSelected = isSelected,
                onClick = {
                    onResizeModeChange(mode)
                    onBack()
                },
                modifier =
                    if (isFirst) Modifier.focusRequester(initialFocusRequester) else Modifier,
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun SettingsList(
    viewModel: PlayerViewModel,
    tracks: Tracks,
    currentResizeMode: PlayerResizeMode,
    onNavigate: (PlayerSidePanelType) -> Unit,
    subtitlesFocusRequester: FocusRequester,
    audioFocusRequester: FocusRequester,
    speedFocusRequester: FocusRequester,
    aspectRatioFocusRequester: FocusRequester,
    sleepTimerFocusRequester: FocusRequester,
) {
    val context = LocalContext.current
    val currentLocale = LocalConfiguration.current.locales[0]
    val displayExtraInfo = viewModel.displayExtraInfo
    val uiState by viewModel.uiState.collectAsState()
    val isMovie = uiState.seriesId == null
    val sleepTimerStatus = uiState.sleepTimerStatus

    val sleepTimerSubtitle =
        if (sleepTimerStatus.isActive) {
            when (val mode = sleepTimerStatus.mode) {
                is SleepTimerMode.EndOfMedia -> {
                    if (isMovie) stringResource(id = PlayerR.string.sleep_timer_end_of_video)
                    else stringResource(id = PlayerR.string.sleep_timer_end_of_episode)
                }
                is SleepTimerMode.Duration -> {
                    val remainingMins = (sleepTimerStatus.remainingSeconds + 59L) / 60L
                    stringResource(id = PlayerR.string.sleep_timer_remaining, remainingMins.toInt())
                }
                else -> stringResource(id = PlayerR.string.sleep_timer_off)
            }
        } else {
            stringResource(id = PlayerR.string.sleep_timer_off)
        }

    val currentSpeed = viewModel.playbackSpeed
    val speedLabel =
        if (currentSpeed == 1.0f) stringResource(id = PlayerR.string.normal_speed)
        else "${currentSpeed}x"

    val audioGroups =
        remember(tracks) {
            tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
        }
    val currentAudio =
        remember(audioGroups, currentLocale, displayExtraInfo) {
            val selectedIndex = audioGroups.indexOfFirst { it.isSelected }
            if (selectedIndex >= 0) {
                TrackFormatter.getTrackDisplayInfo(
                        group = audioGroups[selectedIndex],
                        context = context,
                        displayExtraInfo = displayExtraInfo,
                        currentLocale = currentLocale,
                        fallbackIndex = selectedIndex + 1,
                    )
                    .name
            } else null
        }

    val textGroups =
        remember(tracks) { tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported } }
    val currentSubtitle =
        remember(textGroups, currentLocale, displayExtraInfo) {
            val selectedIndex = textGroups.indexOfFirst { it.isSelected }
            if (selectedIndex >= 0) {
                TrackFormatter.getTrackDisplayInfo(
                        group = textGroups[selectedIndex],
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
                iconRes = CoreR.drawable.ic_playback_speed,
                onClick = { onNavigate(PlayerSidePanelType.SPEED) },
                modifier = Modifier.focusRequester(speedFocusRequester),
            )
        }

        // Aspect Ratio
        item {
            NavigationItem(
                title = stringResource(id = PlayerR.string.aspect_ratio),
                subtitle = resizeModeLabel,
                iconRes = CoreR.drawable.ic_aspect_ratio,
                onClick = { onNavigate(PlayerSidePanelType.ASPECT_RATIO) },
                modifier = Modifier.focusRequester(aspectRatioFocusRequester),
            )
        }

        // Sleep Timer
        item {
            NavigationItem(
                title = stringResource(id = PlayerR.string.sleep_timer),
                subtitle = sleepTimerSubtitle,
                iconRes = CoreR.drawable.ic_sleep_timer,
                onClick = { onNavigate(PlayerSidePanelType.SLEEP_TIMER) },
                modifier = Modifier.focusRequester(sleepTimerFocusRequester),
            )
        }
    }
}

private sealed interface TvSleepTimerOption {
    data object EndOfMedia : TvSleepTimerOption

    data class Minutes(val count: Int) : TvSleepTimerOption

    data object Off : TvSleepTimerOption
}

@Composable
private fun SleepTimerList(
    viewModel: PlayerViewModel,
    initialFocusRequester: FocusRequester,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val isMovie = uiState.seriesId == null
    val sleepTimerStatus = uiState.sleepTimerStatus

    val options =
        remember(isMovie) {
            listOf(
                TvSleepTimerOption.EndOfMedia,
                TvSleepTimerOption.Minutes(10),
                TvSleepTimerOption.Minutes(15),
                TvSleepTimerOption.Minutes(30),
                TvSleepTimerOption.Minutes(45),
                TvSleepTimerOption.Minutes(60),
                TvSleepTimerOption.Minutes(90),
                TvSleepTimerOption.Minutes(120),
                TvSleepTimerOption.Off,
            )
        }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        itemsIndexed(items = options) { index, option ->
            val isFirst = index == 0
            val (title, isSelected, chips) =
                when (option) {
                    is TvSleepTimerOption.EndOfMedia -> {
                        val text =
                            if (isMovie)
                                stringResource(id = PlayerR.string.sleep_timer_end_of_video)
                            else stringResource(id = PlayerR.string.sleep_timer_end_of_episode)
                        val selected = sleepTimerStatus.mode is SleepTimerMode.EndOfMedia
                        Triple(text, selected, emptyList<String>())
                    }
                    is TvSleepTimerOption.Minutes -> {
                        val text =
                            stringResource(id = PlayerR.string.sleep_timer_minutes, option.count)
                        val selected =
                            sleepTimerStatus.mode is SleepTimerMode.Duration &&
                                (sleepTimerStatus.mode as SleepTimerMode.Duration).totalMinutes ==
                                    option.count
                        val chipList =
                            if (selected) {
                                val remainingMins = (sleepTimerStatus.remainingSeconds + 59L) / 60L
                                listOf(
                                    stringResource(
                                        id = PlayerR.string.sleep_timer_remaining,
                                        remainingMins.toInt(),
                                    )
                                )
                            } else emptyList()
                        Triple(text, selected, chipList)
                    }
                    is TvSleepTimerOption.Off -> {
                        val text = stringResource(id = PlayerR.string.sleep_timer_off)
                        val selected = !sleepTimerStatus.isActive
                        Triple(text, selected, emptyList<String>())
                    }
                }

            SelectableItem(
                title = title,
                isSelected = isSelected,
                chips = chips,
                onClick = {
                    when (option) {
                        is TvSleepTimerOption.EndOfMedia ->
                            viewModel.setSleepTimer(SleepTimerMode.EndOfMedia)
                        is TvSleepTimerOption.Minutes ->
                            viewModel.setSleepTimer(SleepTimerMode.Duration(option.count))
                        is TvSleepTimerOption.Off -> viewModel.setSleepTimer(SleepTimerMode.Off)
                    }
                    onBack()
                },
                modifier =
                    if (isFirst) Modifier.focusRequester(initialFocusRequester) else Modifier,
            )
        }
    }
}

@Composable
private fun TrackChip(text: String) {
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

@Composable
private fun SelectableItem(
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
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(modifier = Modifier.weight(1f))
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

@Composable
private fun NavigationItem(
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
private fun NavigationItemPreview() {
    FindroidTheme {
        NavigationItem(
            title = "Audio",
            subtitle = "Italiano (5.1)",
            iconRes = CoreR.drawable.ic_audio,
            onClick = {},
        )
    }
}

@Composable
private fun ChapterList(
    viewModel: PlayerViewModel,
    initialFocusRequester: FocusRequester,
    onClose: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val chapters = uiState.currentChapters
    val currentPositionMs = viewModel.player.currentPosition
    val currentChapterIndex =
        remember(chapters, currentPositionMs) {
            ChapterUtils.getCurrentChapterIndex(chapters, currentPositionMs) ?: 0
        }
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = (currentChapterIndex - 1).coerceAtLeast(0)
        )

    LaunchedEffect(currentChapterIndex) {
        if (currentChapterIndex in chapters.indices) {
            listState.scrollToItem(currentChapterIndex)
            repeat(6) {
                try {
                    initialFocusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {
                    delay(50.milliseconds)
                }
            }
        }
    }

    if (chapters.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(id = PlayerR.string.no_chapters),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    } else {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            itemsIndexed(
                items = chapters,
                key = { index, chapter -> "${chapter.startPosition}_$index" },
            ) { index, chapter ->
                val isCurrent = index == currentChapterIndex
                val nextChapterStart =
                    chapters.getOrNull(index + 1)?.startPosition ?: Long.MAX_VALUE
                val matchedSegment =
                    findChapterSegment(
                        chapter = chapter,
                        nextChapterStart = nextChapterStart,
                        segments = uiState.segments,
                        durationMs = viewModel.player.duration.coerceAtLeast(0L),
                    )
                val segmentType =
                    matchedSegment?.type
                        ?: findChapterSegmentType(
                            chapter = chapter,
                            nextChapterStart = nextChapterStart,
                            segments = uiState.segments,
                            durationMs = viewModel.player.duration.coerceAtLeast(0L),
                        )
                val segmentColor =
                    when (val matchedColor = matchedSegment?.color) {
                        0L -> getSegmentColor(segmentType)
                        null -> getSegmentColor(segmentType)
                        else -> Color(matchedColor)
                    }

                ChapterItem(
                    chapter = chapter,
                    chapterIndex = index,
                    isCurrent = isCurrent,
                    trickplay = uiState.currentTrickplay,
                    segmentType = segmentType,
                    customSegmentColor = segmentColor,
                    onClick = {
                        viewModel.player.seekTo(chapter.startPosition)
                        onClose()
                    },
                    modifier =
                        if (index == currentChapterIndex) {
                            Modifier.focusRequester(initialFocusRequester)
                        } else {
                            Modifier
                        },
                )
            }
        }
    }
}

@Composable
private fun ChapterItem(
    chapter: PlayerChapter,
    chapterIndex: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
    trickplay: Trickplay?,
    modifier: Modifier = Modifier,
    segmentType: FindroidSegmentType? = null,
    customSegmentColor: Color? = null,
) {
    val segmentLabel = segmentType?.labelRes?.let { stringResource(it) }
    val chapterName =
        chapter.name?.takeIf { it.isNotBlank() }
            ?: stringResource(id = PlayerR.string.chapter_title, chapterIndex + 1)
    val timestampString =
        chapter.startPosition.milliseconds.toComponents { h, m, s, _ ->
            if (h > 0) {
                "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
            } else {
                "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
            }
        }

    var isImageError by remember(chapter.imageUri) { mutableStateOf(false) }

    val trickplayBitmap =
        remember(trickplay, chapter.startPosition) {
            if (trickplay != null && trickplay.images.isNotEmpty()) {
                val intervalMs = trickplay.intervalMs
                if (intervalMs > 0) {
                    val idx =
                        (chapter.startPosition / intervalMs)
                            .toInt()
                            .coerceIn(0, trickplay.images.size - 1)
                    trickplay.images.getOrNull(idx)
                } else {
                    null
                }
            } else {
                null
            }
        }

    val imageAspectRatio =
        remember(trickplayBitmap) {
            if (trickplayBitmap != null && trickplayBitmap.height > 0) {
                (trickplayBitmap.width.toFloat() / trickplayBitmap.height.toFloat()).coerceIn(
                    4f / 3f,
                    2.4f,
                )
            } else {
                16f / 9f
            }
        }

    val segmentColor = customSegmentColor ?: segmentType?.let { getSegmentColor(it) }

    val itemColors =
        ClickableSurfaceDefaults.colors(
            containerColor =
                if (isCurrent) {
                    segmentColor?.copy(alpha = 0.25f) ?: PlayerTvSidePanelUnselected
                } else {
                    segmentColor?.copy(alpha = 0.12f) ?: PlayerTvSidePanelContainer
                },
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color.Black,
        )

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = PanelItemShape),
        colors = itemColors,
        scale = ClickableSurfaceScale.None,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Chapter thumbnail with Dynamic Aspect Ratio
            val hasServerImage = !chapter.imageUri.isNullOrBlank() && !isImageError
            Box(
                modifier =
                    Modifier.height(63.dp)
                        .aspectRatio(imageAspectRatio)
                        .clip(ChapterThumbShape)
                        .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (hasServerImage) {
                    AsyncImage(
                        model = chapter.imageUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onError = { isImageError = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (trickplayBitmap != null) {
                    Image(
                        bitmap = trickplayBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        painter = painterResource(id = CoreR.drawable.ic_play),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Color.White.copy(alpha = 0.5f),
                    )
                }

                if (isCurrent) {
                    Box(
                        modifier =
                            Modifier.fillMaxSize()
                                .background(
                                    (segmentColor ?: MaterialTheme.colorScheme.primary).copy(
                                        alpha = 0.25f
                                    )
                                )
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Right: Chapter Name & Start Timestamp
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = chapterName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (segmentColor != null && segmentLabel != null) {
                        Box(
                            modifier =
                                Modifier.clip(RoundedCornerShape(4.dp))
                                    .background(segmentColor.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = segmentLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = segmentColor,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = timestampString,
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                )
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
                    subtitle = "1.0x",
                    iconRes = CoreR.drawable.ic_playback_speed,
                    onClick = {},
                )
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
                title = "English (AAC)",
                chips = listOf("Default", "Stereo"),
                isSelected = true,
                onClick = {},
            )
            SelectableItem(
                title = "Japanese (FLAC)",
                chips = listOf("5.1"),
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
private fun SpeedListPreview() {
    FindroidTheme {
        Column(
            modifier = Modifier.width(320.dp).background(Color.Black).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectableItem(
                title = stringResource(id = PlayerR.string.normal_speed),
                isSelected = true,
                onClick = {},
            )
            SelectableItem(
                title = "1.25x",
                isSelected = false,
                onClick = {},
            )
            SelectableItem(
                title = "1.5x",
                isSelected = false,
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun AspectRatioListPreview() {
    FindroidTheme {
        Column(
            modifier = Modifier.width(320.dp).background(Color.Black).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectableItem(
                title = stringResource(id = PlayerR.string.aspect_ratio_fit),
                isSelected = true,
                onClick = {},
            )
            SelectableItem(
                title = stringResource(id = PlayerR.string.aspect_ratio_zoom),
                isSelected = false,
                onClick = {},
            )
            SelectableItem(
                title = stringResource(id = PlayerR.string.aspect_ratio_fill),
                isSelected = false,
                onClick = {},
            )
        }
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
                iconRes = CoreR.drawable.ic_playback_speed,
                onClick = {},
            )
            NavigationItem(
                title = stringResource(id = PlayerR.string.aspect_ratio),
                subtitle = stringResource(id = PlayerR.string.aspect_ratio_fit),
                iconRes = CoreR.drawable.ic_aspect_ratio,
                onClick = {},
            )
            NavigationItem(
                title = stringResource(id = PlayerR.string.sleep_timer),
                subtitle = stringResource(id = PlayerR.string.sleep_timer_off),
                iconRes = CoreR.drawable.ic_sleep_timer,
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun SleepTimerListPreview() {
    FindroidTheme {
        Column(
            modifier = Modifier.width(320.dp).background(Color.Black).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectableItem(
                title = stringResource(id = PlayerR.string.sleep_timer_end_of_video),
                isSelected = false,
                onClick = {},
            )
            SelectableItem(
                title = stringResource(id = PlayerR.string.sleep_timer_minutes, 30),
                isSelected = true,
                chips = listOf("28m remaining"),
                onClick = {},
            )
            SelectableItem(
                title = stringResource(id = PlayerR.string.sleep_timer_off),
                isSelected = false,
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun ChapterListPreview() {
    FindroidTheme {
        Column(
            modifier = Modifier.width(320.dp).background(Color.Black).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChapterItem(
                chapter = PlayerChapter(name = "Intro", startPosition = 0L),
                chapterIndex = 0,
                isCurrent = true,
                onClick = {},
                trickplay = null,
            )
            ChapterItem(
                chapter = PlayerChapter(name = "Part 1", startPosition = 120_000L),
                chapterIndex = 1,
                isCurrent = false,
                onClick = {},
                trickplay = null,
            )
        }
    }
}

@Preview
@Composable
private fun ChapterItemPreview() {
    FindroidTheme {
        Box(modifier = Modifier.width(320.dp).background(Color.Black).padding(8.dp)) {
            ChapterItem(
                chapter = PlayerChapter(name = "Chapter 1: Arrival", startPosition = 300_000L),
                chapterIndex = 0,
                isCurrent = true,
                onClick = {},
                trickplay = null,
            )
        }
    }
}
