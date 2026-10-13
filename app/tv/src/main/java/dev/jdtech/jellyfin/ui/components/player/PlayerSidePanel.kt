package dev.jdtech.jellyfin.ui.components.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.domain.models.Trickplay
import dev.jdtech.jellyfin.player.core.domain.utils.ChapterUtils
import dev.jdtech.jellyfin.player.core.domain.utils.TrackFormatter
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import kotlin.time.Duration.Companion.milliseconds
import org.jellyfin.sdk.model.api.MediaStreamType

enum class PlayerSidePanelType {
    NONE,
    AUDIO,
    SUBTITLES,
    SETTINGS,
    SPEED,
    ASPECT_RATIO,
    CHAPTERS,
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerSidePanel(
    panelType: PlayerSidePanelType,
    onPanelTypeChange: (PlayerSidePanelType) -> Unit,
    viewModel: PlayerViewModel,
    currentResizeMode: Int,
    onResizeModeChange: (Int) -> Unit,
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

                PlayerSidePanelType.NONE -> Unit
            }
        }
    }
}

@Composable
private fun PanelHeader(panelType: PlayerSidePanelType) {
    val titleRes =
        when (panelType) {
            PlayerSidePanelType.AUDIO -> PlayerR.string.audio
            PlayerSidePanelType.SUBTITLES -> PlayerR.string.subtitles
            PlayerSidePanelType.SETTINGS -> CoreR.string.title_settings
            PlayerSidePanelType.SPEED -> PlayerR.string.playback_speed
            PlayerSidePanelType.ASPECT_RATIO -> PlayerR.string.aspect_ratio
            PlayerSidePanelType.CHAPTERS -> PlayerR.string.chapters
            PlayerSidePanelType.NONE -> CoreR.string.title_settings
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
            itemsIndexed(audioGroups) { index, group ->
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
        item {
            SelectableItem(
                title = stringResource(id = PlayerR.string.none),
                isSelected = noneSelected,
                onClick = { viewModel.switchToTrack(C.TRACK_TYPE_TEXT, -1) },
                modifier = Modifier.focusRequester(initialFocusRequester),
            )
        }

        itemsIndexed(textGroups) { index, group ->
            val isSelected = group.isSelected
            val matchedStream = subtitleStreams.getOrNull(index)
            val displayInfo =
                remember(group, currentLocale, displayExtraInfo, matchedStream) {
                    val track =
                        TrackFormatter.toTrack(group, index + 1).let { t ->
                            if (matchedStream != null) {
                                t.copy(
                                    isForced = matchedStream.isForced == true || t.isForced == true,
                                    isHearingImpaired =
                                        matchedStream.isHearingImpaired == true ||
                                            t.isHearingImpaired == true,
                                    isExternal = matchedStream.isExternal || t.isExternal == true,
                                    codec =
                                        matchedStream.codec.takeIf { it.isNotBlank() } ?: t.codec,
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
        itemsIndexed(speedOptions) { index, speed ->
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
    currentResizeMode: Int,
    onResizeModeChange: (Int) -> Unit,
    initialFocusRequester: FocusRequester,
    onBack: () -> Unit,
) {
    val resizeModes = remember {
        listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT to PlayerR.string.aspect_ratio_fit,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM to PlayerR.string.aspect_ratio_zoom,
            AspectRatioFrameLayout.RESIZE_MODE_FILL to PlayerR.string.aspect_ratio_fill,
        )
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        itemsIndexed(resizeModes) { index, (mode, labelRes) ->
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
    currentResizeMode: Int,
    onNavigate: (PlayerSidePanelType) -> Unit,
    subtitlesFocusRequester: FocusRequester,
    audioFocusRequester: FocusRequester,
    speedFocusRequester: FocusRequester,
    aspectRatioFocusRequester: FocusRequester,
) {
    val context = LocalContext.current
    val currentLocale = LocalConfiguration.current.locales[0]
    val displayExtraInfo = viewModel.displayExtraInfo

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
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM ->
                stringResource(id = PlayerR.string.aspect_ratio_zoom)
            AspectRatioFrameLayout.RESIZE_MODE_FILL ->
                stringResource(id = PlayerR.string.aspect_ratio_fill)
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
                iconRes = CoreR.drawable.ic_speaker,
                onClick = { onNavigate(PlayerSidePanelType.AUDIO) },
                modifier = Modifier.focusRequester(audioFocusRequester),
            )
        }

        // Playback Speed
        item {
            NavigationItem(
                title = stringResource(id = PlayerR.string.playback_speed),
                subtitle = speedLabel,
                iconRes = PlayerR.drawable.ic_gauge,
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
    }
}

@Composable
private fun TrackChip(text: String) {
    val contentColor = LocalContentColor.current
    Box(
        modifier =
            Modifier.clip(RoundedCornerShape(4.dp))
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
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF242426),
                focusedContainerColor = Color.White,
                contentColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        scale = ClickableSurfaceScale.None,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (chips.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        chips.forEach { chipText -> TrackChip(text = chipText) }
                    }
                }
            }
            if (isSelected) {
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
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
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF242426),
                focusedContainerColor = Color.White,
                contentColor = Color.White,
                focusedContentColor = Color.Black,
            ),
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
            iconRes = CoreR.drawable.ic_speaker,
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
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (currentChapterIndex in chapters.indices) {
            listState.scrollToItem(currentChapterIndex)
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
        val baseUrl = remember { viewModel.getBaseUrl() }
        val currentMediaId =
            remember(viewModel.player.currentMediaItem) {
                viewModel.player.currentMediaItem?.mediaId
            }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            itemsIndexed(chapters) { index, chapter ->
                val chapterImageUrl =
                    remember(baseUrl, currentMediaId, index) {
                        if (baseUrl.isNotBlank() && !currentMediaId.isNullOrBlank()) {
                            "$baseUrl/Items/$currentMediaId/Images/Chapter/$index"
                        } else {
                            null
                        }
                    }
                val isCurrent = index == currentChapterIndex

                ChapterItem(
                    chapter = chapter,
                    chapterIndex = index,
                    isCurrent = isCurrent,
                    trickplay = uiState.currentTrickplay,
                    chapterImageUrl = chapterImageUrl,
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
    chapterImageUrl: String?,
    modifier: Modifier = Modifier,
) {
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

    val trickplayBitmap =
        remember(trickplay, chapter.startPosition) {
            if (trickplay != null && trickplay.images.isNotEmpty()) {
                val intervalMs =
                    if (trickplay.interval > 1000) trickplay.interval.toLong()
                    else trickplay.interval.toLong() * 1000L
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

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (isCurrent) Color(0xFF2E2E32) else Color(0xFF242426),
                focusedContainerColor = Color.White,
                contentColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        scale = ClickableSurfaceScale.None,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Chapter thumbnail (Left, 16:9)
            Box(
                modifier =
                    Modifier.width(112.dp)
                        .height(63.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (trickplayBitmap != null) {
                    Image(
                        bitmap = trickplayBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (!chapterImageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = chapterImageUrl,
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
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Right: Chapter Name & Start Timestamp
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = chapterName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isCurrent) {
                        Box(
                            modifier =
                                Modifier.size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    Text(
                        text = timestampString,
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}
